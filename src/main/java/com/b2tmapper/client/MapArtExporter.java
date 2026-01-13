package com.b2tmapper.client;

import com.b2tmapper.B2TMapperMod;
import com.b2tmapper.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class MapArtExporter {

    private static final String API_BASE = "https://mc-mapper-production.up.railway.app/api";
    private static final int COOLDOWN_MINUTES = 20;
    
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();
    
    private static boolean isExporting = false;

    public static void tryExport() {
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (client.player == null || client.world == null) {
            return;
        }
        
        if (isExporting) {
            sendMessage(client, "§eExport already in progress...");
            return;
        }
        
        ModConfig config = ModConfig.get();
        
        if (config.authToken == null || config.authToken.isEmpty()) {
            sendMessage(client, "§cYou must link your account first! Use Right Shift > Account");
            return;
        }
        
        if (config.streamingServerId <= 0) {
            sendMessage(client, "§cNo server selected! Enable Map Streaming first.");
            return;
        }
        
        ItemStack heldItem = client.player.getMainHandStack();
        if (!(heldItem.getItem() instanceof FilledMapItem)) {
            heldItem = client.player.getOffHandStack();
            if (!(heldItem.getItem() instanceof FilledMapItem)) {
                sendMessage(client, "§cYou must be holding a filled map!");
                return;
            }
        }
        
        MapIdComponent mapIdComponent = heldItem.get(DataComponentTypes.MAP_ID);
        if (mapIdComponent == null) {
            sendMessage(client, "§cCouldn't read map data!");
            return;
        }
        
        MapState mapState = FilledMapItem.getMapState(mapIdComponent, client.world);
        if (mapState == null) {
            sendMessage(client, "§cCouldn't read map state!");
            return;
        }
        
        if (mapState.scale != 0) {
            sendMessage(client, "§cOnly scale 0 (128x128) maps are supported for map art!");
            return;
        }
        
        if (config.lastMapArtExport > 0) {
            long elapsed = System.currentTimeMillis() - config.lastMapArtExport;
            long cooldownMs = COOLDOWN_MINUTES * 60 * 1000L;
            
            if (elapsed < cooldownMs) {
                long remainingMs = cooldownMs - elapsed;
                int remainingMinutes = (int) Math.ceil(remainingMs / 60000.0);
                sendMessage(client, "§cPlease wait " + remainingMinutes + " minute(s) before exporting another map art.");
                return;
            }
        }
        
        byte[] colorBytes = mapState.colors;
        if (colorBytes == null || colorBytes.length != 16384) {
            sendMessage(client, "§cInvalid map data!");
            return;
        }
        
        int[] colors = new int[16384];
        for (int i = 0; i < 16384; i++) {
            colors[i] = colorBytes[i] & 0xFF;
        }
        
        int nonEmptyCount = 0;
        for (int color : colors) {
            if (color != 0) nonEmptyCount++;
        }
        
        if (nonEmptyCount < 1000) {
            sendMessage(client, "§cThis map appears to be mostly empty!");
            return;
        }
        
        String playerName = client.player.getName().getString();
        String serverAddress = B2TMapperMod.getCurrentServerAddress();
        
        sendMessage(client, "§eUploading map art...");
        isExporting = true;
        
        CompletableFuture.runAsync(() -> {
            try {
                exportMapArt(config, colors, playerName, serverAddress);
            } finally {
                isExporting = false;
            }
        });
    }
    
    private static void exportMapArt(ModConfig config, int[] colors, String playerName, String serverAddress) {
        try {
            String secret = getStreamingSecret(config);
            if (secret == null) {
                sendMessageAsync("§cFailed to get credentials. Make sure Map Streaming is connected.");
                return;
            }
            
            JsonObject body = new JsonObject();
            body.addProperty("server_id", config.streamingServerId);
            body.addProperty("player_ign", playerName);
            body.addProperty("server_address", serverAddress != null ? serverAddress : "");
            body.addProperty("streaming_secret", secret);
            
            JsonArray colorsArray = new JsonArray();
            for (int color : colors) {
                colorsArray.add(color);
            }
            body.add("colors", colorsArray);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/display-case/upload"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.authToken)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                config.lastMapArtExport = System.currentTimeMillis();
                ModConfig.save();
                sendMessageAsync("§a✓ Map art submitted for review!");
                
            } else if (response.statusCode() == 409) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                String error = json.has("error") ? json.get("error").getAsString() : "Duplicate map art";
                sendMessageAsync("§c" + error);
                
            } else if (response.statusCode() == 429) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                String error = json.has("error") ? json.get("error").getAsString() : "Please wait before uploading again";
                sendMessageAsync("§c" + error);
                
            } else if (response.statusCode() == 403) {
                String responseBody = response.body();
                if (responseBody.contains("SECRET_INVALID") || responseBody.contains("expired")) {
                    sendMessageAsync("§cCredentials expired. Reconnect Map Streaming in the menu.");
                } else if (responseBody.contains("mismatch")) {
                    sendMessageAsync("§cServer address mismatch. Are you on the right server?");
                } else {
                    sendMessageAsync("§cAccess denied.");
                }
                
            } else {
                sendMessageAsync("§cUpload failed (status " + response.statusCode() + ")");
            }
            
        } catch (Exception e) {
            sendMessageAsync("§cUpload failed: " + e.getMessage());
        }
    }
    
    private static String getStreamingSecret(ModConfig config) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/servers/" + config.streamingServerId + "/streaming-credentials"))
                .header("Authorization", "Bearer " + config.authToken)
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                if (json.get("success").getAsBoolean()) {
                    return json.get("streaming_secret").getAsString();
                }
            }
        } catch (Exception e) {
        }
        return null;
    }
    
    private static void sendMessage(MinecraftClient client, String message) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal(message), false);
        }
    }
    
    private static void sendMessageAsync(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> sendMessage(client, message));
    }
}
