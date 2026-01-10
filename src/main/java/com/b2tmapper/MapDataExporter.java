package com.b2tmapper;

import net.minecraft.item.ItemStack;
import net.minecraft.item.map.MapState;
import net.minecraft.item.FilledMapItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class MapDataExporter {

    private static String cachedDesktopPath = null;

    private static String getDesktopPath() {
        if (cachedDesktopPath != null) {
            return cachedDesktopPath;
        }

        String userHome = System.getProperty("user.home");

        try {
            ProcessBuilder pb = new ProcessBuilder(
                "reg", "query",
                "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Explorer\\User Shell Folders",
                "/v", "Desktop"
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Desktop") && line.contains("REG_")) {
                    String[] parts = line.trim().split("\\s{2,}");
                    if (parts.length >= 3) {
                        String regPath = parts[2].replace("%USERPROFILE%", userHome);
                        File regDesktop = new File(regPath);
                        if (regDesktop.exists() && regDesktop.isDirectory()) {
                            cachedDesktopPath = regDesktop.getAbsolutePath();
                            return cachedDesktopPath;
                        }
                    }
                }
            }
            process.waitFor();
        } catch (Exception e) {
        }

        File oneDriveDesktop = new File(userHome, "OneDrive/Desktop");
        if (oneDriveDesktop.exists() && oneDriveDesktop.isDirectory()) {
            cachedDesktopPath = oneDriveDesktop.getAbsolutePath();
            return cachedDesktopPath;
        }

        cachedDesktopPath = userHome + File.separator + "Desktop";
        return cachedDesktopPath;
    }

    public static boolean exportMapData(ItemStack mapStack, int mapGridX, int mapGridZ,
                                        int playerGridX, int playerGridZ, String playerName,
                                        int originalSlot, int currentSlot, boolean createdAtCenter,
                                        int mapId) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) {
                return false;
            }
            MapState mapState = FilledMapItem.getMapState(mapStack, client.world);
            if (mapState == null) {
                return false;
            }

            double playerX = client.player.getX();
            double playerZ = client.player.getZ();
            double playerY = client.player.getY();

            byte[] colors = mapState.colors;
            if (colors == null || colors.length != 16384) {
                return false;
            }
            List<Integer> colorList = new ArrayList<>();
            for (byte color : colors) {
                colorList.add((int) color & 0xFF);
            }

            String serverAddress = "singleplayer";
            String serverName = "Singleplayer";

            ServerInfo serverInfo = client.getCurrentServerEntry();
            if (serverInfo != null) {
                serverAddress = serverInfo.address;
                serverName = serverInfo.name;
            }

            String worldName = client.world.getRegistryKey().getValue().toString();
            String dimension = client.world.getRegistryKey().getValue().toString();
            String playerUuid = client.player.getUuidAsString();
            String minecraftVersion = client.getGameVersion();

            MapData data = new MapData();
            data.modVersion = B2TMapperMod.MOD_VERSION;
            data.mapId = mapId;
            data.mapGridX = mapGridX;
            data.mapGridZ = mapGridZ;
            data.playerGridX = playerGridX;
            data.playerGridZ = playerGridZ;
            data.gridX = mapGridX;
            data.gridZ = mapGridZ;
            data.worldX = mapGridX * 128;
            data.worldZ = mapGridZ * 128;
            data.playerX = playerX;
            data.playerY = playerY;
            data.playerZ = playerZ;
            data.playerName = playerName;
            data.playerUuid = playerUuid;
            data.serverAddress = serverAddress;
            data.serverName = serverName;
            data.worldName = worldName;
            data.dimension = dimension;
            data.mapScale = mapState.scale;
            data.colors = colorList;
            data.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            data.minecraftVersion = minecraftVersion;

            data.verificationHash = generateHash(data);

            String desktopPath = getDesktopPath();
            File exportDir = new File(desktopPath, "MCMapper-Maps");

            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = String.format("map_%d_%d_%s.json", mapGridX, mapGridZ, timestamp);
            File exportFile = new File(exportDir, filename);

            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (FileWriter writer = new FileWriter(exportFile)) {
                gson.toJson(data, writer);
            }
            if (client.player != null) {
                client.player.sendMessage(
                        net.minecraft.text.Text.literal("§a✓ Exported: §fMCMapper-Maps\\" + filename),
                        false
                );
            }

            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private static String formatDouble(double value) {
        String str = String.valueOf(value);
        if (str.endsWith(".0")) {
            return str.substring(0, str.length() - 2);
        }
        return str;
    }

    private static String generateHash(MapData data) {
        return com.b2tmapper.security.HashGenerator.generateMapHash(
            data.gridX, data.gridZ, data.worldX, data.worldZ,
            data.playerX, data.playerY, data.playerZ,
            data.playerName, data.playerUuid, data.timestamp,
            data.dimension, data.mapScale, data.serverAddress,
            data.serverName, data.modVersion, data.colors
        );
    }

    private static class MapData {
        String modVersion;
        int mapId;
        int mapGridX;
        int mapGridZ;
        int playerGridX;
        int playerGridZ;
        int gridX;
        int gridZ;
        int worldX;
        int worldZ;
        double playerX;
        double playerY;
        double playerZ;
        String playerName;
        String playerUuid;
        String serverAddress;
        String serverName;
        String worldName;
        String dimension;
        int mapScale;
        List<Integer> colors;
        String timestamp;
        String minecraftVersion;
        String verificationHash;
    }
}
