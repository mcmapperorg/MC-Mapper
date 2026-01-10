package com.b2tmapper.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class GridBeacon {
    
    private static final Identifier BEACON_LOGO = Identifier.of("b2tmapper", "textures/beacon_logo.png");
    private static final int LOGO_HEIGHT = 150; // Below clouds
    private static final int BEDROCK_Y = -64;
    private static final int BUILD_HEIGHT = 1024; // Way past clouds! // Top of world
    
    private static boolean enabled = false;
    
    public static void toggle() {
        enabled = !enabled;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            String status = enabled ? "\u00A7a\u00A7l Grid Beacon ENABLED" : "\u00A7c\u00A7l Grid Beacon DISABLED";
            client.player.sendMessage(Text.literal(status), false);
        }
    }
    
    public static boolean isEnabled() {
        return enabled;
    }
    
    public static void render(MatrixStack matrices, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        
        double playerX = client.player.getX();
        double playerZ = client.player.getZ();
        
        int gridX = Math.floorDiv((int)playerX + 64, 128);
        int gridZ = Math.floorDiv((int)playerZ + 64, 128);
        
        int centerX = gridX * 128;
        int centerZ = gridZ * 128;
        
        renderLaser(matrices, centerX, centerZ, tickDelta);
        
        renderLogo(matrices, centerX, LOGO_HEIGHT, centerZ, tickDelta);
    }
    
    private static void renderLaser(MatrixStack matrices, int x, int z, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        double camX = client.gameRenderer.getCamera().getPos().x;
        double camY = client.gameRenderer.getCamera().getPos().y;
        double camZ = client.gameRenderer.getCamera().getPos().z;
        
        matrices.push();
        matrices.translate(x - camX, 0, z - camZ);
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        
        float width = 0.15625f; // Half of 5 pixels
        int r = 0, g = 255, b = 0, a = 180; // Green with transparency
        
        float y1 = BEDROCK_Y - (float)camY;
        float y2 = BUILD_HEIGHT - (float)camY;
        
        buffer.vertex(matrix, -width, y1, -width).color(r, g, b, a);
        buffer.vertex(matrix, -width, y2, -width).color(r, g, b, a);
        buffer.vertex(matrix, width, y2, -width).color(r, g, b, a);
        buffer.vertex(matrix, width, y1, -width).color(r, g, b, a);
        
        buffer.vertex(matrix, -width, y1, width).color(r, g, b, a);
        buffer.vertex(matrix, width, y1, width).color(r, g, b, a);
        buffer.vertex(matrix, width, y2, width).color(r, g, b, a);
        buffer.vertex(matrix, -width, y2, width).color(r, g, b, a);
        
        buffer.vertex(matrix, -width, y1, -width).color(r, g, b, a);
        buffer.vertex(matrix, -width, y1, width).color(r, g, b, a);
        buffer.vertex(matrix, -width, y2, width).color(r, g, b, a);
        buffer.vertex(matrix, -width, y2, -width).color(r, g, b, a);
        
        buffer.vertex(matrix, width, y1, -width).color(r, g, b, a);
        buffer.vertex(matrix, width, y2, -width).color(r, g, b, a);
        buffer.vertex(matrix, width, y2, width).color(r, g, b, a);
        buffer.vertex(matrix, width, y1, width).color(r, g, b, a);
        
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        
        matrices.pop();
    }
    
    private static void renderLogo(MatrixStack matrices, int x, int y, int z, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        double camX = client.gameRenderer.getCamera().getPos().x;
        double camY = client.gameRenderer.getCamera().getPos().y;
        double camZ = client.gameRenderer.getCamera().getPos().z;
        
        matrices.push();
        matrices.translate(x - camX, y - camY, z - camZ);
        
        float yaw = client.gameRenderer.getCamera().getYaw();
        matrices.multiply(new Quaternionf().rotationY((float)Math.toRadians(-yaw + 180)));
        
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, BEACON_LOGO);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
        
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        Matrix4f matrix = matrices.peek().getPositionMatrix();
        
        float size = 4.0f;
        float halfSize = size / 2;
        
        buffer.vertex(matrix, -halfSize, -halfSize, 0).texture(0, 1);
        buffer.vertex(matrix, -halfSize, halfSize, 0).texture(0, 0);
        buffer.vertex(matrix, halfSize, halfSize, 0).texture(1, 0);
        buffer.vertex(matrix, halfSize, -halfSize, 0).texture(1, 1);
        
        BufferRenderer.drawWithGlobalProgram(buffer.end());
        
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        
        matrices.pop();
    }
}
