package com.b2tmapper.client.ping;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

public class TrackedPingRenderer {
    
    private static TrackedPingRenderer INSTANCE;
    
    private static final Identifier BEAM_TEXTURE = Identifier.of("textures/entity/beacon_beam.png");
    
    private TrackedPingRenderer() {}
    
    public static TrackedPingRenderer get() {
        if (INSTANCE == null) {
            INSTANCE = new TrackedPingRenderer();
        }
        return INSTANCE;
    }
    
    public void renderWorld(MatrixStack matrices, VertexConsumerProvider vertexConsumers, 
                            Camera camera, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        
        List<TrackedPing> pings = TrackedPingManager.get().getTrackedPings();
        if (pings.isEmpty()) return;
        
        double camX = camera.getPos().x;
        double camY = camera.getPos().y;
        double camZ = camera.getPos().z;
        
        long worldTime = client.world.getTime();
        
        for (TrackedPing ping : pings) {
            renderBeacon(matrices, vertexConsumers, ping, camX, camY, camZ, worldTime, tickDelta);
        }
    }
    
    private void renderBeacon(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                              TrackedPing ping, double camX, double camY, double camZ,
                              long worldTime, float tickDelta) {
        
        double x = ping.worldX + 0.5 - camX;
        double z = ping.worldZ + 0.5 - camZ;
        
        double horizontalDist = Math.sqrt(x * x + z * z);
        
        double renderDist = Math.min(horizontalDist, 64.0);
        
        float beamScale = 1.0f;
        
        if (horizontalDist > 64.0) {
            beamScale = (float) Math.sqrt(horizontalDist / 64.0);
            beamScale = Math.min(beamScale, 4.0f); // Cap the scale
            
            double factor = renderDist / horizontalDist;
            x *= factor;
            z *= factor;
        }
        
        double y = 0 - camY;
        
        float[] color = ping.getColorRGB();
        
        float innerRadius = 0.15f * beamScale;
        float outerRadius = 0.22f * beamScale;
        
        matrices.push();
        matrices.translate(x, y, z);
        
        renderBeam(matrices, vertexConsumers, tickDelta, worldTime, 0, 1024, color, innerRadius, outerRadius);
        
        matrices.pop();
    }
    
