package com.b2tmapper.client;

import com.b2tmapper.B2TMapperMod;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;

public class GridHudOverlay {
    
    public static void register() {
        HudRenderCallback.EVENT.register(GridHudOverlay::renderHud);
    }
    
    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (B2TMapperMod.isUiHidden()) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (client.player == null || client.world == null) {
            return;
        }
        
        B2TMapperMod.MapTrackingData mapData = B2TMapperMod.getMapTrackingData();
        
        if (mapData != null) {
            int y = 10;
            int lineHeight = 10;
            
            if (mapData.isInvalidated) {
                context.drawText(client.textRenderer, "§c§l[MAP INVALIDATED]", 10, y, 0xFF0000, true);
                y += lineHeight;
                context.drawText(client.textRenderer, "§cThis map cannot be exported!", 10, y, 0xFF0000, true);
                y += lineHeight;
                context.drawText(client.textRenderer, "§eCreate a new map at grid center.", 10, y, 0xFFFF00, true);
                return;
            }
            
            if (mapData.alreadyExported) {
                context.drawText(client.textRenderer, "§6§l[ALREADY EXPORTED]", 10, y, 0xFFAA00, true);
                y += lineHeight;
                context.drawText(client.textRenderer, "§6This map was already exported.", 10, y, 0xFFAA00, true);
                y += lineHeight;
                context.drawText(client.textRenderer, "§eEach map can only be exported once.", 10, y, 0xFFFF00, true);
                return;
            }
            
            String mapIdText = String.format("Map #%d", mapData.mapId);
            context.drawText(client.textRenderer, mapIdText, 10, y, 0xAAAAAA, true);
            y += lineHeight;
            
            String mapGridText = String.format("Map Grid: (%d, %d)", 
                mapData.mapGridX, mapData.mapGridZ);
            context.drawText(client.textRenderer, mapGridText, 10, y, 0x00FF00, true);
            y += lineHeight;
            
            String centerText = String.format("Map Center: (%d, %d)", 
                mapData.mapCenterX, mapData.mapCenterZ);
            context.drawText(client.textRenderer, centerText, 10, y, 0xFFFFFF, true);
            y += lineHeight;
            
            String playerGridText = String.format("Player Grid: (%d, %d)", 
                mapData.playerGridX, mapData.playerGridZ);
            
            boolean onSameGrid = (mapData.playerGridX == mapData.mapGridX && 
                                 mapData.playerGridZ == mapData.mapGridZ);
            
            int playerGridColor = onSameGrid ? 0x00FF00 : 0xFFFF00;
            context.drawText(client.textRenderer, playerGridText, 10, y, playerGridColor, true);
            y += lineHeight;
            
            String handText = String.format("Slot: %d", mapData.originalSlot + 1);
            context.drawText(client.textRenderer, handText, 10, y, 0xFFFFFF, true);
            y += lineHeight;
            
            if (mapData.createdAtCenter) {
                String readyText = "§a✓ Ready to export (Right Alt)";
                context.drawText(client.textRenderer, readyText, 10, y, 0x00FF00, true);
                y += lineHeight;
                String warningText = "§7Don't move or change slots!";
                context.drawText(client.textRenderer, warningText, 10, y, 0x777777, true);
            } else {
                String notCenterText = "§c✗ Map not created at grid center!";
                context.drawText(client.textRenderer, notCenterText, 10, y, 0xFF0000, true);
            }
        }
    }
}
