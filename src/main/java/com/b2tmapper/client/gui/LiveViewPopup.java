package com.b2tmapper.client.gui;

import com.b2tmapper.config.ModConfig;
import com.b2tmapper.client.LiveViewBroadcaster;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
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

public class LiveViewPopup extends BasePopup {

    private static final String API_BASE = "https://mc-mapper-production.up.railway.app/api";

    private boolean enableButtonHovered = false;
    private boolean selectServerHovered = false;
    private boolean showingServerList = false;
    
    private List<ServerInfo> servers = new ArrayList<>();
    private List<ServerInfo> filteredServers = new ArrayList<>();
    private boolean loadingServers = false;
    private String serverError = null;
    private TextFieldWidget searchField;
    private String searchText = "";
    private int hoveredServerIndex = -1;
    private int scrollOffset = 0;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    public LiveViewPopup(Screen parent) {
        super(parent, "Live View");
    }

    @Override
    protected void init() {
        super.init();
        popupWidth = 280;
        popupHeight = 320;
        popupX = (width - popupWidth) / 2;
        popupY = (height - popupHeight) / 2;

        int searchX = popupX + padding + 10;
        int searchY = popupY + headerHeight + padding + 120;
        int searchWidth = popupWidth - padding * 2 - 20;

        searchField = new TextFieldWidget(textRenderer, searchX, searchY, searchWidth, 16, Text.literal("Search"));
        searchField.setMaxLength(50);
        searchField.setPlaceholder(Text.literal("Search servers..."));
        searchField.setChangedListener(this::onSearchChanged);
        searchField.setVisible(false);
        addDrawableChild(searchField);
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

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        ModConfig config = ModConfig.get();
        
        int contentX = popupX + padding;
        int contentY = popupY + headerHeight + padding;
        int contentWidth = popupWidth - padding * 2;

        if (showingServerList) {
            renderServerList(context, mouseX, mouseY, contentX, contentY, contentWidth);
            return;
        }

        context.drawCenteredTextWithShadow(textRenderer, "Broadcast your location to the website", 
            contentX + contentWidth / 2, contentY, GRAY);
        contentY += 12;
        context.drawCenteredTextWithShadow(textRenderer, "for real-time tracking", 
            contentX + contentWidth / 2, contentY, GRAY);
        contentY += 20;

        boolean isEnabled = config.liveViewEnabled;
        boolean hasServer = config.liveViewServerId > 0;
        boolean isLoggedIn = config.authToken != null && !config.authToken.isEmpty();
        
        String statusText;
        int statusColor;
        
        if (!isLoggedIn) {
            statusText = "Status: Not Logged In";
            statusColor = 0xFFFF4444;
        } else if (!hasServer) {
            statusText = "Status: No Server Selected";
            statusColor = 0xFFFF8844;
        } else if (isEnabled) {
            statusText = "Status: BROADCASTING";
            statusColor = 0xFF44FF44;
        } else {
            statusText = "Status: Disabled";
            statusColor = 0xFF888888;
        }
        
        context.drawCenteredTextWithShadow(textRenderer, statusText, 
            contentX + contentWidth / 2, contentY, statusColor);
        contentY += 20;

        int btnWidth = 120;
        int btnHeight = 24;
        int btnX = contentX + (contentWidth - btnWidth) / 2;
        
        boolean canEnable = isLoggedIn && hasServer;
        
        if (canEnable) {
            enableButtonHovered = mouseX >= btnX && mouseX < btnX + btnWidth
                    && mouseY >= contentY && mouseY < contentY + btnHeight;
            
            String btnText = isEnabled ? "Disable Live View" : "Enable Live View";
            int btnBg = enableButtonHovered ? GREEN_HOVER() : GREEN_BUTTON();
            int btnBorder = isEnabled ? 0xFF44FF44 : GREEN_BORDER();
            
            context.fill(btnX, contentY, btnX + btnWidth, contentY + btnHeight, btnBg);
            drawBorder(context, btnX, contentY, btnWidth, btnHeight, btnBorder);
            context.drawCenteredTextWithShadow(textRenderer, btnText, 
                btnX + btnWidth / 2, contentY + (btnHeight - 8) / 2, WHITE);
        } else {
            enableButtonHovered = false;
            context.fill(btnX, contentY, btnX + btnWidth, contentY + btnHeight, 0xD9442222);
            drawBorder(context, btnX, contentY, btnWidth, btnHeight, 0xFF663333);
            String btnText = !isLoggedIn ? "Login Required" : "Select Server First";
            context.drawCenteredTextWithShadow(textRenderer, btnText, 
                btnX + btnWidth / 2, contentY + (btnHeight - 8) / 2, 0xFF888888);
        }
        contentY += btnHeight + 20;

        drawSectionHeader(context, contentX, contentY, "Server");
        contentY += 16;

        String serverText = config.liveViewServerName != null ? config.liveViewServerName : "No Server Selected";
        if (serverText.length() > 25) serverText = serverText.substring(0, 23) + "..";
        
        int serverColor = hasServer ? 0xFF44FF44 : 0xFFFF8844;
        context.drawTextWithShadow(textRenderer, serverText, contentX, contentY, serverColor);
        contentY += 14;

        int selectBtnWidth = 100;
        selectServerHovered = mouseX >= contentX && mouseX < contentX + selectBtnWidth
                && mouseY >= contentY && mouseY < contentY + 18;
        
        int selectBg = selectServerHovered ? GREEN_HOVER() : GREEN_BUTTON();
        context.fill(contentX, contentY, contentX + selectBtnWidth, contentY + 18, selectBg);
        drawBorder(context, contentX, contentY, selectBtnWidth, 18, GREEN_BORDER());
        context.drawCenteredTextWithShadow(textRenderer, "Select Server", 
            contentX + selectBtnWidth / 2, contentY + 5, WHITE);
        contentY += 30;

        context.fill(contentX, contentY, contentX + contentWidth, contentY + 60, 0x40000000);
        drawBorder(context, contentX, contentY, contentWidth, 60, 0x44338833);
        
        contentY += 6;
        context.drawCenteredTextWithShadow(textRenderer, "\u00A7eHow it works:", 
            contentX + contentWidth / 2, contentY, WHITE);
        contentY += 12;
        context.drawCenteredTextWithShadow(textRenderer, "Your position is 100% private", 
            contentX + contentWidth / 2, contentY, GRAY);
        contentY += 10;
        context.drawCenteredTextWithShadow(textRenderer, "Only YOU can see it on the website", 
            contentX + contentWidth / 2, contentY, GRAY);
        contentY += 10;
        context.drawCenteredTextWithShadow(textRenderer, "Updates every 2 seconds", 
            contentX + contentWidth / 2, contentY, GRAY);
    }

