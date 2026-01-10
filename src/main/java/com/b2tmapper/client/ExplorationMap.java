package com.b2tmapper.client;

import com.b2tmapper.B2TMapperMod;
import com.b2tmapper.config.ModConfig;
import com.b2tmapper.config.ModConfig.MapMode;
import com.b2tmapper.config.ModConfig.MapPosition;
import com.b2tmapper.config.ModConfig.MapShape;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.registry.entry.RegistryEntry;

import java.util.HashMap;
import java.util.Map;

public class ExplorationMap {

    private static final int MARGIN = 10;
    private static final int BORDER_WIDTH = 3;
    private static final int BLOCKS_RADIUS = 64;
    
    private static final int NOT_CACHED = -1;
    private static final int UNEXPLORED_COLOR = 0xFF1a1a2e;
    
    private static final Map<Long, int[]> chunkColorCache = new HashMap<>();
    private static final Map<Long, int[]> chunkHeightCache = new HashMap<>();
    
    private static int lastChunkX = Integer.MIN_VALUE;
    private static int lastChunkZ = Integer.MIN_VALUE;
    private static int lastDimension = 0;

    private static final Map<Block, Integer> BLOCK_COLORS = new HashMap<>();
    
    static {
        BLOCK_COLORS.put(Blocks.GRASS_BLOCK, 0xFF8CBD57);
        BLOCK_COLORS.put(Blocks.DIRT, 0xFF8B5A2B);
        BLOCK_COLORS.put(Blocks.COARSE_DIRT, 0xFF77553C);
        BLOCK_COLORS.put(Blocks.PODZOL, 0xFF6A4725);
        BLOCK_COLORS.put(Blocks.MYCELIUM, 0xFF6F6265);
        BLOCK_COLORS.put(Blocks.DIRT_PATH, 0xFF947449);
        BLOCK_COLORS.put(Blocks.FARMLAND, 0xFF4E3321);
        BLOCK_COLORS.put(Blocks.MUD, 0xFF3D3837);
        
        BLOCK_COLORS.put(Blocks.STONE, 0xFF7F7F7F);
        BLOCK_COLORS.put(Blocks.GRANITE, 0xFF956755);
        BLOCK_COLORS.put(Blocks.DIORITE, 0xFFBCBCBC);
        BLOCK_COLORS.put(Blocks.ANDESITE, 0xFF848484);
        BLOCK_COLORS.put(Blocks.DEEPSLATE, 0xFF4E4E52);
        BLOCK_COLORS.put(Blocks.COBBLESTONE, 0xFF7A7A7A);
        BLOCK_COLORS.put(Blocks.GRAVEL, 0xFF837F7E);
        BLOCK_COLORS.put(Blocks.BEDROCK, 0xFF545454);
        
        BLOCK_COLORS.put(Blocks.SAND, 0xFFDBCFA3);
        BLOCK_COLORS.put(Blocks.RED_SAND, 0xFFA55322);
        BLOCK_COLORS.put(Blocks.SANDSTONE, 0xFFD8CA8E);
        BLOCK_COLORS.put(Blocks.SOUL_SAND, 0xFF513C32);
        
        BLOCK_COLORS.put(Blocks.SNOW, 0xFFFAFAFA);
        BLOCK_COLORS.put(Blocks.SNOW_BLOCK, 0xFFFAFAFA);
        BLOCK_COLORS.put(Blocks.ICE, 0xFF91B5FC);
        BLOCK_COLORS.put(Blocks.PACKED_ICE, 0xFF8AAACF);
        BLOCK_COLORS.put(Blocks.BLUE_ICE, 0xFF74A4D6);
        
        BLOCK_COLORS.put(Blocks.CLAY, 0xFF9EA4B0);
        BLOCK_COLORS.put(Blocks.TERRACOTTA, 0xFF985E43);
        
        BLOCK_COLORS.put(Blocks.OAK_LOG, 0xFF9A7D4E);
        BLOCK_COLORS.put(Blocks.SPRUCE_LOG, 0xFF3D2813);
        BLOCK_COLORS.put(Blocks.BIRCH_LOG, 0xFFD5CDB4);
        BLOCK_COLORS.put(Blocks.JUNGLE_LOG, 0xFF55410D);
        BLOCK_COLORS.put(Blocks.ACACIA_LOG, 0xFF6B5036);
        BLOCK_COLORS.put(Blocks.DARK_OAK_LOG, 0xFF3E2912);
        BLOCK_COLORS.put(Blocks.OAK_PLANKS, 0xFFA88554);
        BLOCK_COLORS.put(Blocks.SPRUCE_PLANKS, 0xFF73553A);
        BLOCK_COLORS.put(Blocks.BIRCH_PLANKS, 0xFFC5B77D);
        
        BLOCK_COLORS.put(Blocks.OAK_LEAVES, 0xFF4A7A32);
        BLOCK_COLORS.put(Blocks.SPRUCE_LEAVES, 0xFF3D5C31);
        BLOCK_COLORS.put(Blocks.BIRCH_LEAVES, 0xFF5E8A42);
        BLOCK_COLORS.put(Blocks.JUNGLE_LEAVES, 0xFF3D8C23);
        BLOCK_COLORS.put(Blocks.ACACIA_LEAVES, 0xFF5D8A38);
        BLOCK_COLORS.put(Blocks.DARK_OAK_LEAVES, 0xFF3D6A28);
        BLOCK_COLORS.put(Blocks.CHERRY_LEAVES, 0xFFE8AFD0);
        
        BLOCK_COLORS.put(Blocks.NETHERRACK, 0xFF723232);
        BLOCK_COLORS.put(Blocks.NETHER_BRICKS, 0xFF2C1418);
        BLOCK_COLORS.put(Blocks.BASALT, 0xFF4A4B4F);
        BLOCK_COLORS.put(Blocks.BLACKSTONE, 0xFF2A2328);
        BLOCK_COLORS.put(Blocks.CRIMSON_NYLIUM, 0xFF852222);
        BLOCK_COLORS.put(Blocks.WARPED_NYLIUM, 0xFF2B7265);
        BLOCK_COLORS.put(Blocks.GLOWSTONE, 0xFFAB8654);
        BLOCK_COLORS.put(Blocks.MAGMA_BLOCK, 0xFF8E3A0E);
        BLOCK_COLORS.put(Blocks.OBSIDIAN, 0xFF0F0A18);
        BLOCK_COLORS.put(Blocks.CRYING_OBSIDIAN, 0xFF200A30);
        
        BLOCK_COLORS.put(Blocks.END_STONE, 0xFFDBDBA5);
        BLOCK_COLORS.put(Blocks.PURPUR_BLOCK, 0xFFA97FAA);
        
        BLOCK_COLORS.put(Blocks.COAL_ORE, 0xFF636363);
        BLOCK_COLORS.put(Blocks.IRON_ORE, 0xFF887E79);
        BLOCK_COLORS.put(Blocks.GOLD_ORE, 0xFF8A8152);
        BLOCK_COLORS.put(Blocks.DIAMOND_ORE, 0xFF7F9C9C);
        BLOCK_COLORS.put(Blocks.ANCIENT_DEBRIS, 0xFF5D4639);
        
        BLOCK_COLORS.put(Blocks.SHORT_GRASS, 0xFF8CBD57);
        BLOCK_COLORS.put(Blocks.TALL_GRASS, 0xFF8CBD57);
        BLOCK_COLORS.put(Blocks.FERN, 0xFF6A8C34);
        BLOCK_COLORS.put(Blocks.VINE, 0xFF3B6A1E);
        BLOCK_COLORS.put(Blocks.CACTUS, 0xFF5B7D3A);
        
        BLOCK_COLORS.put(Blocks.WATER, 0xFF3F76E4);
        BLOCK_COLORS.put(Blocks.LAVA, 0xFFCF5B13);
        
        BLOCK_COLORS.put(Blocks.MOSS_BLOCK, 0xFF5A7337);
        BLOCK_COLORS.put(Blocks.SCULK, 0xFF0D1D26);
    }

