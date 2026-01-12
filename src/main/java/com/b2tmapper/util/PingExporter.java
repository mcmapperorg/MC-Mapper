package com.b2tmapper.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.item.ItemStack;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.map.MapState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.b2tmapper.B2TMapperMod;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PingExporter {

    public static boolean suppressHudRendering = false;
    
    private static PingData pendingPingData = null;
    private static int screenshotDelayFrames = 0;
    
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
                        String regPath = parts[2];
                        regPath = regPath.replace("%USERPROFILE%", userHome);
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
        
        File oneDriveDesktop2 = new File(userHome, "OneDrive\\Desktop");
        if (oneDriveDesktop2.exists() && oneDriveDesktop2.isDirectory()) {
            cachedDesktopPath = oneDriveDesktop2.getAbsolutePath();
            return cachedDesktopPath;
        }
        
        cachedDesktopPath = userHome + File.separator + "Desktop";
        return cachedDesktopPath;
    }

    public static class PingData {
        public String pingId;
        public String modVersion;
        
        public int gridX;
        public int gridZ;
        
        public int worldX;
        public int worldZ;
        public double exactX;
        public double exactY;
        public double exactZ;
        
        public String playerName;
        public String playerUuid;
        
        public String serverAddress;
        public String serverName;
        public String dimension;
        
        public boolean hasMapData;
        public int mapGridX;
        public int mapGridZ;
        public List<Integer> mapColors;
        
        public int referenceGridX;
        public int referenceGridZ;
        
        public String timestamp;
        public String screenshotFilename;
        
        public String verificationHash;
    }

    public static boolean exportPing() {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) {
                return false;
            }
            String pingId = UUID.randomUUID().toString().substring(0, 8);
            
            double exactX = client.player.getX();
            double exactY = client.player.getY();
            double exactZ = client.player.getZ();
            
            int gridX = (int) Math.floor(exactX / 128.0);
            int gridZ = (int) Math.floor(exactZ / 128.0);
            
            int worldX = (int) Math.floor(exactX);
            int worldZ = (int) Math.floor(exactZ);
            String serverAddress = "singleplayer";
            String serverName = "Singleplayer";
            ServerInfo serverInfo = client.getCurrentServerEntry();
            if (serverInfo != null) {
                serverAddress = serverInfo.address;
                serverName = serverInfo.name;
            }

            String playerName = client.player.getName().getString();
            String playerUuid = client.player.getUuidAsString();
            String dimension = client.world.getRegistryKey().getValue().toString();

            boolean hasMapData = false;
            int mapGridX = 0;
            int mapGridZ = 0;
            List<Integer> mapColors = new ArrayList<>();
            
            ItemStack heldItem = client.player.getMainHandStack();
            if (heldItem.getItem() instanceof FilledMapItem) {
                MapState mapState = FilledMapItem.getMapState(heldItem, client.world);
                if (mapState != null && mapState.colors != null && mapState.colors.length == 16384) {
                    hasMapData = true;
                    mapGridX = (int) Math.floor(mapState.centerX / 128.0);
                    mapGridZ = (int) Math.floor(mapState.centerZ / 128.0);
                    
                    for (byte color : mapState.colors) {
                        mapColors.add((int) color & 0xFF);
                    }
                    
                }
            }

            PingData data = new PingData();
            data.pingId = pingId;
            data.modVersion = B2TMapperMod.MOD_VERSION;
            data.gridX = gridX;
            data.gridZ = gridZ;
            data.worldX = worldX;
            data.worldZ = worldZ;
            data.exactX = exactX;
            data.exactY = exactY;
            data.exactZ = exactZ;
            data.playerName = playerName;
            data.playerUuid = playerUuid;
            data.serverAddress = serverAddress;
            data.serverName = serverName;
            data.dimension = dimension;
            data.hasMapData = hasMapData;
            data.mapGridX = mapGridX;
            data.mapGridZ = mapGridZ;
            data.mapColors = mapColors;
            data.referenceGridX = gridX;
            data.referenceGridZ = gridZ;
            data.timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            String screenshotFilename = "screenshot_" + pingId + ".png";
            data.screenshotFilename = screenshotFilename;
            
            data.verificationHash = generateHash(data);

            pendingPingData = data;
            screenshotDelayFrames = 3; // Wait 3 frames for clean render
            suppressHudRendering = true; // Tell HUD to hide
            
            
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            suppressHudRendering = false;
            
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null && client.player != null) {
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("\u00a7c\u2717 Ping export failed: " + e.getMessage()),
                    false
                );
            }
            return false;
        }
    }

    public static void tickScreenshot() {
        if (pendingPingData != null && screenshotDelayFrames > 0) {
            screenshotDelayFrames--;
            
            if (screenshotDelayFrames == 0) {
                captureDelayedScreenshot();
            }
        }
    }

    private static void captureDelayedScreenshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        PingData data = pendingPingData;
        
        try {
            BufferedImage screenshot = takeScreenshotNow(client);
            
            suppressHudRendering = false;
            
            if (screenshot == null) {
                if (client.player != null) {
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal("\u00a7c\u2717 Ping failed: Could not capture screenshot"),
                        false
                    );
                }
                pendingPingData = null;
                return;
            }

            String desktopPath = getDesktopPath();
            File exportDir = new File(desktopPath, "MCMapper-Pings");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }

            String fileTimestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String zipFilename = String.format("ping_%d_%d_%s_%s.zip", data.gridX, data.gridZ, data.pingId, fileTimestamp);
            File zipFile = new File(exportDir, zipFilename);

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
                String jsonFilename = "ping_" + data.pingId + ".json";
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String jsonContent = gson.toJson(data);
                
                ZipEntry jsonEntry = new ZipEntry(jsonFilename);
                zos.putNextEntry(jsonEntry);
                zos.write(jsonContent.getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
                
                ZipEntry screenshotEntry = new ZipEntry(data.screenshotFilename);
                zos.putNextEntry(screenshotEntry);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(screenshot, "PNG", baos);
                zos.write(baos.toByteArray());
                zos.closeEntry();
            }
            if (client.player != null) {
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("\u00a7a\u2713 Ping exported: \u00a7fMCMapper-Pings/" + zipFilename),
                    false
                );
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("\u00a77Grid: (" + data.gridX + ", " + data.gridZ + ") | ID: " + data.pingId),
                    false
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
            suppressHudRendering = false;
            
            if (client != null && client.player != null) {
                client.player.sendMessage(
                    net.minecraft.text.Text.literal("\u00a7c\u2717 Ping export failed: " + e.getMessage()),
                    false
                );
            }
        } finally {
            pendingPingData = null;
        }
    }

    private static BufferedImage takeScreenshotNow(MinecraftClient client) {
        try {
            boolean hudWasHidden = client.options.hudHidden;
            client.options.hudHidden = true;
            
            Framebuffer framebuffer = client.getFramebuffer();
            int width = framebuffer.textureWidth;
            int height = framebuffer.textureHeight;
            
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            
            try (net.minecraft.client.texture.NativeImage nativeImage = 
                    ScreenshotRecorder.takeScreenshot(framebuffer)) {
                
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int color = nativeImage.getColor(x, y);
                        int a = (color >> 24) & 0xFF;
                        int b = (color >> 16) & 0xFF;
                        int g = (color >> 8) & 0xFF;
                        int r = color & 0xFF;
                        image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                    }
                }
            }
            
            client.options.hudHidden = hudWasHidden;
            
            return image;
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String formatDouble(double value) {
        String str = String.valueOf(value);
        if (str.endsWith(".0")) {
            return str.substring(0, str.length() - 2);
        }
        return str;
    }

    private static String generateHash(PingData data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            
            StringBuilder hashInput = new StringBuilder();
            hashInput.append(data.pingId).append("|");
            hashInput.append(data.modVersion).append("|");
            hashInput.append(data.gridX).append("|");
            hashInput.append(data.gridZ).append("|");
            hashInput.append(data.worldX).append("|");
            hashInput.append(data.worldZ).append("|");
            hashInput.append(formatDouble(data.exactX)).append("|");
            hashInput.append(formatDouble(data.exactY)).append("|");
            hashInput.append(formatDouble(data.exactZ)).append("|");
            hashInput.append(data.playerName).append("|");
            hashInput.append(data.playerUuid).append("|");
            hashInput.append(data.serverAddress).append("|");
            hashInput.append(data.dimension).append("|");
            hashInput.append(data.timestamp).append("|");
            hashInput.append(data.hasMapData).append("|");
            
            if (data.hasMapData && data.mapColors != null && !data.mapColors.isEmpty()) {
                hashInput.append(data.mapGridX).append("|");
                hashInput.append(data.mapGridZ).append("|");
                for (int i = 0; i < Math.min(100, data.mapColors.size()); i++) {
                    hashInput.append(data.mapColors.get(i));
                    if (i < 99 && i < data.mapColors.size() - 1) {
                        hashInput.append(",");
                    }
                }
            }
            
            String inputString = hashInput.toString();
            
            byte[] hashBytes = digest.digest(inputString.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (Exception e) {
            return "error";
        }
    }
}