    private void renderServerList(DrawContext context, int mouseX, int mouseY, int contentX, int contentY, int contentWidth) {
        int backBtnWidth = 50;
        boolean backHovered = mouseX >= contentX && mouseX < contentX + backBtnWidth
                && mouseY >= contentY && mouseY < contentY + 16;
        
        int backBg = backHovered ? GREEN_HOVER() : GREEN_BUTTON();
        context.fill(contentX, contentY, contentX + backBtnWidth, contentY + 16, backBg);
        drawBorder(context, contentX, contentY, backBtnWidth, 16, GREEN_BORDER());
        context.drawCenteredTextWithShadow(textRenderer, "< Back", contentX + backBtnWidth / 2, contentY + 4, WHITE);
        
        if (backHovered && isMouseClicked()) {
            showingServerList = false;
            searchField.setVisible(false);
            return;
        }

        context.drawTextWithShadow(textRenderer, "Select Server", contentX + backBtnWidth + 10, contentY + 4, WHITE);
        contentY += 22;

        searchField.setVisible(true);
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
                contentX + listWidth / 2, contentY + listHeight / 2, 0xFFFF4444);
            return;
        }

        if (filteredServers.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "No servers available", 
                contentX + listWidth / 2, contentY + listHeight / 2, GRAY);
            return;
        }

        hoveredServerIndex = -1;
        int itemHeight = 26;
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

            context.drawTextWithShadow(textRenderer, server.name, contentX + 8, itemY + 4, WHITE);
            
            String info = server.totalMaps + " maps";
            context.drawTextWithShadow(textRenderer, info, contentX + 8, itemY + 14, GRAY);

            drawBorder(context, contentX + 4, itemY, listWidth - 8, itemHeight - 2, 
                hovered ? GREEN_BORDER() : 0x44338833);
        }
    }

    private boolean mouseClickedFlag = false;
    
    private boolean isMouseClicked() {
        return mouseClickedFlag;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            mouseClickedFlag = true;
            
            ModConfig config = ModConfig.get();

            if (showingServerList) {
                int contentX = popupX + padding;
                int contentY = popupY + headerHeight + padding;
                int backBtnWidth = 50;
                
                if (mouseX >= contentX && mouseX < contentX + backBtnWidth
                        && mouseY >= contentY && mouseY < contentY + 16) {
                    showingServerList = false;
                    searchField.setVisible(false);
                    mouseClickedFlag = false;
                    return true;
                }

                if (hoveredServerIndex >= 0 && hoveredServerIndex < filteredServers.size()) {
                    ServerInfo selected = filteredServers.get(hoveredServerIndex);
                    config.liveViewServerId = selected.id;
                    config.liveViewServerName = selected.name;
                    ModConfig.save();
                    
                    showingServerList = false;
                    searchField.setVisible(false);
                    mouseClickedFlag = false;
                    return true;
                }
            } else {
                if (enableButtonHovered) {
                    config.liveViewEnabled = !config.liveViewEnabled;
                    ModConfig.save();
                    
                    if (config.liveViewEnabled) {
                        LiveViewBroadcaster.start();
                    } else {
                        LiveViewBroadcaster.stop();
                    }
                    
                    mouseClickedFlag = false;
                    return true;
                }

                if (selectServerHovered) {
                    showingServerList = true;
                    loadServers();
                    mouseClickedFlag = false;
                    return true;
                }
            }
            
            mouseClickedFlag = false;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (showingServerList) {
            int listHeight = popupHeight - headerHeight - padding * 2 - 50;
            int visibleItems = (listHeight - 8) / 26;
            int maxScroll = Math.max(0, filteredServers.size() - visibleItems);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (int) verticalAmount));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void loadServers() {
        loadingServers = true;
        serverError = null;
        servers.clear();

        CompletableFuture.runAsync(() -> {
            try {
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

    private static class ServerInfo {
        int id;
        String name;
        int totalMaps;
    }
}