    public static void register() {
        HudRenderCallback.EVENT.register(ExplorationMap::renderExplorationMap);
    }

    private static void renderExplorationMap(DrawContext context, RenderTickCounter tickCounter) {
        if (B2TMapperMod.isUiHidden()) return;
        
        ModConfig config = ModConfig.get();
        if (!config.showMap) return;
        if (config.mapMode != MapMode.EXPLORATION) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;

        int currentDimension = client.world.getDimension().hashCode();
        if (currentDimension != lastDimension) {
            clearCache();
            lastDimension = currentDimension;
        }

        int mapSize = config.mapSize;
        boolean isCircle = config.mapShape == MapShape.CIRCLE;

        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        
        int mapX, mapY;
        MapPosition pos = config.mapPosition;
        
        switch (pos) {
            case TOP_LEFT: mapX = MARGIN; mapY = MARGIN; break;
            case TOP_RIGHT: mapX = screenWidth - mapSize - MARGIN - BORDER_WIDTH * 2; mapY = MARGIN; break;
            case BOTTOM_LEFT: mapX = MARGIN; mapY = screenHeight - mapSize - MARGIN - BORDER_WIDTH * 2; break;
            default: mapX = screenWidth - mapSize - MARGIN - BORDER_WIDTH * 2; mapY = screenHeight - mapSize - MARGIN - BORDER_WIDTH * 2; break;
        }

        int playerX = (int) client.player.getX();
        int playerZ = (int) client.player.getZ();
        int playerChunkX = playerX >> 4;
        int playerChunkZ = playerZ >> 4;
        
        if (playerChunkX != lastChunkX || playerChunkZ != lastChunkZ) {
            scanNearbyTerrain(client.world, playerX, playerZ);
            lastChunkX = playerChunkX;
            lastChunkZ = playerChunkZ;
        }

        if (isCircle) {
            drawCircleBorderThick(context, mapX, mapY, mapSize + BORDER_WIDTH * 2, BORDER_WIDTH, 0xFF000000);
        } else {
            context.fill(mapX, mapY, mapX + mapSize + BORDER_WIDTH * 2, mapY + mapSize + BORDER_WIDTH * 2, 0xFF000000);
        }

        int innerX = mapX + BORDER_WIDTH;
        int innerY = mapY + BORDER_WIDTH;

        if (isCircle) {
            drawCircleBackground(context, innerX, innerY, mapSize, UNEXPLORED_COLOR);
        } else {
            context.fill(innerX, innerY, innerX + mapSize, innerY + mapSize, UNEXPLORED_COLOR);
        }

        float scale = (float) BLOCKS_RADIUS * 2 / mapSize;
        int centerMapX = innerX + mapSize / 2;
        int centerMapY = innerY + mapSize / 2;
        int halfSize = mapSize / 2;

        for (int px = 0; px < mapSize; px++) {
            for (int py = 0; py < mapSize; py++) {
                if (isCircle) {
                    int dx = px - halfSize;
                    int dy = py - halfSize;
                    if (dx * dx + dy * dy > halfSize * halfSize) continue;
                }

                int worldX = playerX + (int)((px - halfSize) * scale);
                int worldZ = playerZ + (int)((py - halfSize) * scale);

                int color = getCachedColor(worldX, worldZ);
                
                if (color != NOT_CACHED && color != UNEXPLORED_COLOR) {
                    int currentH = getCachedHeight(worldX, worldZ);
                    int northH = getCachedHeight(worldX, worldZ - 1);
                    int westH = getCachedHeight(worldX - 1, worldZ);
                    
                    int slopeShade = (currentH - northH) * 8 + (currentH - westH) * 4;
                    slopeShade = MathHelper.clamp(slopeShade, -30, 30);
                    
                    color = adjustBrightness(color, slopeShade);
                    context.fill(innerX + px, innerY + py, innerX + px + 1, innerY + py + 1, color);
                }
            }
        }

        drawCompass(context, client.textRenderer, innerX, innerY, mapSize);
        drawPlayerArrow(context, centerMapX, centerMapY, client.player.getYaw());

        if (isCircle) {
            drawCircleBorder(context, innerX, innerY, mapSize, 0xFF33AA33);
        } else {
            drawBorder(context, innerX, innerY, mapSize, mapSize, 0xFF33AA33);
        }
    }

