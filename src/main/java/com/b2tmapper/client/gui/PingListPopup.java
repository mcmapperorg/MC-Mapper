package com.b2tmapper.client.gui;

import com.b2tmapper.config.ModConfig;
import com.b2tmapper.client.ping.TrackedPing;
import com.b2tmapper.client.ping.TrackedPingManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PingListPopup extends BasePopup {

    private static final String API_BASE = "https://mc-mapper-production.up.railway.app/api";
    
    private boolean[] filterEnabled = {true, true, true, true, true};
    private static final String[] FILTER_NAMES = {"Info", "Danger", "Landmark", "Personal", "Private"};
    private static final String[] FILTER_ICONS = {"i", "!", "L", "P", "X"};
    private static final int[] FILTER_COLORS = {0xFF0096FF, 0xFFFF0000, 0xFFFFC800, 0xFF00FF00, 0xFF9B59B6};

    private List<ServerInfo> servers = new ArrayList<>();
    private List<ServerInfo> filteredServers = new ArrayList<>();
    private int selectedServerIndex = -1;
    private boolean loadingServers = true;
    private String serverError = null;

    private List<PingInfo> pings = new ArrayList<>();
    private List<PingInfo> filteredPings = new ArrayList<>();
    private boolean loadingPings = false;
    private String pingError = null;

    private int hoveredFilter = -1;
    private int hoveredServerIndex = -1;
    private int hoveredPingIndex = -1;
    private int hoveredTrackButton = -1;
    private int scrollOffset = 0;
    private boolean showingServers = true;

    private TextFieldWidget serverSearchField;
    private TextFieldWidget pingSearchField;
    private String serverSearchText = "";
    private String pingSearchText = "";

    private boolean backButtonHovered = false;

    private boolean isDraggingScrollbar = false;
    private int scrollbarDragOffset = 0;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    public PingListPopup(Screen parent) {
        super(parent, "Ping List");
        loadServers();
    }

    @Override
    protected void init() {
        super.init();
        popupWidth = 320;
        popupHeight = 380;
        popupX = (width - popupWidth) / 2;
        popupY = (height - popupHeight) / 2;

        int searchWidth = popupWidth - padding * 2 - 20;
        int searchX = popupX + padding + 10;

        serverSearchField = new TextFieldWidget(textRenderer, searchX, popupY + headerHeight + padding + 18, searchWidth, 16, Text.literal("Search"));
        serverSearchField.setMaxLength(50);
        serverSearchField.setPlaceholder(Text.literal("Search servers..."));
        serverSearchField.setChangedListener(this::onServerSearchChanged);
        addDrawableChild(serverSearchField);

        pingSearchField = new TextFieldWidget(textRenderer, searchX, popupY + headerHeight + padding + 50, searchWidth, 16, Text.literal("Search"));
        pingSearchField.setMaxLength(50);
        pingSearchField.setPlaceholder(Text.literal("Search pings..."));
        pingSearchField.setChangedListener(this::onPingSearchChanged);
        pingSearchField.setVisible(false);
        addDrawableChild(pingSearchField);

        updateFilteredServers();
    }

    private void onServerSearchChanged(String text) {
        serverSearchText = text.toLowerCase();
        scrollOffset = 0;
        updateFilteredServers();
    }

    private void onPingSearchChanged(String text) {
        pingSearchText = text.toLowerCase();
        scrollOffset = 0;
        updateFilteredPings();
    }

    private void updateFilteredServers() {
        filteredServers.clear();
        for (ServerInfo server : servers) {
            if (serverSearchText.isEmpty() || 
                server.name.toLowerCase().contains(serverSearchText)) {
                filteredServers.add(server);
            }
        }
    }

    private void updateFilteredPings() {
        filteredPings.clear();
        for (PingInfo ping : pings) {
            int typeIdx = getPingTypeIndex(ping.pingType);
            if (typeIdx < 0 || typeIdx >= filterEnabled.length || !filterEnabled[typeIdx]) {
                continue;
            }
            if (pingSearchText.isEmpty() ||
                ping.username.toLowerCase().contains(pingSearchText) ||
                ping.textContent.toLowerCase().contains(pingSearchText) ||
                ping.keywords.toLowerCase().contains(pingSearchText)) {
                filteredPings.add(ping);
            }
        }
    }

    private void loadServers() {
        loadingServers = true;
        serverError = null;
        
        CompletableFuture.runAsync(() -> {
            try {
                servers.clear();
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/servers"))
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                    if (json.get("success").getAsBoolean()) {
                        JsonArray serversArray = json.getAsJsonArray("servers");
                        for (int i = 0; i < serversArray.size(); i++) {
                            JsonObject s = serversArray.get(i).getAsJsonObject();
                            if (s.get("is_configured").getAsBoolean() && s.get("is_public").getAsBoolean()) {
                                ServerInfo info = new ServerInfo();
                                info.id = s.get("id").getAsInt();
                                info.name = s.get("name").getAsString();
                                info.slug = s.has("slug") && !s.get("slug").isJsonNull() 
                                    ? s.get("slug").getAsString() : "";
                                info.totalMaps = s.get("total_maps").getAsInt();
                                info.totalContributors = s.has("total_contributors") 
                                    ? s.get("total_contributors").getAsInt() : 0;
                                info.isPrivate = false;
                                info.role = "public";
                                servers.add(info);
                            }
                        }
                    } else {
                        serverError = "API returned error";
                    }
                } else {
                    serverError = "HTTP " + response.statusCode();
                }
                
                String authToken = ModConfig.get().authToken;
                if (authToken != null && !authToken.isEmpty()) {
                    try {
                        HttpRequest privateRequest = HttpRequest.newBuilder()
                            .uri(URI.create(API_BASE + "/my-servers"))
                            .header("Authorization", "Bearer " + authToken)
                            .GET()
                            .build();
                        
                        HttpResponse<String> privateResponse = httpClient.send(privateRequest, HttpResponse.BodyHandlers.ofString());
                        
                        if (privateResponse.statusCode() == 200) {
                            JsonObject privateJson = gson.fromJson(privateResponse.body(), JsonObject.class);
                            if (privateJson.get("success").getAsBoolean()) {
                                if (privateJson.has("owned") && !privateJson.get("owned").isJsonNull()) {
                                    JsonArray ownedArray = privateJson.getAsJsonArray("owned");
                                    for (int i = 0; i < ownedArray.size(); i++) {
                                        JsonObject s = ownedArray.get(i).getAsJsonObject();
                                        ServerInfo info = new ServerInfo();
                                        info.id = s.get("id").getAsInt();
                                        info.name = s.has("name") && !s.get("name").isJsonNull() 
                                            ? s.get("name").getAsString() : "My Server";
                                        info.slug = s.has("slug") && !s.get("slug").isJsonNull() 
                                            ? s.get("slug").getAsString() : "";
                                        info.totalMaps = s.has("total_maps") ? s.get("total_maps").getAsInt() : 0;
                                        info.totalContributors = 0;
                                        info.isPrivate = true;
                                        info.role = "owner";
                                        servers.add(info);
                                    }
                                }
                                
                                if (privateJson.has("shared") && !privateJson.get("shared").isJsonNull()) {
                                    JsonArray sharedArray = privateJson.getAsJsonArray("shared");
                                    for (int i = 0; i < sharedArray.size(); i++) {
                                        JsonObject s = sharedArray.get(i).getAsJsonObject();
                                        ServerInfo info = new ServerInfo();
                                        info.id = s.get("id").getAsInt();
                                        info.name = s.has("name") && !s.get("name").isJsonNull() 
                                            ? s.get("name").getAsString() : "Shared Server";
                                        info.slug = s.has("slug") && !s.get("slug").isJsonNull() 
                                            ? s.get("slug").getAsString() : "";
                                        info.totalMaps = s.has("total_maps") ? s.get("total_maps").getAsInt() : 0;
                                        info.totalContributors = 0;
                                        info.isPrivate = true;
                                        info.role = "shared";
                                        servers.add(info);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                    }
                }
                
                updateFilteredServers();
            } catch (Exception e) {
                serverError = "Connection failed";
                e.printStackTrace();
            }
            loadingServers = false;
        });
    }

    private void loadPings(int serverId) {
        loadingPings = true;
        pingError = null;
        pings.clear();
        filteredPings.clear();
        scrollOffset = 0;
        
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE + "/pings?server_id=" + serverId))
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                    if (json.get("success").getAsBoolean()) {
                        JsonArray pingsArray = json.getAsJsonArray("pings");
                        for (int i = 0; i < pingsArray.size(); i++) {
                            JsonObject p = pingsArray.get(i).getAsJsonObject();
                            PingInfo info = new PingInfo();
                            info.id = p.get("id").getAsInt();
                            info.username = p.get("username").getAsString();
                            info.pingType = p.get("ping_type").getAsString();
                            info.gridX = p.get("gridX").getAsInt();
                            info.gridZ = p.get("gridZ").getAsInt();
                            info.worldX = p.get("worldX").getAsInt();
                            info.worldZ = p.get("worldZ").getAsInt();
                            info.textContent = p.has("text_content") && !p.get("text_content").isJsonNull() 
                                ? p.get("text_content").getAsString() : "";
                            info.keywords = p.has("keywords") && !p.get("keywords").isJsonNull()
                                ? p.get("keywords").getAsString() : "";
                            pings.add(info);
                        }
                    } else {
                        pingError = "API returned error";
                    }
                } else {
                    pingError = "HTTP " + response.statusCode();
                }
                
                String authToken = ModConfig.get().authToken;
                if (authToken != null && !authToken.isEmpty()) {
                    try {
                        String privateUrl = API_BASE + "/private-pings?server_id=" + serverId;
                        
                        HttpRequest privateRequest = HttpRequest.newBuilder()
                            .uri(URI.create(privateUrl))
                            .header("Authorization", "Bearer " + authToken)
                            .GET()
                            .build();
                        
                        HttpResponse<String> privateResponse = httpClient.send(privateRequest, HttpResponse.BodyHandlers.ofString());
                        
                        if (privateResponse.statusCode() == 200) {
                            JsonObject privateJson = gson.fromJson(privateResponse.body(), JsonObject.class);
                            if (privateJson.get("success").getAsBoolean()) {
                                JsonArray privatePingsArray = privateJson.getAsJsonArray("pings");
                                for (int i = 0; i < privatePingsArray.size(); i++) {
                                    JsonObject p = privatePingsArray.get(i).getAsJsonObject();
                                    PingInfo info = new PingInfo();
                                    info.id = p.get("id").getAsInt() + 1000000;
                                    info.username = p.has("username") && !p.get("username").isJsonNull()
                                        ? p.get("username").getAsString() : "You";
                                    info.pingType = "private";
                                    info.gridX = p.get("gridX").getAsInt();
                                    info.gridZ = p.get("gridZ").getAsInt();
                                    info.worldX = p.get("worldX").getAsInt();
                                    info.worldZ = p.get("worldZ").getAsInt();
                                    info.textContent = p.has("text_content") && !p.get("text_content").isJsonNull() 
                                        ? p.get("text_content").getAsString() : "";
                                    info.keywords = p.has("note") && !p.get("note").isJsonNull()
                                        ? p.get("note").getAsString() : "";
                                    pings.add(info);
                                }
                            }
                        } else {
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                
                updateFilteredPings();
            } catch (Exception e) {
                pingError = "Connection failed";
                e.printStackTrace();
            }
            loadingPings = false;
        });
    }

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        int contentX = popupX + padding;
        int contentY = popupY + headerHeight + padding;
        int contentWidth = popupWidth - padding * 2;

        serverSearchField.setVisible(showingServers);
        pingSearchField.setVisible(!showingServers);

        if (showingServers) {
            renderServerList(context, mouseX, mouseY, contentX, contentY, contentWidth);
        } else {
            renderPingList(context, mouseX, mouseY, contentX, contentY, contentWidth);
        }
    }

    private void renderServerList(DrawContext context, int mouseX, int mouseY, int contentX, int contentY, int contentWidth) {
        context.drawCenteredTextWithShadow(textRenderer, "Select Server", contentX + contentWidth / 2, contentY, WHITE);
        contentY += 16;

        serverSearchField.setY(contentY);
        serverSearchField.setX(contentX + 10);
        serverSearchField.setWidth(contentWidth - 20);
        contentY += 22;

        context.fill(contentX, contentY, contentX + contentWidth, contentY + 1, GREEN_BORDER());
        contentY += 6;

        int listHeight = popupY + popupHeight - contentY - padding;
        int listWidth = contentWidth - 12; // Leave room for scrollbar
        context.fill(contentX, contentY, contentX + listWidth, contentY + listHeight, 0x44000000);
        drawBorder(context, contentX, contentY, listWidth, listHeight, GREEN_BORDER());

        if (loadingServers) {
            context.drawCenteredTextWithShadow(textRenderer, "Loading servers...", contentX + listWidth / 2, contentY + listHeight / 2, GRAY);
            return;
        }

        if (serverError != null) {
            context.drawCenteredTextWithShadow(textRenderer, "Error: " + serverError, contentX + listWidth / 2, contentY + listHeight / 2 - 6, 0xFFFF4444);
            context.drawCenteredTextWithShadow(textRenderer, "Click to retry", contentX + listWidth / 2, contentY + listHeight / 2 + 8, GRAY);
            return;
        }

        if (filteredServers.isEmpty()) {
            String msg = servers.isEmpty() ? "No servers available" : "No servers match search";
            context.drawCenteredTextWithShadow(textRenderer, msg, contentX + listWidth / 2, contentY + listHeight / 2, GRAY);
            return;
        }

        hoveredServerIndex = -1;
        int itemHeight = 32;
        int visibleItems = (listHeight - 8) / itemHeight;
        int innerY = contentY + 4;

        for (int i = 0; i < Math.min(filteredServers.size(), visibleItems); i++) {
            int idx = i + scrollOffset;
            if (idx >= filteredServers.size()) break;

            ServerInfo server = filteredServers.get(idx);
            int itemY = innerY + (i * itemHeight);

            boolean hovered = mouseX >= contentX + 4 && mouseX < contentX + listWidth - 4
                    && mouseY >= itemY && mouseY < itemY + itemHeight - 2;

            if (hovered) {
                hoveredServerIndex = idx;
                context.fill(contentX + 4, itemY, contentX + listWidth - 4, itemY + itemHeight - 2, GREEN_HOVER());
            }

            int nameX = contentX + 8;
            if (server.isPrivate) {
                String roleIcon;
                int roleColor;
                if ("owner".equals(server.role)) {
                    roleIcon = "[*]"; // Owner
                    roleColor = 0xFFFFD700; // Gold
                } else {
                    roleIcon = "[+]"; // Shared
                    roleColor = 0xFF9B59B6; // Purple
                }
                context.drawTextWithShadow(textRenderer, roleIcon, nameX, itemY + 4, roleColor);
                nameX += textRenderer.getWidth(roleIcon) + 4;
            }

            context.drawTextWithShadow(textRenderer, server.name, nameX, itemY + 4, WHITE);
            
            String stats;
            if (server.isPrivate) {
                stats = server.totalMaps + " maps" + ("owner".equals(server.role) ? " (owned)" : " (shared)");
            } else {
                stats = server.totalMaps + " maps, " + server.totalContributors + " contributors";
            }
            context.drawTextWithShadow(textRenderer, stats, contentX + 8, itemY + 16, GRAY);

            int borderColor = hovered ? GREEN_BORDER() : 0x44338833;
            if (server.isPrivate && !hovered) {
                borderColor = "owner".equals(server.role) ? 0x44FFD700 : 0x449B59B6;
            }
            drawBorder(context, contentX + 4, itemY, listWidth - 8, itemHeight - 2, borderColor);
        }

        renderScrollbar(context, contentX + listWidth + 2, contentY, 8, listHeight, 
            filteredServers.size(), visibleItems, scrollOffset, mouseX, mouseY);
    }

    private void renderPingList(DrawContext context, int mouseX, int mouseY, int contentX, int contentY, int contentWidth) {
        int backBtnWidth = 50;
        int backBtnHeight = 16;
        backButtonHovered = mouseX >= contentX && mouseX < contentX + backBtnWidth
                && mouseY >= contentY && mouseY < contentY + backBtnHeight;
        
        int backBg = backButtonHovered ? GREEN_HOVER() : GREEN_BUTTON();
        context.fill(contentX, contentY, contentX + backBtnWidth, contentY + backBtnHeight, backBg);
        drawBorder(context, contentX, contentY, backBtnWidth, backBtnHeight, GREEN_BORDER());
        context.drawCenteredTextWithShadow(textRenderer, "< Back", contentX + backBtnWidth / 2, contentY + 4, WHITE);

        String serverName = selectedServerIndex >= 0 && selectedServerIndex < servers.size() 
            ? servers.get(selectedServerIndex).name : "Unknown";
        context.drawTextWithShadow(textRenderer, serverName, contentX + backBtnWidth + 8, contentY + 4, WHITE);

        contentY += backBtnHeight + 6;

        pingSearchField.setY(contentY);
        pingSearchField.setX(contentX + 10);
        pingSearchField.setWidth(contentWidth - 20);
        contentY += 22;

        hoveredFilter = -1;
        int filterSize = 20;
        int filterSpacing = 4;
        int totalFilterWidth = (filterSize * 5) + (filterSpacing * 4);
        int filterStartX = contentX + (contentWidth - totalFilterWidth) / 2;

        for (int i = 0; i < 5; i++) {
            int filterX = filterStartX + (i * (filterSize + filterSpacing));
            boolean hovered = mouseX >= filterX && mouseX < filterX + filterSize 
                    && mouseY >= contentY && mouseY < contentY + filterSize;
            if (hovered) hoveredFilter = i;

            int bg, border;
            if (filterEnabled[i]) {
                bg = hovered ? BLUE_HOVER() : BLUE_SELECTED();
                border = BLUE_BORDER();
            } else {
                bg = hovered ? GREEN_HOVER() : GREEN_BUTTON();
                border = GREEN_BORDER();
            }

            context.fill(filterX, contentY, filterX + filterSize, contentY + filterSize, bg);
            drawBorder(context, filterX, contentY, filterSize, filterSize, border);
            context.drawCenteredTextWithShadow(textRenderer, FILTER_ICONS[i], filterX + filterSize / 2, contentY + 6,
                filterEnabled[i] ? FILTER_COLORS[i] : GRAY);
        }

        contentY += filterSize + 6;

        context.fill(contentX, contentY, contentX + contentWidth, contentY + 1, GREEN_BORDER());
        contentY += 4;

        int listHeight = popupY + popupHeight - contentY - padding;
        int listWidth = contentWidth - 12; // Leave room for scrollbar
        context.fill(contentX, contentY, contentX + listWidth, contentY + listHeight, 0x44000000);
        drawBorder(context, contentX, contentY, listWidth, listHeight, GREEN_BORDER());

        if (loadingPings) {
            context.drawCenteredTextWithShadow(textRenderer, "Loading pings...", contentX + listWidth / 2, contentY + listHeight / 2, GRAY);
            return;
        }

        if (pingError != null) {
            context.drawCenteredTextWithShadow(textRenderer, "Error: " + pingError, contentX + listWidth / 2, contentY + listHeight / 2, 0xFFFF4444);
            return;
        }

        if (filteredPings.isEmpty()) {
            String msg = pings.isEmpty() ? "No pings found" : "No pings match filters";
            context.drawCenteredTextWithShadow(textRenderer, msg, contentX + listWidth / 2, contentY + listHeight / 2, GRAY);
            return;
        }

        hoveredPingIndex = -1;
        hoveredTrackButton = -1;
        int itemHeight = 28;
        int visibleItems = (listHeight - 8) / itemHeight;
        int innerY = contentY + 4;
        
        int trackBtnWidth = 28;
        int trackBtnHeight = 18;

        for (int i = 0; i < Math.min(filteredPings.size(), visibleItems); i++) {
            int idx = i + scrollOffset;
            if (idx >= filteredPings.size()) break;

            PingInfo ping = filteredPings.get(idx);
            int itemY = innerY + (i * itemHeight);
            
            int trackBtnX = contentX + listWidth - trackBtnWidth - 8;
            int trackBtnY = itemY + 4;
            
            boolean trackHovered = mouseX >= trackBtnX && mouseX < trackBtnX + trackBtnWidth
                    && mouseY >= trackBtnY && mouseY < trackBtnY + trackBtnHeight;
            
            if (trackHovered) {
                hoveredTrackButton = idx;
            }

            boolean hovered = mouseX >= contentX + 4 && mouseX < contentX + listWidth - 4
                    && mouseY >= itemY && mouseY < itemY + itemHeight - 2;

            if (hovered && !trackHovered) {
                hoveredPingIndex = idx;
                context.fill(contentX + 4, itemY, contentX + listWidth - 4, itemY + itemHeight - 2, GREEN_HOVER());
            }

            int typeIdx = getPingTypeIndex(ping.pingType);
            String icon = typeIdx >= 0 ? FILTER_ICONS[typeIdx] : "?";
            int iconColor = typeIdx >= 0 ? FILTER_COLORS[typeIdx] : WHITE;
            context.drawTextWithShadow(textRenderer, "[" + icon + "]", contentX + 8, itemY + 4, iconColor);

            String info = ping.username + " @ " + ping.worldX + ", " + ping.worldZ;
            context.drawTextWithShadow(textRenderer, info, contentX + 32, itemY + 4, WHITE);
            
            String preview = !ping.keywords.isEmpty() ? ping.keywords : ping.textContent;
            if (preview.length() > 25) preview = preview.substring(0, 22) + "...";
            context.drawTextWithShadow(textRenderer, preview, contentX + 8, itemY + 14, GRAY);
            
            boolean isTracked = TrackedPingManager.get().isTracked(ping.id);
            int btnBg = trackHovered ? (isTracked ? 0xD9AA3333 : BLUE_HOVER()) : (isTracked ? 0xD9662222 : BLUE_SELECTED());
            int btnBorder = isTracked ? 0xFFAA3333 : BLUE_BORDER();
            
            context.fill(trackBtnX, trackBtnY, trackBtnX + trackBtnWidth, trackBtnY + trackBtnHeight, btnBg);
            drawBorder(context, trackBtnX, trackBtnY, trackBtnWidth, trackBtnHeight, btnBorder);
            
            String btnText = isTracked ? "-" : "+";
            context.drawCenteredTextWithShadow(textRenderer, btnText, trackBtnX + trackBtnWidth / 2, trackBtnY + 5, WHITE);
        }

        renderScrollbar(context, contentX + listWidth + 2, contentY, 8, listHeight, 
            filteredPings.size(), visibleItems, scrollOffset, mouseX, mouseY);
    }

    private void renderScrollbar(DrawContext context, int x, int y, int width, int height, 
            int totalItems, int visibleItems, int currentOffset, int mouseX, int mouseY) {
        if (totalItems <= visibleItems) return; // No scrollbar needed

        context.fill(x, y, x + width, y + height, 0x44000000);
        drawBorder(context, x, y, width, height, 0x44338833);

        float ratio = (float) visibleItems / totalItems;
        int thumbHeight = Math.max(20, (int) (height * ratio));
        int maxOffset = totalItems - visibleItems;
        int thumbY = y + (int) ((height - thumbHeight) * ((float) currentOffset / maxOffset));

        boolean thumbHovered = mouseX >= x && mouseX < x + width && mouseY >= thumbY && mouseY < thumbY + thumbHeight;
        int thumbColor = thumbHovered || isDraggingScrollbar ? 0xFF00AA00 : 0xFF008800;
        
        context.fill(x + 1, thumbY, x + width - 1, thumbY + thumbHeight, thumbColor);
    }

    private int getPingTypeIndex(String pingType) {
        switch (pingType.toLowerCase()) {
            case "info": return 0;
            case "danger": return 1;
            case "landmark": return 2;
            case "personal": return 3;
            case "private": return 4;
            default: return -1;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int contentX = popupX + padding;
            int contentY = popupY + headerHeight + padding;
            int contentWidth = popupWidth - padding * 2;
            int listWidth = contentWidth - 12;
            int scrollbarX = contentX + listWidth + 2;
            
            if (mouseX >= scrollbarX && mouseX < scrollbarX + 8) {
                isDraggingScrollbar = true;
                return true;
            }

            if (showingServers) {
                if (serverError != null) {
                    loadServers();
                    return true;
                }
                
                if (hoveredServerIndex >= 0 && hoveredServerIndex < filteredServers.size()) {
                    ServerInfo selected = filteredServers.get(hoveredServerIndex);
                    for (int i = 0; i < servers.size(); i++) {
                        if (servers.get(i).id == selected.id) {
                            selectedServerIndex = i;
                            break;
                        }
                    }
                    showingServers = false;
                    scrollOffset = 0;
                    pingSearchText = "";
                    pingSearchField.setText("");
                    loadPings(selected.id);
                    return true;
                }
            } else {
                if (backButtonHovered) {
                    showingServers = true;
                    scrollOffset = 0;
                    pings.clear();
                    filteredPings.clear();
                    return true;
                }
                
                if (hoveredFilter >= 0) {
                    filterEnabled[hoveredFilter] = !filterEnabled[hoveredFilter];
                    updateFilteredPings();
                    scrollOffset = 0;
                    return true;
                }
                
                if (hoveredTrackButton >= 0 && hoveredTrackButton < filteredPings.size()) {
                    PingInfo ping = filteredPings.get(hoveredTrackButton);
                    TrackedPingManager manager = TrackedPingManager.get();
                    
                    if (manager.isTracked(ping.id)) {
                        manager.untrack(ping.id);
                    } else {
                        TrackedPing tracked = new TrackedPing(
                            ping.id,
                            !ping.textContent.isEmpty() ? ping.textContent : ping.keywords,
                            ping.pingType,
                            ping.worldX,
                            ping.worldZ,
                            ping.username,
                            selectedServerIndex >= 0 ? servers.get(selectedServerIndex).id : 0
                        );
                        manager.track(tracked);
                    }
                    return true;
                }

                if (hoveredPingIndex >= 0 && hoveredPingIndex < filteredPings.size()) {
                    PingInfo ping = filteredPings.get(hoveredPingIndex);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            isDraggingScrollbar = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (isDraggingScrollbar) {
            int contentY = popupY + headerHeight + padding + (showingServers ? 44 : 70);
            int listHeight = popupY + popupHeight - contentY - padding;
            
            List<?> items = showingServers ? filteredServers : filteredPings;
            int itemHeight = showingServers ? 32 : 28;
            int visibleItems = (listHeight - 8) / itemHeight;
            int maxOffset = Math.max(0, items.size() - visibleItems);
            
            if (maxOffset > 0) {
                float ratio = (float) (mouseY - contentY) / listHeight;
                scrollOffset = Math.max(0, Math.min(maxOffset, (int) (ratio * maxOffset)));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (showingServers) {
            int listHeight = popupHeight - headerHeight - padding * 2 - 44;
            int visibleItems = (listHeight - 8) / 32;
            int maxScroll = Math.max(0, filteredServers.size() - visibleItems);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
        } else {
            int listHeight = popupHeight - headerHeight - padding * 2 - 70;
            int visibleItems = (listHeight - 8) / 28;
            int maxScroll = Math.max(0, filteredPings.size() - visibleItems);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
        }
        return true;
    }

    private static class ServerInfo {
        int id;
        String name;
        String slug;
        int totalMaps;
        int totalContributors;
        boolean isPrivate;
        String role; // "public", "owner", "shared"
    }

    private static class PingInfo {
        int id;
        String username;
        String pingType;
        int gridX;
        int gridZ;
        int worldX;
        int worldZ;
        String textContent;
        String keywords;
    }
}
