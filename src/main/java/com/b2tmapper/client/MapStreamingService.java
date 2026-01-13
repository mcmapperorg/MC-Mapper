package com.b2tmapper.client;

import com.b2tmapper.B2TMapperMod;
import com.b2tmapper.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MapStreamingService {

    private static final String API_BASE = "https://mc-mapper-production.up.railway.app/api";
    private static final int STREAM_INTERVAL_MS = 10000;
    private static final int SECRET_REFRESH_MS = 4 * 60 * 60 * 1000;

    private static ScheduledExecutorService scheduler;
    private static ScheduledFuture<?> streamTask;
    private static ScheduledFuture<?> secretRefreshTask;
    private static boolean isRunning = false;
    private static boolean inSafeZone = false;
    private static boolean inOverworld = true;
    private static boolean eventsRegistered = false;

    private static String streamingSecret = null;
    private static long secretFetchedAt = 0;

    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    private static final ConcurrentHashMap<String, int[]> gridData = new ConcurrentHashMap<>();
    private static final Map<String, Integer> lastSentCompletion = new HashMap<>();

    private static int lastPlayerGridX = Integer.MIN_VALUE;
    private static int lastPlayerGridZ = Integer.MIN_VALUE;

    public static void registerEvents() {
        if (eventsRegistered) return;

        ClientChunkEvents.CHUNK_LOAD.register((world, chunk) -> {
            if (!isRunning) return;
            
            ModConfig config = ModConfig.get();
            if (!config.streamingEnabled) return;

            processChunkImmediate(world, chunk, config);
        });

        eventsRegistered = true;
    }

    private static void processChunkImmediate(World world, WorldChunk chunk, ModConfig config) {
        try {
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
                if (isInSafeZone(centerX, centerZ)) {
                    return;
                }
            }

            for (int dx = 0; dx < 16; dx++) {
                for (int dz = 0; dz < 16; dz++) {
                    int x = baseX + dx;
                    int z = baseZ + dz;

                    if (config.safeZoneEnabled && config.safeZoneRadius > 0) {
                        if (isInSafeZone(x, z)) {
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

            if (mapColor.id == 12) {
                int waterDepth = 0;
                BlockPos checkPos = pos;
                while (checkPos.getY() > world.getBottomY() && waterDepth < 20) {
                    BlockState checkState = chunk.getBlockState(checkPos);
                    MapColor checkColor = checkState.getMapColor(world, checkPos);
                    if (checkColor == null || checkColor.id != 12) break;
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

    public static boolean fetchStreamingCredentials() {
        try {
            ModConfig config = ModConfig.get();
            if (config.authToken == null || config.authToken.isEmpty()) {
                return false;
            }
            if (config.streamingServerId <= 0) {
                return false;
            }

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/servers/" + config.streamingServerId + "/streaming-credentials"))
                .header("Authorization", "Bearer " + config.authToken)
                .GET()
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                if (json.get("success").getAsBoolean()) {
                    streamingSecret = json.get("streaming_secret").getAsString();
                    secretFetchedAt = System.currentTimeMillis();
                    return true;
                }
            }
        } catch (Exception e) {
        }
        return false;
    }

    private static boolean secretNeedsRefresh() {
        if (streamingSecret == null || secretFetchedAt == 0) return true;
        return System.currentTimeMillis() - secretFetchedAt > SECRET_REFRESH_MS;
    }

    public static void start() {
        if (isRunning) {
            return;
        }

        ModConfig config = ModConfig.get();
        if (config.authToken == null || config.authToken.isEmpty()) {
            return;
        }

        if (config.streamingServerId <= 0) {
            return;
        }

        if (!fetchStreamingCredentials()) {
            return;
        }

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
    }

    private static void refreshSecretIfNeeded() {
        if (secretNeedsRefresh()) {
            fetchStreamingCredentials();
        }
    }

    public static void stop() {
        if (!isRunning) {
            return;
        }

        streamPendingGrids();

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

    private static boolean isInSafeZone(int x, int z) {
        ModConfig config = ModConfig.get();
        if (!config.safeZoneEnabled || config.safeZoneRadius <= 0) {
            return false;
        }
        double distance = Math.sqrt(x * x + z * z);
        return distance > config.safeZoneRadius;
    }

    private static void streamPendingGrids() {
        try {
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
            if (!config.streamingEnabled || config.authToken == null || config.streamingServerId <= 0) {
                return;
            }

            if (streamingSecret == null) {
                if (!fetchStreamingCredentials()) {
                    return;
                }
            }

            int playerX = (int) Math.floor(client.player.getX());
            int playerZ = (int) Math.floor(client.player.getZ());
            inSafeZone = isInSafeZone(playerX, playerZ);

            if (inSafeZone) {
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
                String gridKey = entry.getKey();
                String[] parts = gridKey.split(",");
                int gridX = Integer.parseInt(parts[0]);
                int gridZ = Integer.parseInt(parts[1]);

                streamGrid(gridKey, gridX, gridZ);
            }

        } catch (Exception e) {
        }
    }

    private static void streamGrid(String gridKey, int gridX, int gridZ) {
        try {
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

            String playerName = client.player.getName().getString();
            String playerUuid = client.player.getUuidAsString();
            String dimension = client.world.getRegistryKey().getValue().toString();

            String currentServerAddress = B2TMapperMod.getCurrentServerAddress();

            int worldX = gridX * 128 - 64;
            int worldZ = gridZ * 128 - 64;

            JsonObject body = new JsonObject();
            body.addProperty("server_id", config.streamingServerId);
            body.addProperty("grid_x", gridX);
            body.addProperty("grid_z", gridZ);
            body.addProperty("world_x", worldX);
            body.addProperty("world_z", worldZ);
            body.addProperty("player_name", playerName);
            body.addProperty("player_uuid", playerUuid);
            body.addProperty("dimension", dimension);
            body.addProperty("server_address", currentServerAddress != null ? currentServerAddress : "");
            body.addProperty("streaming_secret", streamingSecret != null ? streamingSecret : "");

            JsonArray colorsArray = new JsonArray();
            for (int color : colors) {
                colorsArray.add(color);
            }
            body.add("colors", colorsArray);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/maps/stream"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + config.authToken)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                lastSentCompletion.put(gridKey, completionPercent);

                if (completionPercent >= 5 && (lastSent == null || completionPercent >= lastSent + 5)) {
                    final int finalPercent = completionPercent;
                    final int finalGridX = gridX;
                    final int finalGridZ = gridZ;
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.sendMessage(
                                net.minecraft.text.Text.literal("§a⬆ Streamed (" + finalGridX + ", " + finalGridZ + ") - " + finalPercent + "%"),
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
                        notifyError("Streaming credentials expired. Reconnect to server.");
                        stop();
                    }
                } else {
                    notifyError("Streaming rejected: Wrong server or no permission.");
                    stop();
                }
            }

        } catch (Exception e) {
        }
    }

    private static void notifyError(String message) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player != null) {
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("§c" + message),
                    false
                );
            }
        });
    }

    private static boolean verifyServerAddress(ModConfig config) {
        String currentServer = B2TMapperMod.getCurrentServerAddress();
        if (currentServer == null) {
            return false;
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

    public static void initialize() {
        registerEvents();
        ModConfig config = ModConfig.get();
        if (config.streamingEnabled && config.authToken != null && config.streamingServerId > 0) {
            start();
        }
    }

    public static void shutdown() {
        stop();
    }

    public static void clearData() {
        gridData.clear();
        lastSentCompletion.clear();
        lastPlayerGridX = Integer.MIN_VALUE;
        lastPlayerGridZ = Integer.MIN_VALUE;
        streamingSecret = null;
        secretFetchedAt = 0;
    }
}