    private static int adjustBrightness(int color, int amount) {
        int r = MathHelper.clamp(((color >> 16) & 0xFF) + amount, 0, 255);
        int g = MathHelper.clamp(((color >> 8) & 0xFF) + amount, 0, 255);
        int b = MathHelper.clamp((color & 0xFF) + amount, 0, 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void scanNearbyTerrain(World world, int playerX, int playerZ) {
        int scanRadius = BLOCKS_RADIUS + 16;
        for (int x = playerX - scanRadius; x <= playerX + scanRadius; x++) {
            for (int z = playerZ - scanRadius; z <= playerZ + scanRadius; z++) {
                if (!hasColorCached(x, z)) {
                    int[] data = scanBlockData(world, x, z);
                    cacheData(x, z, data[0], data[1]);
                }
            }
        }
    }

    private static int[] scanBlockData(World world, int x, int z) {
        int chunkX = x >> 4;
        int chunkZ = z >> 4;
        
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return new int[]{UNEXPLORED_COLOR, 64};
        }

        BlockPos.Mutable pos = new BlockPos.Mutable(x, 0, z);
        int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z);
        
        for (int y = topY; y >= world.getBottomY(); y--) {
            pos.setY(y);
            BlockState state = world.getBlockState(pos);
            
            if (state.isAir()) continue;

            Block block = state.getBlock();
            int color;
            
            if (block == Blocks.WATER) {
                int depth = getWaterDepth(world, x, y, z);
                color = getWaterColor(depth, world, pos);
            } else if (BLOCK_COLORS.containsKey(block)) {
                color = BLOCK_COLORS.get(block);
                color = applyBiomeTint(world, pos, block, color);
            } else {
                color = getMapColorFor(state, world, pos);
            }
            
            int heightShade = (y - 64) / 4;
            heightShade = MathHelper.clamp(heightShade, -20, 25);
            color = adjustBrightness(color, heightShade);
            
            return new int[]{color, y};
        }
        
        return new int[]{0xFF545454, 0};
    }

