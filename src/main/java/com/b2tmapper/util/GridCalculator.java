package com.b2tmapper.util;

public class GridCalculator {
    public static final int GRID_SIZE = 128;
    
    public static int worldToGrid(double worldCoord) {
        return (int) Math.floor(worldCoord / GRID_SIZE);
    }
    
    public static int worldToGrid(int worldCoord) {
        return (int) Math.floor((double) worldCoord / GRID_SIZE);
    }
    
    public static int gridToWorld(int gridCoord) {
        return gridCoord * GRID_SIZE;
    }
    
    public static boolean isInGrid(double worldX, double worldZ, int gridX, int gridZ) {
        int calcGridX = worldToGrid(worldX);
        int calcGridZ = worldToGrid(worldZ);
        return calcGridX == gridX && calcGridZ == gridZ;
    }
}
