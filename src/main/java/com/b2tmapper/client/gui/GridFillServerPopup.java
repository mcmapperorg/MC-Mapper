package com.b2tmapper.client.gui;

import com.b2tmapper.config.ModConfig;
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

public class GridFillServerPopup extends BasePopup {

    private static final String API_BASE = "https://mc-mapper-production.up.railway.app/api";

    private List<ServerInfo> servers = new ArrayList<>();
    private List<ServerInfo> filteredServers = new ArrayList<>();
    private boolean loadingServers = true;
    private String serverError = null;

    private TextFieldWidget searchField;
    private String searchText = "";
    private int hoveredServerIndex = -1;
    private int scrollOffset = 0;
    private boolean isDraggingScrollbar = false;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    public GridFillServerPopup(Screen parent) {
        super(parent, "Select Server for Grid Fill");
        loadServers();
    }

    @Override
    protected void init() {
        super.init();
        popupWidth = 300;
        popupHeight = 320;
        popupX = (width - popupWidth) / 2;
        popupY = (height - popupHeight) / 2;

        int searchX = popupX + padding + 10;
        int searchY = popupY + headerHeight + padding + 16;
        int searchWidth = popupWidth - padding * 2 - 20;

        searchField = new TextFieldWidget(textRenderer, searchX, searchY, searchWidth, 16, Text.literal("Search"));
        searchField.setMaxLength(50);
        searchField.setPlaceholder(Text.literal("Search servers..."));
        searchField.setChangedListener(this::onSearchChanged);
        addDrawableChild(searchField);

        updateFilteredServers();
    }

    private void onSearchChanged(String text) {
        searchText = text.toLowerCase();
        scrollOffset = 0;
        updateFilteredServers();
    }

    private void updateFilteredServers() {
        filteredServers.clear();
        for (ServerInfo server : servers) {
            if (searchText.isEmpty() || server.name.toLowerCase().contains(searchText)) {
                filteredServers.add(server);
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
                                info.totalMaps = s.get("total_maps").getAsInt();
                                info.isPrivate = false;
                                servers.add(info);
                            }
                        }
                    }
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
                                        info.totalMaps = s.has("total_maps") ? s.get("total_maps").getAsInt() : 0;
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
                                        info.totalMaps = s.has("total_maps") ? s.get("total_maps").getAsInt() : 0;
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

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        int contentX = popupX + padding;
        int contentY = popupY + headerHeight + padding;
        int contentWidth = popupWidth - padding * 2;

        context.drawCenteredTextWithShadow(textRenderer, "Select a server to display map data", 
            contentX + contentWidth / 2, contentY, GRAY);
        contentY += 16;

        searchField.setY(contentY);
        searchField.setX(contentX + 10);
        searchField.setWidth(contentWidth - 20);
        contentY += 22;

        context.fill(contentX, contentY, contentX + contentWidth, contentY + 1, GREEN_BORDER());
        contentY += 6;

        int listHeight = popupY + popupHeight - contentY - padding;
        int listWidth = contentWidth - 12;
        context.fill(contentX, contentY, contentX + listWidth, contentY + listHeight, 0x44000000);
        drawBorder(context, contentX, contentY, listWidth, listHeight, GREEN_BORDER());

        if (loadingServers) {
            context.drawCenteredTextWithShadow(textRenderer, "Loading servers...", 
                contentX + listWidth / 2, contentY + listHeight / 2, GRAY);
            return;
        }

        if (serverError != null) {
            context.drawCenteredTextWithShadow(textRenderer, "Error: " + serverError, 
                contentX + listWidth / 2, contentY + listHeight / 2 - 6, 0xFFFF4444);
            context.drawCenteredTextWithShadow(textRenderer, "Click to retry", 
                contentX + listWidth / 2, contentY + listHeight / 2 + 8, GRAY);
            return;
        }

        if (filteredServers.isEmpty()) {
            String msg = servers.isEmpty() ? "No servers available" : "No servers match search";
            context.drawCenteredTextWithShadow(textRenderer, msg, 
                contentX + listWidth / 2, contentY + listHeight / 2, GRAY);
            return;
        }

        hoveredServerIndex = -1;
        int itemHeight = 28;
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
                String roleIcon = "owner".equals(server.role) ? "[*]" : "[+]";
                int roleColor = "owner".equals(server.role) ? 0xFFFFD700 : 0xFF9B59B6;
                context.drawTextWithShadow(textRenderer, roleIcon, nameX, itemY + 4, roleColor);
                nameX += textRenderer.getWidth(roleIcon) + 4;
            }

            context.drawTextWithShadow(textRenderer, server.name, nameX, itemY + 4, WHITE);

            String stats = server.totalMaps + " maps" + (server.isPrivate ? " (private)" : "");
            context.drawTextWithShadow(textRenderer, stats, contentX + 8, itemY + 14, GRAY);

            int borderColor = hovered ? GREEN_BORDER() : 0x44338833;
            if (server.isPrivate && !hovered) {
                borderColor = "owner".equals(server.role) ? 0x44FFD700 : 0x449B59B6;
            }
            drawBorder(context, contentX + 4, itemY, listWidth - 8, itemHeight - 2, borderColor);
        }

        if (filteredServers.size() > visibleItems) {
            int scrollbarX = contentX + listWidth + 2;
            int scrollbarHeight = listHeight;
            float ratio = (float) visibleItems / filteredServers.size();
            int thumbHeight = Math.max(20, (int) (scrollbarHeight * ratio));
            int maxOffset = filteredServers.size() - visibleItems;
            int thumbY = contentY + (int) ((scrollbarHeight - thumbHeight) * ((float) scrollOffset / maxOffset));

            context.fill(scrollbarX, contentY, scrollbarX + 8, contentY + scrollbarHeight, 0x44000000);
            context.fill(scrollbarX + 1, thumbY, scrollbarX + 7, thumbY + thumbHeight, 0xFF008800);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (serverError != null) {
                loadServers();
                return true;
            }

            if (hoveredServerIndex >= 0 && hoveredServerIndex < filteredServers.size()) {
                ServerInfo selected = filteredServers.get(hoveredServerIndex);
                
                ModConfig config = ModConfig.get();
                config.gridFillServerId = selected.id;
                config.gridFillServerName = selected.name;
                ModConfig.save();
                
                
                close();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listHeight = popupHeight - headerHeight - padding * 2 - 44;
        int visibleItems = (listHeight - 8) / 28;
        int maxScroll = Math.max(0, filteredServers.size() - visibleItems);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
        return true;
    }

    private static class ServerInfo {
        int id;
        String name;
        int totalMaps;
        boolean isPrivate;
        String role;
    }
}
