package com.b2tmapper.client;

import com.b2tmapper.B2TMapperMod;
import com.b2tmapper.config.ModConfig;
import com.b2tmapper.config.ModConfig.MapMode;
import com.b2tmapper.config.ModConfig.MapPosition;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.MathHelper;

public class MiniGridMap {

    private static final int MAP_SIZE = 160;  // Total size of mini map (2x bigger)
    private static final int GRID_SIZE = 50;  // Size of each grid cell (2x bigger)
    private static final int MARGIN = 10;     // Margin from screen edges

    public static void register() {
        HudRenderCallback.EVENT.register(MiniGridMap::renderMiniMap);
    }

    private static void renderMiniMap(DrawContext context, RenderTickCounter tickCounter) {
        if (B2TMapperMod.isUiHidden()) {
            return;
        }

        ModConfig config = ModConfig.get();
        
        if (!config.showMap) {
            return;
        }

        if (config.mapMode != MapMode.GRID) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();

        if (client.player == null || client.world == null) {
            return;
        }

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        
        int mapX, mapY;
        MapPosition pos = config.mapPosition;
        
        switch (pos) {
            case TOP_LEFT:
                mapX = MARGIN;
                mapY = MARGIN + 12; // Extra space for title
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

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int gridX = currentGridX + dx;
                int gridZ = currentGridZ + dz;

                int screenX = mapX + (dx + 1) * GRID_SIZE;
                int screenY = mapY + (dz + 1) * GRID_SIZE;

                boolean isCurrent = (dx == 0 && dz == 0);
                int borderColor = isCurrent ? 0xFF00FF00 : 0xFF808080; // Green for current, gray for others
                int fillColor = isCurrent ? 0x8000FF00 : 0x40404040;   // Semi-transparent

                context.fill(screenX, screenY, screenX + GRID_SIZE, screenY + GRID_SIZE, fillColor);

                drawBorder(context, screenX, screenY, GRID_SIZE, borderColor);

                String gridText = gridX + "," + gridZ;
                int textX = screenX + 4; // More padding for bigger cells
                int textY = screenY + 4;

                context.drawText(client.textRenderer, gridText, textX, textY, 0xFFFFFFFF, false);

                if (isCurrent) {
                    drawDirectionArrow(context, screenX, screenY, GRID_SIZE, yaw);
                }
            }
        }

        String title = "Grid Map";
        int titleWidth = client.textRenderer.getWidth(title);
        int titleX, titleY;
        
        if (pos == MapPosition.BOTTOM_LEFT || pos == MapPosition.BOTTOM_RIGHT) {
            titleY = mapY - 12;
        } else {
            titleY = mapY - 12;
        }
        
        if (pos == MapPosition.TOP_LEFT || pos == MapPosition.BOTTOM_LEFT) {
            titleX = mapX;
        } else {
            titleX = mapX + MAP_SIZE - titleWidth;
        }
        
        context.drawText(client.textRenderer, title, titleX, titleY, 0xFFFFFFFF, true);
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

        double angle = Math.toRadians(-yaw); // Removed the +180 to flip the arrow

        int arrowLength = 16; // Increased for bigger map
        int arrowEndX = centerX + (int)(Math.sin(angle) * arrowLength);
        int arrowEndY = centerY + (int)(Math.cos(angle) * arrowLength);

        drawArrowTriangle(context, centerX, centerY, arrowEndX, arrowEndY);
    }

    private static void drawArrowTriangle(DrawContext context, int startX, int startY, int endX, int endY) {

        drawLine(context, startX, startY, endX, endY, 0xFFFFFF00);

        context.fill(endX - 2, endY - 2, endX + 2, endY + 2, 0xFFFFFF00);
    }

    private static void drawLine(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx - dy;

        int x = x1;
        int y = y1;

        for (int i = 0; i < 40; i++) { // Increased limit for 2x bigger map
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
}
