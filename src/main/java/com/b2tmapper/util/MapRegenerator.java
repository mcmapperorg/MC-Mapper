package com.b2tmapper.util;

import net.minecraft.block.BlockState;
import net.minecraft.block.MapColor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.map.MapState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

public class MapRegenerator {

    private static final int MAP_SIZE = 128;
    private static final int MIN_RENDER_DISTANCE = 5;

    public static boolean hasMinimumRenderDistance() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return false;
        
        int renderDistance = client.options.getViewDistance().getValue();
        return renderDistance >= MIN_RENDER_DISTANCE;
    }

    public static int getRenderDistance() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options == null) return 0;
        return client.options.getViewDistance().getValue();
    }

    public static boolean regenerateColors(MapState mapState, int gridX, int gridZ) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (client.world == null || client.player == null) {
            return false;
        }

        if (!hasMinimumRenderDistance()) {
            return false;
        }

        if (mapState.scale != 0) {
            return false;
        }

        World world = client.world;
        
        int centerX = gridX * 128;
        int centerZ = gridZ * 128;
        byte[] newColors = new byte[MAP_SIZE * MAP_SIZE];
        int regeneratedCount = 0;
        int failedCount = 0;
        int cryingObsidianCount = 0;

        int startX = centerX - (MAP_SIZE / 2);
        int startZ = centerZ - (MAP_SIZE / 2);

        for (int pixelZ = 0; pixelZ < MAP_SIZE; pixelZ++) {
            for (int pixelX = 0; pixelX < MAP_SIZE; pixelX++) {
                int worldX = startX + pixelX;
                int worldZ = startZ + pixelZ;

                int chunkX = worldX >> 4;
                int chunkZ = worldZ >> 4;
                
                WorldChunk chunk = world.getChunkManager().getWorldChunk(chunkX, chunkZ);
                
                if (chunk == null) {
                    int index = pixelX + pixelZ * MAP_SIZE;
                    newColors[index] = mapState.colors[index];
                    failedCount++;
                    continue;
                }

                int topY = world.getTopY(Heightmap.Type.WORLD_SURFACE, worldX, worldZ);
                BlockPos pos = new BlockPos(worldX, topY - 1, worldZ);
                BlockState blockState = world.getBlockState(pos);

                if (blockState.getBlock() == net.minecraft.block.Blocks.CRYING_OBSIDIAN) {
                    cryingObsidianCount++;
                }

                MapColor mapColor = blockState.getMapColor(world, pos);

                int shade = calculateShade(world, worldX, worldZ, topY);

                int colorIndex = pixelX + pixelZ * MAP_SIZE;
                
                if (mapColor == MapColor.CLEAR) {
                    newColors[colorIndex] = 0;
                } else {
                    newColors[colorIndex] = (byte) (mapColor.id * 4 + shade);
                }

                regeneratedCount++;
            }
        }

        System.arraycopy(newColors, 0, mapState.colors, 0, newColors.length);
        return failedCount == 0; // Only fully successful if all chunks were loaded
    }

    private static int calculateShade(World world, int x, int z, int currentY) {
        int northY = world.getTopY(Heightmap.Type.WORLD_SURFACE, x, z - 1);

        if (currentY < northY) {
            return 0; // Darker
        } else if (currentY > northY) {
            return 2; // Brighter
        } else {
            return 1; // Normal
        }
    }
}
