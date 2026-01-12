package com.b2tmapper.client.gui;

import com.b2tmapper.client.LiveViewBroadcaster;
import com.b2tmapper.client.MapStreamingService;
import com.b2tmapper.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class LiveViewPopup extends Screen {

    private final Screen parent;
    private int popupX, popupY, popupWidth, popupHeight;

    private int GREEN_BG() { return ModConfig.get().uiTheme.bg; }
    private int GREEN_HOVER() { return ModConfig.get().uiTheme.hover; }
    private int GREEN_BORDER() { return ModConfig.get().uiTheme.border; }
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int GREEN = 0xFF55FF55;

    private static final String API_BASE = "https://mc-mapper-production.up.railway.app/api";
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    private List<ServerInfo> servers = new ArrayList<>();
    private boolean loading = true;
    private String error = null;

    private static class ServerInfo {
        int id;
        String name;
        String address;
        boolean isPrivate;
    }

    public LiveViewPopup(Screen parent) {
        super(Text.literal("Live View"));
        this.parent = parent;
        loadServers();
    }

    private void loadServers() {
        loading = true;
        error = null;

        CompletableFuture.runAsync(() -> {
            try {
                servers.clear();

                // Load public servers (no auth required)
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
                                info.address = s.has("server_address") && !s.get("server_address").isJsonNull()
                                    ? s.get("server_address").getAsString() : "";
                                info.isPrivate = false;
                                servers.add(info);
                            }
                        }
                    } else {
                        error = "API returned error";
                    }
                } else {
                    error = "HTTP " + response.statusCode();
                }

                // Also load private servers if logged in
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
                                        info.address = s.has("server_address") && !s.get("server_address").isJsonNull()
                                            ? s.get("server_address").getAsString() : "";
                                        info.isPrivate = true;
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
                                        info.address = s.has("server_address") && !s.get("server_address").isJsonNull()
                                            ? s.get("server_address").getAsString() : "";
                                        info.isPrivate = true;
                                        servers.add(info);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Ignore private server errors
                    }
                }

            } catch (Exception e) {
                error = "Connection failed";
                e.printStackTrace();
            }
            loading = false;
        });
    }

    @Override
    protected void init() {
        super.init();

        popupWidth = 280;
        popupHeight = 250;
        popupX = (width - popupWidth) / 2;
        popupY = (height - popupHeight) / 2;

        // Back button
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Back"),
            button -> close()
        ).dimensions(popupX + popupWidth/2 - 40, popupY + popupHeight - 30, 80, 20).build());
    }

    // Override to disable Minecraft 1.21's blur effect
    @Override
    protected void applyBlur(float delta) {
        // Don't apply blur
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x88000000);
        
        context.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, GREEN_BG());
        drawBorder(context, popupX, popupY, popupWidth, popupHeight, GREEN_BORDER());

        String title = "Live View & Streaming";
        int titleX = popupX + (popupWidth - textRenderer.getWidth(title)) / 2;
        context.drawTextWithShadow(textRenderer, title, titleX, popupY + 10, WHITE);

        ModConfig config = ModConfig.get();
        
        // Status
        String status = config.liveViewEnabled ? "Live View: Active" : "Live View: Inactive";
        int statusColor = config.liveViewEnabled ? GREEN : GRAY;
        context.drawTextWithShadow(textRenderer, status, popupX + 20, popupY + 30, statusColor);

        // Current server
        String serverText = "Server: " + (config.liveViewServerName != null ? config.liveViewServerName : "None");
        context.drawTextWithShadow(textRenderer, serverText, popupX + 20, popupY + 45, GRAY);

        // Instructions
        context.drawTextWithShadow(textRenderer, "Select a server to enable:", popupX + 20, popupY + 65, WHITE);

        // Server list
        int listY = popupY + 80;
        if (loading) {
            context.drawTextWithShadow(textRenderer, "Loading...", popupX + 20, listY, GRAY);
        } else if (error != null) {
            context.drawTextWithShadow(textRenderer, error, popupX + 20, listY, 0xFFFF5555);
        } else if (servers.isEmpty()) {
            context.drawTextWithShadow(textRenderer, "No servers available", popupX + 20, listY, GRAY);
        } else {
            for (int i = 0; i < Math.min(servers.size(), 6); i++) {
                ServerInfo server = servers.get(i);
                int itemY = listY + (i * 22);
                
                boolean hovered = mouseX >= popupX + 15 && mouseX < popupX + popupWidth - 15 
                    && mouseY >= itemY && mouseY < itemY + 20;
                boolean selected = config.liveViewServerId == server.id;
                
                int bg = selected ? ModConfig.get().uiTheme.selectedBg : (hovered ? GREEN_HOVER() : GREEN_BG());
                context.fill(popupX + 15, itemY, popupX + popupWidth - 15, itemY + 20, bg);
                
                int borderColor = selected ? ModConfig.get().uiTheme.selectedBorder : GREEN_BORDER();
                drawBorder(context, popupX + 15, itemY, popupWidth - 30, 20, borderColor);
                
                String displayName = server.isPrivate ? "[P] " + server.name : server.name;
                context.drawTextWithShadow(textRenderer, displayName, popupX + 22, itemY + 6, WHITE);
            }
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && !loading && error == null) {
            int listY = popupY + 80;
            for (int i = 0; i < Math.min(servers.size(), 6); i++) {
                int itemY = listY + (i * 22);
                if (mouseX >= popupX + 15 && mouseX < popupX + popupWidth - 15 
                    && mouseY >= itemY && mouseY < itemY + 20) {
                    selectServer(servers.get(i));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void selectServer(ServerInfo server) {
        ModConfig config = ModConfig.get();
        
        // Set Live View
        config.liveViewEnabled = true;
        config.liveViewServerId = server.id;
        config.liveViewServerName = server.name;
        
        // Also set Streaming server
        config.streamingServerId = server.id;
        config.streamingServerName = server.name;
        config.streamingServerAddress = server.address;
        config.streamingEnabled = true;
        
        ModConfig.save();
        
        // Start services
        LiveViewBroadcaster.start();
        MapStreamingService.start();
        
        // Notify
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.sendMessage(
                Text.literal("§aLive View & Mapping enabled for " + server.name),
                false
            );
        }
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
