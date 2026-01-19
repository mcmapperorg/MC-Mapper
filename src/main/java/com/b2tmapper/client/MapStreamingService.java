package com.b2tmapper.client;

import com.b2tmapper.B2TMapperMod;
import com.b2tmapper.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MapStreamingService {

    private static final String API_BASE = "https://mc-mapper-production.up.railway.app/api";
    private static final int STREAM_INTERVAL_MS = 10000;
    private static final int SECRET_REFRESH_MS = 4 * 60 * 60 * 1000;
    private static final long START_RETRY_COOLDOWN_MS = 30000;
    private static final long CONNECTION_DELAY_MS = 25000;
    private static final int GRID_SKIP_THRESHOLD = 3;

    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> streamTask;
    private static ScheduledFuture<?> secretRefreshTask;
    private static boolean isRunning = false;
    private static boolean inSafeZone = false;
    private static boolean inOverworld = true;
    private static boolean eventsRegistered = false;
    private static boolean gridSkipKilled = false;
    private static boolean safeZoneKilled = false;

    private static String streamingSecret = null;
    private static long secretFetchedAt = 0;
    private static long lastStartAttempt = 0;
    private static long connectionTime = 0;

    private static int detectedServerId = -1;
    private static String detectedServerName = null;
    private static String detectedServerAddress = null;
    private static String lastConnectedServer = null;
    private static int tickCounter = 0;

    private static int lastUploadedGridX = Integer.MIN_VALUE;
    private static int lastUploadedGridZ = Integer.MIN_VALUE;

    private static final Set<String> serverVerifiedChunks = ConcurrentHashMap.newKeySet();
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();
    private static final ConcurrentHashMap<String, int[]> gridData = new ConcurrentHashMap<>();
    private static final Map<String, Integer> lastSentCompletion = new HashMap<>();

    private static int lastPlayerGridX = Integer.MIN_VALUE;
    private static int lastPlayerGridZ = Integer.MIN_VALUE;

    public static void markChunkAsServerVerified(int chunkX, int chunkZ) {
        String key = chunkX + "," + chunkZ;
        serverVerifiedChunks.add(key);
    }

    private static boolean isChunkServerVerified(int chunkX, int chunkZ) {
        String key = chunkX + "," + chunkZ;
        return serverVerifiedChunks.contains(key);
    }

    public static void registerEvents() {
        if (eventsRegistered) return;
        eventsRegistered = true;

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (!isRunning) return;

            ModConfig config = ModConfig.get();
            if (!config.streamingEnabled) return;

            String currentServer = B2TMapperMod.getCurrentServerAddress();
            if (currentServer == null || currentServer.isEmpty()) {
                return;
            }
            
            if (detectedServerAddress != null && !detectedServerAddress.isEmpty()) {
                if (!serverAddressMatches(currentServer, detectedServerAddress)) {
                    return;
                }
            }

            int chunkX = chunk.getPos().x;
            int chunkZ = chunk.getPos().z;

            long timeSinceConnection = System.currentTimeMillis() - connectionTime;
            if (timeSinceConnection < CONNECTION_DELAY_MS) {
                return;
            }

            if (!serverVerifiedChunks.isEmpty() && !isChunkServerVerified(chunkX, chunkZ)) {
                return;
            }

            processChunkImmediate(world, chunk, config);
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            System.out.println("[MCMapper] Connected to server - clearing all cached data");
            System.out.println("[MCMapper] Starting 25 second delay to filter Baritone cache...");
            connectionTime = System.currentTimeMillis();
            clearData();
            
            ModConfig config = ModConfig.get();
            if (config.streamingEnabled) {
                config.streamingEnabled = false;
                ModConfig.save();
                System.out.println("[MCMapper] Streaming disabled on reconnect - must be manually re-enabled");
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            System.out.println("[MCMapper] Disconnected - stopping streaming");
            stop();
            clearData();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            tickCounter++;
            if (tickCounter % 40 != 0) return;

            if (client.player == null || client.world == null) return;

            ModConfig config = ModConfig.get();

            if (connectionTime > 0 && isRunning) {
                long timeSinceConnection = System.currentTimeMillis() - connectionTime;
                if (timeSinceConnection >= CONNECTION_DELAY_MS && timeSinceConnection < CONNECTION_DELAY_MS + 2000) {
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal("§a[MCMapper] Delay complete - now capturing chunks"),
                        true
                    );
                }
            }

            if (config.streamingEnabled && !isRunning) {
                long now = System.currentTimeMillis();
                if (now - lastStartAttempt >= START_RETRY_COOLDOWN_MS) {
                    lastStartAttempt = now;
                    start();
                }
            }

            if (!config.streamingEnabled && isRunning) {
                stop();
            }
        });

        System.out.println("[MCMapper] Events registered (with 25s connection delay)");
    }

    private static void processChunkImmediate(World world, WorldChunk chunk, ModConfig config) {
        try {
            int serverId = getActiveServerId();
            if (serverId <= 0) return;

            String dimension = world.getRegistryKey().getValue().toString();
            if (!dimension.equals("minecraft:overworld")) {
                return;
            }

            int chunkX = chunk.getPos().x;
            int chunkZ = chunk.getPos().z;
            int baseX = chunkX * 16;
            int baseZ = chunkZ * 16;

            if (config.safeZoneEnabled && config.safeZoneRadius > 0) {
                int centerX = baseX + 8;
                int centerZ = baseZ + 8;
                if (!isInSafeZoneCheck(centerX, centerZ)) {
                    return;
                }
            }

            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int x = baseX + dx;
                    int z = baseZ + dz;

                    if (config.safeZoneEnabled && config.safeZoneRadius > 0) {
                        if (!isInSafeZoneCheck(x, z)) {
                            continue;
                        }
                    }

                    int gridX = Math.floorDiv(x + 64, 128);
                    int gridZ = Math.floorDiv(z + 64, 128);
                    int localX = Math.floorMod(x + 64, 128);
                    int localZ = Math.floorMod(z + 64, 128);

                    String gridKey = gridX + "," + gridZ;
                    int[] colors = gridData.computeIfAbsent(gridKey, k -> new int[16384]);
                    int pixelIndex = localZ * 128 + localX;

                    if (colors[pixelIndex] == 0) {
                        int mapColor = calculateMapColorFromChunk(world, chunk, x, z);
                        if (mapColor > 0) {
                            colors[pixelIndex] = mapColor;
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[MCMapper] Chunk processing error: " + e.getMessage());
        }
    }

    private static int calculateMapColorFromChunk(World world, WorldChunk chunk, int x, int z) {
        try {
            int y = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
            if (y <= world.getBottomY()) {
                return 0;
            }

            BlockPos pos = new BlockPos(x, y - 1, z);
            BlockState state = chunk.getBlockState(pos);
            MapColor mapColor = state.getMapColor(world, pos);

            if (mapColor == null || mapColor == MapColor.CLEAR) {
                return 0;
            }

            int shade = 2;

            if (mapColor == MapColor.WATER_BLUE) {
                int waterDepth = 0;
                BlockPos checkPos = pos;
                while (checkPos.getY() > world.getBottomY() && waterDepth < 20) {
                    BlockState checkState = chunk.getBlockState(checkPos);
                    MapColor checkColor = checkState.getMapColor(world, checkPos);
                    if (checkColor != MapColor.WATER_BLUE) break;
                    waterDepth++;
                    checkPos = checkPos.down();
                }

                if (waterDepth <= 2) shade = 3;
                else if (waterDepth <= 4) shade = 2;
                else if (waterDepth <= 8) shade = 1;
                else shade = 0;
            }

            int colorId = mapColor.id * 4 + shade;
            return Math.max(1, Math.min(255, colorId));
        } catch (Exception e) {
            return 0;
        }
    }

    private static boolean detectStreamingServer() {
        try {
            String serverAddress = B2TMapperMod.getCurrentServerAddress();
            if (serverAddress == null || serverAddress.isEmpty()) {
                serverAddress = "singleplayer";
            }

            System.out.println("[MCMapper] Detecting server for address: " + serverAddress);
            lastConnectedServer = serverAddress;

            ModConfig config = ModConfig.get();
            String encodedAddress = URLEncoder.encode(serverAddress, StandardCharsets.UTF_8);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/streaming/detect?address=" + encodedAddress))
                .header("Authorization", "Bearer " + config.authToken)
                .header("X-Mod-Version", B2TMapperMod.MOD_VERSION)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                if (json.has("success") && json.get("success").getAsBoolean()) {
                    detectedServerId = json.get("server_id").getAsInt();
                    detectedServerName = json.has("server_name") ? json.get("server_name").getAsString() : "Unknown";
                    detectedServerAddress = json.has("server_address") ? json.get("server_address").getAsString() : serverAddress;

                    if (json.has("streaming_secret") && !json.get("streaming_secret").isJsonNull()) {
                        streamingSecret = json.get("streaming_secret").getAsString();
                        secretFetchedAt = System.currentTimeMillis();
                    }

                    System.out.println("[MCMapper] Auto-detected: " + detectedServerName + " (ID: " + detectedServerId + ")");
                    return true;
                }
            }

            if (config.streamingServerId > 0 && !serverAddress.equalsIgnoreCase("singleplayer")) {
                System.out.println("[MCMapper] Using manual config server ID: " + config.streamingServerId);
                detectedServerId = config.streamingServerId;
                detectedServerName = config.streamingServerName;
                detectedServerAddress = config.streamingServerAddress;
                return true;
            }

            if (serverAddress.equalsIgnoreCase("singleplayer")) {
                System.out.println("[MCMapper] Singleplayer detected - no server configured, not streaming");
            } else {
                System.out.println("[MCMapper] No server detected and no manual config");
            }
            return false;

        } catch (Exception e) {
            System.out.println("[MCMapper] Detection error: " + e.getMessage());

            String serverAddress = B2TMapperMod.getCurrentServerAddress();
            if (serverAddress == null || serverAddress.isEmpty()) {
                serverAddress = "singleplayer";
            }
            
            ModConfig config = ModConfig.get();
            if (config.streamingServerId > 0 && !serverAddress.equalsIgnoreCase("singleplayer")) {
                detectedServerId = config.streamingServerId;
                detectedServerName = config.streamingServerName;
                detectedServerAddress = config.streamingServerAddress;
                return true;
            }
            return false;
        }
    }

    private static int getActiveServerId() {
        if (detectedServerId > 0) return detectedServerId;
        return ModConfig.get().streamingServerId;
    }

    public static boolean fetchStreamingCredentials() {
        try {
            ModConfig config = ModConfig.get();
            if (config.authToken == null || config.authToken.isEmpty()) {
                return false;
            }

            int serverId = getActiveServerId();
            if (serverId <= 0) {
                return false;
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/servers/" + serverId + "/streaming-credentials"))
                .header("Authorization", "Bearer " + config.authToken)
                .header("X-Mod-Version", B2TMapperMod.MOD_VERSION)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                if (json.has("success") && json.get("success").getAsBoolean()) {
                    if (json.has("streaming_secret") && !json.get("streaming_secret").isJsonNull()) {
                        streamingSecret = json.get("streaming_secret").getAsString();
                        secretFetchedAt = System.currentTimeMillis();
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            System.out.println("[MCMapper] Credentials error: " + e.getMessage());
        }
        return false;
    }

    private static boolean secretNeedsRefresh() {
        if (streamingSecret == null || secretFetchedAt == 0) return true;
        return System.currentTimeMillis() - secretFetchedAt > SECRET_REFRESH_MS;
    }

    private static void refreshSecretIfNeeded() {
        if (secretNeedsRefresh()) {
            fetchStreamingCredentials();
        }
    }

    public static void start() {
        if (isRunning) {
            return;
        }

        ModConfig config = ModConfig.get();
        if (config.authToken == null || config.authToken.isEmpty()) {
            System.out.println("[MCMapper] Cannot start: no auth token");
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (config.safeZoneEnabled && config.safeZoneRadius > 0 && client.player != null) {
            int playerX = (int) Math.floor(client.player.getX());
            int playerZ = (int) Math.floor(client.player.getZ());
            if (!isInSafeZoneCheck(playerX, playerZ)) {
                System.out.println("[MCMapper] Cannot start: player outside safe zone");
                if (client.player != null) {
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal("§c[MCMapper] Cannot start - you are outside your safe zone!"),
                        false
                    );
                }
                config.streamingEnabled = false;
                ModConfig.save();
                return;
            }
        }

        if (!detectStreamingServer()) {
            System.out.println("[MCMapper] Cannot start: no server detected");
            return;
        }

        if (!fetchStreamingCredentials()) {
            System.out.println("[MCMapper] Warning: Could not fetch credentials");
        }

        gridSkipKilled = false;
        safeZoneKilled = false;
        lastUploadedGridX = Integer.MIN_VALUE;
        lastUploadedGridZ = Integer.MIN_VALUE;

        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "MapStreaming-Service");
            t.setDaemon(true);
            return t;
        });

        streamTask = scheduler.scheduleAtFixedRate(
            MapStreamingService::streamPendingGrids,
            5000,
            STREAM_INTERVAL_MS,
            TimeUnit.MILLISECONDS
        );

        secretRefreshTask = scheduler.scheduleAtFixedRate(
            MapStreamingService::refreshSecretIfNeeded,
            SECRET_REFRESH_MS,
            60 * 60 * 1000,
            TimeUnit.MILLISECONDS
        );

        isRunning = true;
        lastStartAttempt = 0;

        long timeSinceConnection = System.currentTimeMillis() - connectionTime;
        long remainingDelay = Math.max(0, CONNECTION_DELAY_MS - timeSinceConnection);

        System.out.println("[MCMapper] Streaming STARTED - " + detectedServerName + " (ID: " + detectedServerId + ")");
        if (remainingDelay > 0) {
            System.out.println("[MCMapper] Waiting " + (remainingDelay / 1000) + "s before processing chunks...");
        }

        if (client.player != null) {
            if (remainingDelay > 0) {
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("§e[MCMapper] Streaming to: " + detectedServerName + " (waiting " + (remainingDelay / 1000) + "s)"),
                    true
                );
            } else {
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("§a[MCMapper] Streaming to: " + detectedServerName),
                    true
                );
            }
        }
    }

    public static void stop() {
        if (!isRunning) {
            return;
        }

        if (!gridSkipKilled && !safeZoneKilled) {
            streamPendingGrids();
        }

        if (streamTask != null) {
            streamTask.cancel(false);
            streamTask = null;
        }

        if (secretRefreshTask != null) {
            secretRefreshTask.cancel(false);
            secretRefreshTask = null;
        }

        if (scheduler != null) {
            scheduler.shutdown();
            scheduler = null;
        }

        isRunning = false;
        System.out.println("[MCMapper] Streaming STOPPED");
    }

    private static void killStreamingDueToSafeZone(int playerX, int playerZ) {
        safeZoneKilled = true;
        
        gridData.clear();
        lastSentCompletion.clear();
        
        ModConfig config = ModConfig.get();
        int radius = config.safeZoneRadius;
        double distance = Math.sqrt(playerX * playerX + playerZ * playerZ);
        
        System.out.println("[MCMapper] SAFE ZONE VIOLATION: Player at (" + playerX + "," + playerZ + ") - Distance: " + (int)distance + " > Limit: " + radius);
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.execute(() -> {
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("§c[MCMapper] Left safe zone! Streaming stopped. Re-enable manually."),
                    false
                );
            });
        }
        
        stop();
        
        config.streamingEnabled = false;
        ModConfig.save();
    }

    private static void killStreamingDueToGridSkip(int fromGridX, int fromGridZ, int toGridX, int toGridZ) {
        gridSkipKilled = true;
        
        gridData.clear();
        lastSentCompletion.clear();
        
        System.out.println("[MCMapper] GRID SKIP DETECTED: (" + fromGridX + "," + fromGridZ + ") -> (" + toGridX + "," + toGridZ + ") - Upload killed!");
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.execute(() -> {
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("§c[MCMapper] Grid skip detected! Upload killed for safety."),
                    false
                );
            });
        }
        
        stop();
        
        ModConfig config = ModConfig.get();
        config.streamingEnabled = false;
        ModConfig.save();
    }

    private static boolean checkGridSkip(int newGridX, int newGridZ) {
        ModConfig config = ModConfig.get();
        if (!config.gridSkipDetectionEnabled) {
            return false;
        }
        
        if (lastUploadedGridX == Integer.MIN_VALUE) {
            return false;
        }
        
        int distanceX = Math.abs(newGridX - lastUploadedGridX);
        int distanceZ = Math.abs(newGridZ - lastUploadedGridZ);
        int totalDistance = distanceX + distanceZ;
        
        if (totalDistance > GRID_SKIP_THRESHOLD) {
            killStreamingDueToGridSkip(lastUploadedGridX, lastUploadedGridZ, newGridX, newGridZ);
            return true;
        }
        
        return false;
    }

    private static void streamPendingGrids() {
        try {
            if (gridSkipKilled || safeZoneKilled) {
                return;
            }
            
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.world == null) {
                return;
            }

            String dimension = client.world.getRegistryKey().getValue().toString();
            inOverworld = dimension.equals("minecraft:overworld");
            if (!inOverworld) {
                return;
            }

            ModConfig config = ModConfig.get();
            if (!config.streamingEnabled || config.authToken == null) {
                return;
            }

            int serverId = getActiveServerId();
            if (serverId <= 0) {
                return;
            }

            if (streamingSecret == null) {
                fetchStreamingCredentials();
            }

            int playerX = (int) Math.floor(client.player.getX());
            int playerZ = (int) Math.floor(client.player.getZ());
            
            inSafeZone = isInSafeZoneCheck(playerX, playerZ);

            if (config.safeZoneEnabled && config.safeZoneRadius > 0 && !inSafeZone) {
                killStreamingDueToSafeZone(playerX, playerZ);
                return;
            }

            if (!verifyServerAddress(config)) {
                return;
            }

            int currentGridX = Math.floorDiv(playerX + 64, 128);
            int currentGridZ = Math.floorDiv(playerZ + 64, 128);

            if (lastPlayerGridX != Integer.MIN_VALUE &&
                (currentGridX != lastPlayerGridX || currentGridZ != lastPlayerGridZ)) {
                String oldGridKey = lastPlayerGridX + "," + lastPlayerGridZ;
                streamGrid(oldGridKey, lastPlayerGridX, lastPlayerGridZ);
            }

            lastPlayerGridX = currentGridX;
            lastPlayerGridZ = currentGridZ;

            for (Map.Entry<String, int[]> entry : gridData.entrySet()) {
                if (gridSkipKilled || safeZoneKilled) return;
                
                String gridKey = entry.getKey();
                String[] parts = gridKey.split(",");
                int gridX = Integer.parseInt(parts[0]);
                int gridZ = Integer.parseInt(parts[1]);

                streamGrid(gridKey, gridX, gridZ);
            }

        } catch (Exception e) {
            System.out.println("[MCMapper] Stream error: " + e.getMessage());
        }
    }

    private static void streamGrid(String gridKey, int gridX, int gridZ) {
        try {
            if (gridSkipKilled || safeZoneKilled) {
                return;
            }
            
            int[] colors = gridData.get(gridKey);
            if (colors == null) {
                return;
            }

            int exploredCount = 0;
            for (int color : colors) {
                if (color != 0) {
                    exploredCount++;
                }
            }

            if (exploredCount == 0) {
                return;
            }

            int completionPercent = (exploredCount * 100) / 16384;

            Integer lastSent = lastSentCompletion.get(gridKey);
            if (lastSent != null && completionPercent <= lastSent) {
                return;
            }

            if (completionPercent < 1 && (lastSent == null || lastSent == 0)) {
                return;
            }

            ModConfig config = ModConfig.get();
            MinecraftClient client = MinecraftClient.getInstance();
            int serverId = getActiveServerId();

            String playerName = client.player.getName().getString();
            String playerUuid = client.player.getUuidAsString();
            String dimension = client.world.getRegistryKey().getValue().toString();
            String currentServerAddress = B2TMapperMod.getCurrentServerAddress();

            int worldX = gridX * 128 - 64;
            int worldZ = gridZ * 128 - 64;

            JsonObject body = new JsonObject();
            body.addProperty("server_id", serverId);
            body.addProperty("grid_x", gridX);
            body.addProperty("grid_z", gridZ);
            body.addProperty("world_x", worldX);
            body.addProperty("world_z", worldZ);
            body.addProperty("player_name", playerName);
            body.addProperty("player_uuid", playerUuid);
            body.addProperty("dimension", dimension);
            body.addProperty("server_address", currentServerAddress != null ? currentServerAddress : "");
            body.addProperty("streaming_secret", streamingSecret != null ? streamingSecret : "");
            body.addProperty("delay_minutes", config.streamDelayMinutes);

            JsonArray colorsArray = new JsonArray();
            for (int color : colors) {
                colorsArray.add(color);
            }
            body.add("colors", colorsArray);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/maps/stream"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.authToken)
                .header("X-Mod-Version", B2TMapperMod.MOD_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                lastSentCompletion.put(gridKey, completionPercent);
                
                lastUploadedGridX = gridX;
                lastUploadedGridZ = gridZ;

                if (completionPercent >= 5 && (lastSent == null || completionPercent >= lastSent + 5)) {
                    final int finalPercent = completionPercent;
                    final int finalGridX = gridX;
                    final int finalGridZ = gridZ;
                    final int delayMins = config.streamDelayMinutes;
                    client.execute(() -> {
                        if (client.player != null) {
                            String delayText = delayMins > 0 ? " (+" + delayMins + "m delay)" : "";
                            client.player.sendMessage(
                                net.minecraft.text.Text.literal("§a⬆ Streamed (" + finalGridX + ", " + finalGridZ + ") - " + finalPercent + "%" + delayText),
                                true
                            );
                        }
                    });
                }
            } else if (response.statusCode() == 403) {
                String responseBody = response.body();
                if (responseBody.contains("SECRET_INVALID") || responseBody.contains("expired")) {
                    if (fetchStreamingCredentials()) {
                        streamGrid(gridKey, gridX, gridZ);
                    } else {
                        notifyError("Streaming credentials expired.");
                        stop();
                    }
                } else {
                    notifyError("Streaming rejected: " + responseBody);
                }
            } else {
                System.out.println("[MCMapper] Upload failed: HTTP " + response.statusCode() + " - " + response.body());
            }

        } catch (Exception e) {
            System.out.println("[MCMapper] Upload error: " + e.getMessage());
        }
    }

    private static boolean isInSafeZoneCheck(int x, int z) {
        ModConfig config = ModConfig.get();
        if (!config.safeZoneEnabled || config.safeZoneRadius <= 0) {
            return true;
        }
        double distance = Math.sqrt(x * x + z * z);
        return distance <= config.safeZoneRadius;
    }

    private static boolean serverAddressMatches(String current, String expected) {
        if (current == null || expected == null) return false;
        
        String normalizedCurrent = current.toLowerCase()
            .replace("https://", "")
            .replace("http://", "")
            .split(":")[0];
            
        String normalizedExpected = expected.toLowerCase()
            .replace("https://", "")
            .replace("http://", "")
            .split(":")[0];
        
        return normalizedCurrent.equals(normalizedExpected) ||
               normalizedCurrent.endsWith("." + normalizedExpected) ||
               normalizedExpected.endsWith("." + normalizedCurrent) ||
               normalizedCurrent.contains(normalizedExpected) ||
               normalizedExpected.contains(normalizedCurrent);
    }

    private static boolean verifyServerAddress(ModConfig config) {
        String currentServer = B2TMapperMod.getCurrentServerAddress();
        if (currentServer == null) {
            return false;
        }

        if (detectedServerAddress != null && !detectedServerAddress.isEmpty()) {
            String expected = detectedServerAddress.toLowerCase()
                .replace("https://", "")
                .replace("http://", "");
            String actual = currentServer.toLowerCase();
            if (actual.contains(expected) || expected.contains(actual)) {
                return true;
            }
        }

        if (config.streamingServerAddress == null || config.streamingServerAddress.isEmpty()) {
            return true;
        }

        String configuredServer = config.streamingServerAddress.toLowerCase()
            .replace("https://", "")
            .replace("http://", "");
        currentServer = currentServer.toLowerCase();

        return currentServer.contains(configuredServer) || configuredServer.contains(currentServer);
    }

    private static void notifyError(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("§c[MCMapper] " + message),
                    false
                );
            }
        });
    }

    public static void initialize() {
        System.out.println("[MCMapper] Initializing MapStreamingService (with 25s connection delay)");
        registerEvents();
    }

    public static void shutdown() {
        stop();
    }

    public static boolean isRunning() {
        return isRunning;
    }

    public static boolean isInSafeZone() {
        return inSafeZone;
    }

    public static boolean isInOverworld() {
        return inOverworld;
    }

    public static int getDetectedServerId() {
        return detectedServerId;
    }

    public static String getDetectedServerName() {
        return detectedServerName;
    }

    public static String getDetectedServerAddress() {
        return detectedServerAddress;
    }

    public static boolean isGridSkipKilled() {
        return gridSkipKilled;
    }

    public static boolean isSafeZoneKilled() {
        return safeZoneKilled;
    }

    public static void clearData() {
        gridData.clear();
        lastSentCompletion.clear();
        serverVerifiedChunks.clear();
        lastPlayerGridX = Integer.MIN_VALUE;
        lastPlayerGridZ = Integer.MIN_VALUE;
        lastUploadedGridX = Integer.MIN_VALUE;
        lastUploadedGridZ = Integer.MIN_VALUE;
        streamingSecret = null;
        secretFetchedAt = 0;
        lastConnectedServer = null;
        detectedServerId = -1;
        detectedServerName = null;
        detectedServerAddress = null;
        lastStartAttempt = 0;
        gridSkipKilled = false;
        safeZoneKilled = false;
    }
}