    public void renderBeam(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                           float tickDelta, long worldTime, int yOffset, int maxY,
                           float[] color, float innerRadius, float outerRadius) {
        
        int height = maxY - yOffset;
        float time = (float) Math.floorMod(worldTime, 40) + tickDelta;
        float negativeTime = maxY < 0 ? time : -time;
        float fractionalTime = MathHelper.fractionalPart(negativeTime * 0.2F - (float) MathHelper.floor(negativeTime * 0.1F));
        
        float red = color[0];
        float green = color[1];
        float blue = color[2];
        
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 2.25F - 45.0F));
        
        float innerX1 = -innerRadius;
        float innerZ1 = -innerRadius;
        float innerX2 = -innerRadius;
        float innerZ2 = innerRadius;
        float innerX3 = innerRadius;
        float innerZ3 = innerRadius;
        float innerX4 = innerRadius;
        float innerZ4 = -innerRadius;
        
        float u1 = -1.0F + fractionalTime;
        float u2 = (float) height * (0.5F / innerRadius) + u1;
        
        VertexConsumer vertexConsumer = vertexConsumers.getBuffer(RenderLayer.getBeaconBeam(BEAM_TEXTURE, true));
        
        renderBeamLayer(matrices, vertexConsumer, red, green, blue, 0.85F, yOffset, height,
            innerX1, innerZ1, innerX2, innerZ2, innerX3, innerZ3, innerX4, innerZ4, 
            0.0F, 1.0F, u1, u2);
        
        matrices.pop();
        
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(time * 2.25F - 45.0F));
        
        float outerX1 = -outerRadius;
        float outerX4 = outerRadius;
        float v1 = -1.0F + fractionalTime;
        float v2 = (float) height + v1;
        
        VertexConsumer glowConsumer = vertexConsumers.getBuffer(RenderLayer.getBeaconBeam(BEAM_TEXTURE, true));
        
        renderBeamLayer(matrices, glowConsumer, red, green, blue, 0.2F, yOffset, height,
            outerX1, outerX1, outerX1, outerX4, outerX4, outerX4, outerX4, outerX1,
            0.0F, 1.0F, v1, v2);
        
        matrices.pop();
    }
    
    private void renderBeamLayer(MatrixStack matrices, VertexConsumer vertices,
                                  float red, float green, float blue, float alpha,
                                  int yOffset, int height,
                                  float x1, float z1, float x2, float z2,
                                  float x3, float z3, float x4, float z4,
                                  float u1, float u2, float v1, float v2) {
        
        MatrixStack.Entry entry = matrices.peek();
        Matrix4f positionMatrix = entry.getPositionMatrix();
        Matrix3f normalMatrix = entry.getNormalMatrix();
        
        renderBeamFace(positionMatrix, normalMatrix, vertices, red, green, blue, alpha,
            yOffset, height, x1, z1, x2, z2, u1, u2, v1, v2);
        renderBeamFace(positionMatrix, normalMatrix, vertices, red, green, blue, alpha,
            yOffset, height, x3, z3, x4, z4, u1, u2, v1, v2);
        renderBeamFace(positionMatrix, normalMatrix, vertices, red, green, blue, alpha,
            yOffset, height, x2, z2, x3, z3, u1, u2, v1, v2);
        renderBeamFace(positionMatrix, normalMatrix, vertices, red, green, blue, alpha,
            yOffset, height, x4, z4, x1, z1, u1, u2, v1, v2);
    }
    
    private void renderBeamFace(Matrix4f positionMatrix, Matrix3f normalMatrix,
                                 VertexConsumer vertices,
                                 float red, float green, float blue, float alpha,
                                 int yOffset, int height,
                                 float x1, float z1, float x2, float z2,
                                 float u1, float u2, float v1, float v2) {
        
        renderBeamVertex(positionMatrix, normalMatrix, vertices, red, green, blue, alpha,
            height + yOffset, x1, z1, u2, v1);
        renderBeamVertex(positionMatrix, normalMatrix, vertices, red, green, blue, alpha,
            yOffset, x1, z1, u2, v2);
        renderBeamVertex(positionMatrix, normalMatrix, vertices, red, green, blue, alpha,
            yOffset, x2, z2, u1, v2);
        renderBeamVertex(positionMatrix, normalMatrix, vertices, red, green, blue, alpha,
            height + yOffset, x2, z2, u1, v1);
    }
    
    private void renderBeamVertex(Matrix4f positionMatrix, Matrix3f normalMatrix,
                                   VertexConsumer vertices,
                                   float red, float green, float blue, float alpha,
                                   int y, float x, float z, float u, float v) {
        vertices.vertex(positionMatrix, x, (float) y, z)
            .color(red, green, blue, alpha)
            .texture(u, v)
            .overlay(OverlayTexture.DEFAULT_UV)
            .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
            .normal(0.0F, 1.0F, 0.0F);
    }
    
    public void renderHUD(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        List<TrackedPing> pings = TrackedPingManager.get().getTrackedPings();
        if (pings.isEmpty()) return;
        
        TextRenderer tr = client.textRenderer;
        int screenHeight = client.getWindow().getScaledHeight();
        
        int x = 5;
        int y = screenHeight - 14 - (pings.size() * 12);
        
        double playerX = client.player.getX();
        double playerZ = client.player.getZ();
        
        context.fill(x - 2, y - 12, x + 100, y - 1, 0xAA000000);
        context.drawTextWithShadow(tr, "Tracked Pings", x, y - 10, 0xFFFFFF);
        
        for (TrackedPing ping : pings) {
            double dx = ping.worldX - playerX;
            double dz = ping.worldZ - playerZ;
            int dist = (int) Math.sqrt(dx * dx + dz * dz);
            
            String icon = getIcon(ping.pingType);
            String name = truncate(ping.name, 12);
            String distStr = formatDistance(dist);
            String dir = getDirection(dx, dz);
            
            String text = icon + " " + name;
            String info = distStr + " " + dir;
            
            int textWidth = Math.max(tr.getWidth(text), tr.getWidth(info)) + 6;
            
            context.fill(x - 2, y - 1, x + textWidth, y + 21, 0x99000000);
            
            context.drawTextWithShadow(tr, text, x, y, ping.getColorWithAlpha());
            
            context.drawTextWithShadow(tr, info, x, y + 10, 0xCCCCCC);
            
            y += 24;
        }
    }
    
    private String getDirection(double dx, double dz) {
        double angle = Math.toDegrees(Math.atan2(dz, dx));
        angle = (angle + 360 + 90) % 360; // Adjust so 0 = North
        
        if (angle < 22.5 || angle >= 337.5) return "N";
        if (angle < 67.5) return "NE";
        if (angle < 112.5) return "E";
        if (angle < 157.5) return "SE";
        if (angle < 202.5) return "S";
        if (angle < 247.5) return "SW";
        if (angle < 292.5) return "W";
        return "NW";
    }
    
    private String getIcon(String type) {
        if (type == null) return "?";
        switch (type.toLowerCase()) {
            case "info": return "i";
            case "danger": return "!";
            case "landmark": return "*";
            case "personal": return "o";
            case "private": return "x";
            default: return "?";
        }
    }
    
    private String truncate(String s, int max) {
        if (s == null || s.isEmpty()) return "Waypoint";
        return s.length() <= max ? s : s.substring(0, max - 1) + "..";
    }
    
    private String formatDistance(int dist) {
        if (dist < 1000) return dist + "m";
        return String.format("%.1fkm", dist / 1000.0);
    }
}
