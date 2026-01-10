package com.b2tmapper.client;

import com.b2tmapper.B2TMapperMod;
import com.b2tmapper.config.ModConfig;
import com.b2tmapper.config.ModConfig.MapMode;
import com.b2tmapper.config.ModConfig.MapPosition;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class GridFillMap {

    private static final String API_BASE = "https://mc-mapper-production.up.railway.app/api";
    
    private static final int MAP_SIZE = 160;
    private static final int GRID_SIZE = 50;
    private static final int MARGIN = 10;
    
    private static final Set<String> existingGrids = ConcurrentHashMap.newKeySet();
    
    private static final ConcurrentHashMap<String, int[]> colorCache = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> loadingColors = new ConcurrentHashMap<>();
    
    private static int loadedServerId = -1;
    private static boolean loadingServerData = false;
    private static boolean serverDataLoaded = false;
    
    private static int lastGridX = Integer.MIN_VALUE;
    private static int lastGridZ = Integer.MIN_VALUE;
    
    private static final HttpClient httpClient = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    public static void register() {
        HudRenderCallback.EVENT.register(GridFillMap::renderGridFillMap);
    }

    private static void renderGridFillMap(DrawContext context, RenderTickCounter tickCounter) {
        if (B2TMapperMod.isUiHidden()) return;

        ModConfig config = ModConfig.get();
        
        if (!config.showMap) return;
        
        if (config.mapMode != MapMode.GRID_FILL) return;
        
        if (config.gridFillServerId <= 0) {
            renderNoServerMessage(context, config);
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        int serverId = config.gridFillServerId;
        if (serverId != loadedServerId && !loadingServerData) {
            loadServerGrids(serverId);
        }

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        
        int mapX, mapY;
        MapPosition pos = config.mapPosition;
        
        switch (pos) {
            case TOP_LEFT:
                mapX = MARGIN;
                mapY = MARGIN + 12;
                break;
            case TOP_RIGHT:
                mapX = screenWidth - MAP_SIZE - MARGIN;
                mapY = MARGIN + 12;
                break;
            case BOTTOM_LEFT:
                mapX = MARGIN;
                mapY = screenHeight - MAP_SIZE - MARGIN;
                break;
            case BOTTOM_RIGHT:
            default:
                mapX = screenWidth - MAP_SIZE - MARGIN;
                mapY = screenHeight - MAP_SIZE - MARGIN;
                break;
        }

        int playerX = (int) client.player.getX();
        int playerZ = (int) client.player.getZ();
        int currentGridX = Math.floorDiv(playerX, 128);
        int currentGridZ = Math.floorDiv(playerZ, 128);
        float yaw = client.player.getYaw();

        if (currentGridX != lastGridX || currentGridZ != lastGridZ) {
            lastGridX = currentGridX;
            lastGridZ = currentGridZ;
            
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int gx = currentGridX + dx;
                    int gz = currentGridZ + dz;
                    String key = gx + "," + gz;
                    if (existingGrids.contains(key) && !colorCache.containsKey(key) && !loadingColors.containsKey(key)) {
                        loadGridColors(gx, gz);
                    }
                }
            }
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int gridX = currentGridX + dx;
                int gridZ = currentGridZ + dz;

                int screenX = mapX + (dx + 1) * GRID_SIZE;
                int screenY = mapY + (dz + 1) * GRID_SIZE;

                boolean isCurrent = (dx == 0 && dz == 0);
                String key = gridX + "," + gridZ;
                
                boolean gridExists = existingGrids.contains(key);
                int[] colors = colorCache.get(key);
                boolean isLoadingColors = loadingColors.containsKey(key);
                
                if (gridExists && colors != null) {
                    drawMapColors(context, screenX, screenY, GRID_SIZE, colors);
                } else if (gridExists && isLoadingColors) {
                    context.fill(screenX, screenY, screenX + GRID_SIZE, screenY + GRID_SIZE, 0x6000AA00);
                    int dotCount = (int)((System.currentTimeMillis() / 300) % 4);
                    String dots = ".".repeat(dotCount);
                    context.drawCenteredTextWithShadow(client.textRenderer, dots, 
                        screenX + GRID_SIZE / 2, screenY + GRID_SIZE / 2 - 4, 0xFFFFFFFF);
                } else if (gridExists) {
                    context.fill(screenX, screenY, screenX + GRID_SIZE, screenY + GRID_SIZE, 0x6000AA00);
                } else if (loadingServerData) {
                    context.fill(screenX, screenY, screenX + GRID_SIZE, screenY + GRID_SIZE, 0x40404040);
                } else {
                    context.fill(screenX, screenY, screenX + GRID_SIZE, screenY + GRID_SIZE, 0x40200020);
                }

                int borderColor;
                if (isCurrent) {
                    borderColor = 0xFF00FF00; // Green for current
                } else if (gridExists) {
                    borderColor = 0xFF00AA00; // Darker green for mapped
                } else {
                    borderColor = 0xFF404040; // Gray for unmapped
                }
                drawBorder(context, screenX, screenY, GRID_SIZE, borderColor);

                String gridText = gridX + "," + gridZ;
                int textColor = gridExists ? 0xFFFFFFFF : 0xFF808080;
                context.drawText(client.textRenderer, gridText, screenX + 2, screenY + 2, textColor, true);

                if (isCurrent) {
                    drawDirectionArrow(context, screenX, screenY, GRID_SIZE, yaw);
                }
            }
        }

        String title = "Grid Fill: " + truncate(config.gridFillServerName, 15);
        int titleWidth = client.textRenderer.getWidth(title);
        int titleX, titleY;
        
        titleY = mapY - 12;
        if (pos == MapPosition.TOP_LEFT || pos == MapPosition.BOTTOM_LEFT) {
            titleX = mapX;
        } else {
            titleX = mapX + MAP_SIZE - titleWidth;
        }
        
        int titleColor = loadingServerData ? 0xFFFFAA00 : 0xFF44FF44;
        context.drawText(client.textRenderer, title, titleX, titleY, titleColor, true);
    }
    
    private static void renderNoServerMessage(DrawContext context, ModConfig config) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        
        int mapX, mapY;
        MapPosition pos = config.mapPosition;
        
        switch (pos) {
            case TOP_LEFT:
                mapX = MARGIN;
                mapY = MARGIN + 12;
                break;
            case TOP_RIGHT:
                mapX = screenWidth - MAP_SIZE - MARGIN;
                mapY = MARGIN + 12;
                break;
            case BOTTOM_LEFT:
                mapX = MARGIN;
                mapY = screenHeight - MAP_SIZE - MARGIN;
                break;
            case BOTTOM_RIGHT:
            default:
                mapX = screenWidth - MAP_SIZE - MARGIN;
                mapY = screenHeight - MAP_SIZE - MARGIN;
                break;
        }
        
        context.fill(mapX, mapY, mapX + MAP_SIZE, mapY + MAP_SIZE, 0x80000000);
        drawBorder(context, mapX, mapY, MAP_SIZE, 0xFFFF8800);
        
        context.drawCenteredTextWithShadow(client.textRenderer, "No Server", 
            mapX + MAP_SIZE / 2, mapY + MAP_SIZE / 2 - 12, 0xFFFF8800);
        context.drawCenteredTextWithShadow(client.textRenderer, "Selected", 
            mapX + MAP_SIZE / 2, mapY + MAP_SIZE / 2, 0xFFFF8800);
        context.drawCenteredTextWithShadow(client.textRenderer, "(Open Map Settings)", 
            mapX + MAP_SIZE / 2, mapY + MAP_SIZE / 2 + 14, 0xFF888888);
        
        context.drawText(client.textRenderer, "Grid Fill", mapX, mapY - 12, 0xFFFF8800, true);
    }

    private static void loadServerGrids(int serverId) {
        loadingServerData = true;
        serverDataLoaded = false;
        existingGrids.clear();
        colorCache.clear();
        loadingColors.clear();
        
        CompletableFuture.runAsync(() -> {
            try {
                String url = API_BASE + "/grids?server_id=" + serverId;
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                    if (json.has("success") && json.get("success").getAsBoolean()) {
                        JsonArray grids = json.getAsJsonArray("grids");
                        for (int i = 0; i < grids.size(); i++) {
                            JsonObject grid = grids.get(i).getAsJsonObject();
                            int gx = grid.get("gridX").getAsInt();
                            int gz = grid.get("gridZ").getAsInt();
                            existingGrids.add(gx + "," + gz);
                        }
                        loadedServerId = serverId;
                        serverDataLoaded = true;
                    }
                } else {
                }
            } catch (Exception e) {
            } finally {
                loadingServerData = false;
            }
        });
    }
    
    private static void loadGridColors(int gridX, int gridZ) {
        String key = gridX + "," + gridZ;
        loadingColors.put(key, true);
        
        CompletableFuture.runAsync(() -> {
            try {
                String url = API_BASE + "/grid/" + gridX + "/" + gridZ + "/image";
                
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                    if (json.has("success") && json.get("success").getAsBoolean()) {
                        if (json.has("colors") && !json.get("colors").isJsonNull()) {
                            JsonArray colorsArray = json.getAsJsonArray("colors");
                            int[] colors = new int[colorsArray.size()];
                            for (int i = 0; i < colorsArray.size(); i++) {
                                colors[i] = colorsArray.get(i).getAsInt();
                            }
                            colorCache.put(key, colors);
                        }
                    }
                }
            } catch (Exception e) {
            } finally {
                loadingColors.remove(key);
            }
        });
    }
    
    private static void drawMapColors(DrawContext context, int x, int y, int size, int[] colors) {
        if (colors.length != 16384) {
            context.fill(x, y, x + size, y + size, 0x6000AA00);
            return;
        }
        
        for (int px = 0; px < size; px++) {
            for (int py = 0; py < size; py++) {
                int srcX = (px * 128) / size;
                int srcY = (py * 128) / size;
                int idx = srcY * 128 + srcX;
                
                if (idx < colors.length) {
                    int colorIdx = colors[idx] & 0xFF;
                    int color = getMapColor(colorIdx);
                    if ((color & 0xFF000000) != 0) {
                        context.fill(x + px, y + py, x + px + 1, y + py + 1, color);
                    }
                }
            }
        }
    }
    
    private static int getMapColor(int index) {
        int[][] baseColors = {
            {0, 0, 0},       // 0: NONE (transparent)
            {127, 178, 56},  // 1: GRASS
            {247, 233, 163}, // 2: SAND
            {199, 199, 199}, // 3: WOOL
            {255, 0, 0},     // 4: FIRE
            {160, 160, 255}, // 5: ICE
            {167, 167, 167}, // 6: METAL
            {0, 124, 0},     // 7: PLANT
            {255, 255, 255}, // 8: SNOW
            {164, 168, 184}, // 9: CLAY
            {151, 109, 77},  // 10: DIRT
            {112, 112, 112}, // 11: STONE
            {64, 64, 255},   // 12: WATER
            {143, 119, 72},  // 13: WOOD
            {255, 252, 245}, // 14: QUARTZ
            {216, 127, 51},  // 15: COLOR_ORANGE
            {178, 76, 216},  // 16: COLOR_MAGENTA
            {102, 153, 216}, // 17: COLOR_LIGHT_BLUE
            {229, 229, 51},  // 18: COLOR_YELLOW
            {127, 204, 25},  // 19: COLOR_LIGHT_GREEN
            {242, 127, 165}, // 20: COLOR_PINK
            {76, 76, 76},    // 21: COLOR_GRAY
            {153, 153, 153}, // 22: COLOR_LIGHT_GRAY
            {76, 127, 153},  // 23: COLOR_CYAN
            {127, 63, 178},  // 24: COLOR_PURPLE
            {51, 76, 178},   // 25: COLOR_BLUE
            {102, 76, 51},   // 26: COLOR_BROWN
            {102, 127, 51},  // 27: COLOR_GREEN
            {153, 51, 51},   // 28: COLOR_RED
            {25, 25, 25},    // 29: COLOR_BLACK
            {250, 238, 77},  // 30: GOLD
            {92, 219, 213},  // 31: DIAMOND
            {74, 128, 255},  // 32: LAPIS
            {0, 217, 58},    // 33: EMERALD
            {129, 86, 49},   // 34: PODZOL
            {112, 2, 0},     // 35: NETHER
            {209, 177, 161}, // 36: TERRACOTTA_WHITE
            {159, 82, 36},   // 37: TERRACOTTA_ORANGE
            {149, 87, 108},  // 38: TERRACOTTA_MAGENTA
            {112, 108, 138}, // 39: TERRACOTTA_LIGHT_BLUE
            {186, 133, 36},  // 40: TERRACOTTA_YELLOW
            {103, 117, 53},  // 41: TERRACOTTA_LIGHT_GREEN
            {160, 77, 78},   // 42: TERRACOTTA_PINK
            {57, 41, 35},    // 43: TERRACOTTA_GRAY
            {135, 107, 98},  // 44: TERRACOTTA_LIGHT_GRAY
            {87, 92, 92},    // 45: TERRACOTTA_CYAN
            {122, 73, 88},   // 46: TERRACOTTA_PURPLE
            {76, 62, 92},    // 47: TERRACOTTA_BLUE
            {76, 50, 35},    // 48: TERRACOTTA_BROWN
            {76, 82, 42},    // 49: TERRACOTTA_GREEN
            {142, 60, 46},   // 50: TERRACOTTA_RED
            {37, 22, 16},    // 51: TERRACOTTA_BLACK
            {189, 48, 49},   // 52: CRIMSON_NYLIUM
            {148, 63, 97},   // 53: CRIMSON_STEM
            {92, 25, 29},    // 54: CRIMSON_HYPHAE
            {22, 126, 134},  // 55: WARPED_NYLIUM
            {58, 142, 140},  // 56: WARPED_STEM
            {86, 44, 62},    // 57: WARPED_HYPHAE
            {20, 180, 133},  // 58: WARPED_WART_BLOCK
            {100, 100, 100}, // 59: DEEPSLATE
            {216, 175, 147}, // 60: RAW_IRON
            {127, 167, 150}, // 61: GLOW_LICHEN
        };
        
        if (index == 0) return 0x00000000; // Transparent
        
        int baseIndex = (index / 4);
        int shade = index % 4;
        
        if (baseIndex >= baseColors.length) {
            return 0xFF808080; // Default gray
        }
        
        int[] rgb = baseColors[baseIndex];
        
        int multiplier;
        switch (shade) {
            case 0: multiplier = 180; break; // Darkest
            case 1: multiplier = 220; break;
            case 2: multiplier = 255; break; // Normal
            case 3: multiplier = 135; break; // Lightest (underwater)
            default: multiplier = 255;
        }
        
        int r = (rgb[0] * multiplier) / 255;
        int g = (rgb[1] * multiplier) / 255;
        int b = (rgb[2] * multiplier) / 255;
        
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void drawBorder(DrawContext context, int x, int y, int size, int color) {
        context.fill(x, y, x + size, y + 1, color);
        context.fill(x, y + size - 1, x + size, y + size, color);
        context.fill(x, y, x + 1, y + size, color);
        context.fill(x + size - 1, y, x + size, y + size, color);
    }

    private static void drawDirectionArrow(DrawContext context, int x, int y, int size, float yaw) {
        yaw = MathHelper.wrapDegrees(yaw);

        int centerX = x + size / 2;
        int centerY = y + size / 2;

        double angle = Math.toRadians(-yaw);

        int arrowLength = 14;
        int arrowEndX = centerX + (int)(Math.sin(angle) * arrowLength);
        int arrowEndY = centerY + (int)(Math.cos(angle) * arrowLength);

        drawLine(context, centerX, centerY, arrowEndX, arrowEndY, 0xFFFFFF00);

        context.fill(arrowEndX - 2, arrowEndY - 2, arrowEndX + 2, arrowEndY + 2, 0xFFFFFF00);
    }

    private static void drawLine(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        int x = x1;
        int y = y1;

        for (int i = 0; i < 40; i++) {
            context.fill(x, y, x + 1, y + 1, color);

            if (x == x2 && y == y2) break;

            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }
    
    private static String truncate(String s, int max) {
        if (s == null) return "Unknown";
        return s.length() <= max ? s : s.substring(0, max - 2) + "..";
    }
    
    public static void clearCache() {
        existingGrids.clear();
        colorCache.clear();
        loadingColors.clear();
        loadedServerId = -1;
        serverDataLoaded = false;
        lastGridX = Integer.MIN_VALUE;
        lastGridZ = Integer.MIN_VALUE;
    }
}