    private static int applyBiomeTint(World world, BlockPos pos, Block block, int baseColor) {
        try {
            RegistryEntry<Biome> biomeEntry = world.getBiome(pos);
            Biome biome = biomeEntry.value();
            
            if (block == Blocks.GRASS_BLOCK || block == Blocks.SHORT_GRASS || 
                block == Blocks.TALL_GRASS || block == Blocks.FERN) {
                int grassColor = biome.getGrassColorAt(pos.getX(), pos.getZ());
                return blendColors(baseColor, grassColor, 0.8f);
            }
            
            if (block != Blocks.CHERRY_LEAVES && 
                (block == Blocks.OAK_LEAVES || block == Blocks.BIRCH_LEAVES || 
                 block == Blocks.SPRUCE_LEAVES || block == Blocks.JUNGLE_LEAVES ||
                 block == Blocks.ACACIA_LEAVES || block == Blocks.DARK_OAK_LEAVES)) {
                int foliageColor = biome.getFoliageColor();
                return blendColors(baseColor, foliageColor, 0.7f);
            }
        } catch (Exception e) {}
        
        return baseColor;
    }

    private static int blendColors(int color1, int color2, float ratio) {
        int r1 = (color1 >> 16) & 0xFF, g1 = (color1 >> 8) & 0xFF, b1 = color1 & 0xFF;
        int r2 = (color2 >> 16) & 0xFF, g2 = (color2 >> 8) & 0xFF, b2 = color2 & 0xFF;
        int r = (int)(r1 * (1 - ratio) + r2 * ratio);
        int g = (int)(g1 * (1 - ratio) + g2 * ratio);
        int b = (int)(b1 * (1 - ratio) + b2 * ratio);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int getMapColorFor(BlockState state, World world, BlockPos pos) {
        try {
            int mapColorId = state.getMapColor(world, pos).color;
            if (mapColorId != 0) return 0xFF000000 | mapColorId;
        } catch (Exception e) {}
        return 0xFF808080;
    }

    private static int getWaterDepth(World world, int x, int startY, int z) {
        int depth = 0;
        BlockPos.Mutable pos = new BlockPos.Mutable(x, startY, z);
        for (int y = startY; y >= world.getBottomY() && depth < 30; y--) {
            pos.setY(y);
            if (world.getBlockState(pos).getBlock() != Blocks.WATER) break;
            depth++;
        }
        return depth;
    }

    private static int getWaterColor(int depth, World world, BlockPos pos) {
        int biomeWaterColor = 0x3F76E4;
        try {
            RegistryEntry<Biome> biomeEntry = world.getBiome(pos);
            biomeWaterColor = biomeEntry.value().getWaterColor();
        } catch (Exception e) {}
        
        int shade = -depth * 3;
        return adjustBrightness(0xFF000000 | biomeWaterColor, shade);
    }

    private static void cacheData(int x, int z, int color, int height) {
        long chunkKey = ((long)((x >> 4) + 2000000) << 32) | ((z >> 4) + 2000000);
        int idx = (z & 15) * 16 + (x & 15);
        
        int[] colors = chunkColorCache.computeIfAbsent(chunkKey, k -> {
            int[] arr = new int[256];
            java.util.Arrays.fill(arr, NOT_CACHED);
            return arr;
        });
        colors[idx] = color;
        
        int[] heights = chunkHeightCache.computeIfAbsent(chunkKey, k -> new int[256]);
        heights[idx] = height;
    }

    private static boolean hasColorCached(int x, int z) {
        long chunkKey = ((long)((x >> 4) + 2000000) << 32) | ((z >> 4) + 2000000);
        int[] colors = chunkColorCache.get(chunkKey);
        if (colors == null) return false;
        return colors[(z & 15) * 16 + (x & 15)] != NOT_CACHED;
    }

    private static int getCachedColor(int x, int z) {
        long chunkKey = ((long)((x >> 4) + 2000000) << 32) | ((z >> 4) + 2000000);
        int[] colors = chunkColorCache.get(chunkKey);
        if (colors == null) return NOT_CACHED;
        return colors[(z & 15) * 16 + (x & 15)];
    }

    private static int getCachedHeight(int x, int z) {
        long chunkKey = ((long)((x >> 4) + 2000000) << 32) | ((z >> 4) + 2000000);
        int[] heights = chunkHeightCache.get(chunkKey);
        if (heights == null) return 64;
        int height = heights[(z & 15) * 16 + (x & 15)];
        return height != 0 ? height : 64;
    }

    private static void drawPlayerArrow(DrawContext context, int cx, int cy, float yaw) {
        double angle = Math.toRadians(yaw + 180);
        int size = 6;
        
        int tipX = cx + (int)(Math.sin(angle) * size);
        int tipY = cy - (int)(Math.cos(angle) * size);
        int leftX = cx + (int)(Math.sin(angle + 2.5) * size * 0.7);
        int leftY = cy - (int)(Math.cos(angle + 2.5) * size * 0.7);
        int rightX = cx + (int)(Math.sin(angle - 2.5) * size * 0.7);
        int rightY = cy - (int)(Math.cos(angle - 2.5) * size * 0.7);
        int backX = cx + (int)(Math.sin(angle + Math.PI) * size * 0.2);
        int backY = cy - (int)(Math.cos(angle + Math.PI) * size * 0.2);
        
        drawThickLine(context, tipX, tipY, leftX, leftY, 0xFF000000);
        drawThickLine(context, tipX, tipY, rightX, rightY, 0xFF000000);
        drawThickLine(context, leftX, leftY, backX, backY, 0xFF000000);
        drawThickLine(context, rightX, rightY, backX, backY, 0xFF000000);
        context.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF000000);
    }

