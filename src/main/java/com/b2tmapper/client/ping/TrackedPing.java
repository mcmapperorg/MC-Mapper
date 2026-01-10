package com.b2tmapper.client.ping;

public class TrackedPing {
    public int id;
    public String name;
    public String pingType;
    public int worldX;
    public int worldY;  // Y level, default 64
    public int worldZ;
    public String username;
    public int serverId;
    
    public TrackedPing() {}
    
    public TrackedPing(int id, String name, String pingType, int worldX, int worldZ, String username, int serverId) {
        this.id = id;
        this.name = name;
        this.pingType = pingType;
        this.worldX = worldX;
        this.worldY = 64; // Default Y
        this.worldZ = worldZ;
        this.username = username;
        this.serverId = serverId;
    }
    
    public int getColor() {
        switch (pingType.toLowerCase()) {
            case "info":     return 0x0096FF; // Blue
            case "danger":   return 0xFF0000; // Red
            case "landmark": return 0xFFC800; // Yellow/Gold
            case "personal": return 0x00FF00; // Green
            case "private":  return 0x9B59B6; // Purple
            default:         return 0xFFFFFF; // White
        }
    }
    
    public int getColorWithAlpha() {
        return 0xFF000000 | getColor();
    }
    
    public float[] getColorRGB() {
        int color = getColor();
        return new float[] {
            ((color >> 16) & 0xFF) / 255f,
            ((color >> 8) & 0xFF) / 255f,
            (color & 0xFF) / 255f
        };
    }
    
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof TrackedPing) {
            return this.id == ((TrackedPing) obj).id;
        }
        return false;
    }
    
    @Override
    public int hashCode() {
        return id;
    }
}
