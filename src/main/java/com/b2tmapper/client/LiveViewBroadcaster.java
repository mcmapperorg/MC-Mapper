package com.b2tmapper.client;

import com.b2tmapper.B2TMapperMod;
import com.b2tmapper.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class LiveViewBroadcaster {

    private static final String API_BASE = "https://mc-mapper-production.up.railway.app/api";
    private static final int UPDATE_INTERVAL_MS = 2000; // 2 seconds
    
    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> broadcastTask;
    private static boolean isRunning = false;
    
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();
    
    private static int lastSentX = Integer.MIN_VALUE;
    private static int lastSentZ = Integer.MIN_VALUE;
    private static float lastSentYaw = Float.MIN_VALUE;

    public static void start() {
        if (isRunning) {
            return;
        }
        
        ModConfig config = ModConfig.get();
        if (config.authToken == null || config.authToken.isEmpty()) {
            return;
        }
        
        if (config.liveViewServerId <= 0) {
            return;
        }
        
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LiveView-Broadcaster");
            t.setDaemon(true);
            return t;
        });
        
        broadcastTask = scheduler.scheduleAtFixedRate(
            LiveViewBroadcaster::broadcastLocation,
            0,
            UPDATE_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );
        
        isRunning = true;
    }

    public static void stop() {
        if (!isRunning) {
            return;
        }
        
        if (broadcastTask != null) {
            broadcastTask.cancel(false);
            broadcastTask = null;
        }
        
        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }
        
        sendOfflineStatus();
        
        isRunning = false;
        lastSentX = Integer.MIN_VALUE;
        lastSentZ = Integer.MIN_VALUE;
        lastSentYaw = Float.MIN_VALUE;
        
    }

    public static boolean isRunning() {
        return isRunning;
    }

    private static void broadcastLocation() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) {
                return;
            }
            
            ModConfig config = ModConfig.get();
            if (!config.liveViewEnabled || config.authToken == null || config.liveViewServerId <= 0) {
                return;
            }
            
            int x = (int) client.player.getX();
            int z = (int) client.player.getZ();
            int y = (int) client.player.getY();
            float yaw = client.player.getYaw();
            
            int gridX = Math.floorDiv(x, 128);
            int gridZ = Math.floorDiv(z, 128);
            
            String dimension = client.world.getRegistryKey().getValue().toString();
            
            JsonObject body = new JsonObject();
            body.addProperty("server_id", config.liveViewServerId);
            body.addProperty("x", x);
            body.addProperty("y", y);
            body.addProperty("z", z);
            body.addProperty("grid_x", gridX);
            body.addProperty("grid_z", gridZ);
            body.addProperty("yaw", yaw);
            body.addProperty("dimension", dimension);
            body.addProperty("online", true);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/live-view/update"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.authToken)
                .header("X-Mod-Version", B2TMapperMod.MOD_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                lastSentX = x;
                lastSentZ = z;
                lastSentYaw = yaw;
            } else {
            }
            
        } catch (Exception e) {
        }
    }

    private static void sendOfflineStatus() {
        try {
            ModConfig config = ModConfig.get();
            if (config.authToken == null || config.liveViewServerId <= 0) {
                return;
            }
            
            JsonObject body = new JsonObject();
            body.addProperty("server_id", config.liveViewServerId);
            body.addProperty("online", false);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/live-view/update"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.authToken)
                .header("X-Mod-Version", B2TMapperMod.MOD_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
            
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
        } catch (Exception e) {
        }
    }

    public static void initialize() {
        ModConfig config = ModConfig.get();
        if (config.liveViewEnabled && config.authToken != null && config.liveViewServerId > 0) {
            start();
        }
    }

    public static void shutdown() {
        stop();
    }
}