    private static void drawThickLine(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        drawLine(context, x1, y1, x2, y2, color);
        drawLine(context, x1 + 1, y1, x2 + 1, y2, color);
        drawLine(context, x1, y1 + 1, x2, y2 + 1, color);
    }

    private static void drawCompass(DrawContext context, TextRenderer textRenderer, int x, int y, int size) {
        int cx = x + size / 2;
        int compassColor = 0xFFFF4444; // All red
        context.drawText(textRenderer, "N", cx - 3, y + 2, compassColor, true);
        context.drawText(textRenderer, "S", cx - 3, y + size - 10, compassColor, true);
        context.drawText(textRenderer, "E", x + size - 8, y + size / 2 - 4, compassColor, true);
        context.drawText(textRenderer, "W", x + 2, y + size / 2 - 4, compassColor, true);
    }

    private static void drawCircleBackground(DrawContext context, int x, int y, int size, int color) {
        int cx = x + size / 2, cy = y + size / 2, r = size / 2;
        for (int dy = -r; dy <= r; dy++)
            for (int dx = -r; dx <= r; dx++)
                if (dx * dx + dy * dy <= r * r)
                    context.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
    }

    private static void drawCircleBorder(DrawContext context, int x, int y, int size, int color) {
        int cx = x + size / 2, cy = y + size / 2, r = size / 2, px = r, py = 0, err = 0;
        while (px >= py) {
            context.fill(cx + px, cy + py, cx + px + 1, cy + py + 1, color);
            context.fill(cx + py, cy + px, cx + py + 1, cy + px + 1, color);
            context.fill(cx - py, cy + px, cx - py + 1, cy + px + 1, color);
            context.fill(cx - px, cy + py, cx - px + 1, cy + py + 1, color);
            context.fill(cx - px, cy - py, cx - px + 1, cy - py + 1, color);
            context.fill(cx - py, cy - px, cx - py + 1, cy - px + 1, color);
            context.fill(cx + py, cy - px, cx + py + 1, cy - px + 1, color);
            context.fill(cx + px, cy - py, cx + px + 1, cy - py + 1, color);
            if (err <= 0) { py++; err += 2 * py + 1; }
            if (err > 0) { px--; err -= 2 * px + 1; }
        }
    }

    private static void drawCircleBorderThick(DrawContext context, int x, int y, int size, int thickness, int color) {
        int cx = x + size / 2, cy = y + size / 2, outerR = size / 2, innerR = outerR - thickness;
        for (int dy = -outerR; dy <= outerR; dy++)
            for (int dx = -outerR; dx <= outerR; dx++) {
                int distSq = dx * dx + dy * dy;
                if (distSq <= outerR * outerR && distSq >= innerR * innerR)
                    context.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
            }
    }

    private static void drawLine(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1), sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1;
        int err = dx - dy, xc = x1, yc = y1;
        for (int i = 0; i < 25; i++) {
            context.fill(xc, yc, xc + 1, yc + 1, color);
            if (xc == x2 && yc == y2) break;
            int e2 = 2 * err;
            if (e2 > -dy) { err -= dy; xc += sx; }
            if (e2 < dx) { err += dx; yc += sy; }
        }
    }

    private static void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    public static void clearCache() {
        chunkColorCache.clear();
        chunkHeightCache.clear();
        lastChunkX = Integer.MIN_VALUE;
        lastChunkZ = Integer.MIN_VALUE;
    }
}
