package com.b2tmapper.client;

import com.b2tmapper.B2TMapperMod;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.Items;

public class CoordinateHud {
    
    public static void register() {
        HudRenderCallback.EVENT.register(CoordinateHud::renderHud);
    }
    
    private static void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        if (B2TMapperMod.isUiHidden()) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        
        if (client.player == null || client.world == null) {
            return;
        }
        
        if (client.player.getMainHandStack().getItem() == Items.FILLED_MAP) {
            return;
        }
        
        int playerX = (int) client.player.getX();
        int playerZ = (int) client.player.getZ();
        double exactPlayerX = client.player.getX();
        double exactPlayerZ = client.player.getZ();
        
        int gridX = Math.floorDiv(playerX, 128);
        int gridZ = Math.floorDiv(playerZ, 128);
        
        int centerX = gridX * 128;
        int centerZ = gridZ * 128;
        
        double distX = Math.abs(exactPlayerX - centerX);
        double distZ = Math.abs(exactPlayerZ - centerZ);
        double distance = Math.sqrt(distX * distX + distZ * distZ);
        
        boolean atGridCenter = (distX <= 0.5 && distZ <= 0.5);
        
        int y = 10;
        int lineHeight = 10;
        
        String gridText = String.format("Grid: (%d, %d)", gridX, gridZ);
        context.drawText(client.textRenderer, gridText, 10, y, 0x00FF00, true);
        y += lineHeight;
        
        String centerText = String.format("Center: (%d, %d)", centerX, centerZ);
        context.drawText(client.textRenderer, centerText, 10, y, 0xFFFFFF, true);
        y += lineHeight;
        
        String distText = String.format("Distance: %.1f blocks", distance);
        int distColor = atGridCenter ? 0x00FF00 : 0xFF0000;
        context.drawText(client.textRenderer, distText, 10, y, distColor, true);
        y += lineHeight;
        
        if (atGridCenter) {
            String readyText = "[OK] AT GRID CENTER - Create map here!";
            context.drawText(client.textRenderer, readyText, 10, y, 0x00FF00, true);
        } else {
            String farText = "[X] NOT AT CENTER - Move to create map";
            context.drawText(client.textRenderer, farText, 10, y, 0xFF0000, true);
        }
    }
}
