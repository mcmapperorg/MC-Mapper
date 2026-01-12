package com.b2tmapper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import com.b2tmapper.util.PingExporter;
import com.b2tmapper.client.LiveViewBroadcaster;
import com.b2tmapper.client.MapStreamingService;
import com.b2tmapper.client.gui.MenuBarScreen;
import com.b2tmapper.client.ping.TrackedPingRenderer;
import com.b2tmapper.client.ping.TrackedPingManager;
import com.b2tmapper.config.ModConfig;

public class B2TMapperMod implements ClientModInitializer {

    public static final String MOD_ID = "b2t-mapper";
    public static final String MOD_VERSION = "2.0.0";

    private static KeyBinding pingKeyBinding;
    private static KeyBinding hideUiKey;
    private static KeyBinding menuKeyBinding;

    public static boolean hideModUI = false;

    @Override
    public void onInitializeClient() {

        ModConfig.load();
        
        // Initialize services
        LiveViewBroadcaster.initialize();
        MapStreamingService.initialize();

        // Register Mapping Status HUD (top center) and Tracked Pings HUD
        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (!hideModUI) {
                renderMappingStatusHud(context);
                // Render tracked pings HUD (bottom left)
                if (ModConfig.get().showPingBeacons) {
                    TrackedPingRenderer.get().renderHUD(context, tickCounter.getTickDelta(true));
                }
            }
        });

        // Ping key (P)
        pingKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.b2tmapper.ping",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "category.b2tmapper"
        ));

        // Hide UI key (H)
        hideUiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.b2tmapper.hide_ui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.b2tmapper"
        ));

        // Menu key (Right Shift)
        menuKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.b2tmapper.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.b2tmapper"
        ));

        // Client tick handler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PingExporter.tickScreenshot();

            if (client.player == null || client.world == null) {
                return;
            }

            // Handle key presses
            if (pingKeyBinding.wasPressed()) {
                PingExporter.exportPing();
            }

            if (hideUiKey.wasPressed()) {
                hideModUI = !hideModUI;
                String status = hideModUI ? "§7UI Hidden" : "§aUI Visible";
                client.player.sendMessage(net.minecraft.text.Text.literal(status), true);
            }

            if (menuKeyBinding.wasPressed()) {
                client.setScreen(new MenuBarScreen());
            }
        });

        // World render for ping beacons
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (!hideModUI && ModConfig.get().showPingBeacons) {
                MinecraftClient client = MinecraftClient.getInstance();
                TrackedPingRenderer.get().renderWorld(
                    context.matrixStack(),
                    context.consumers(),
                    context.camera(),
                    client.getRenderTickCounter().getTickDelta(true)
                );
            }
        });
    }

    /**
     * Render "Mapping Active" status in top center when streaming is enabled
     */
    private void renderMappingStatusHud(DrawContext context) {
        ModConfig config = ModConfig.get();
        
        if (!config.streamingEnabled) {
            return;
        }
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return;
        }
        
        int screenWidth = client.getWindow().getScaledWidth();
        
        String statusText;
        int statusColor;
        
        if (MapStreamingService.isRunning()) {
            if (MapStreamingService.isInSafeZone()) {
                statusText = "⚠ Mapping Paused (Safe Zone)";
                statusColor = 0xFFFFAA00; // Orange
            } else {
                statusText = "● Mapping Active";
                statusColor = 0xFF44FF44; // Green
            }
        } else {
            statusText = "○ Mapping Ready";
            statusColor = 0xFF888888; // Gray
        }
        
        int textWidth = client.textRenderer.getWidth(statusText);
        int x = (screenWidth - textWidth) / 2;
        int y = 5;
        
        // Background
        context.fill(x - 4, y - 2, x + textWidth + 4, y + 11, 0xAA000000);
        
        // Text
        context.drawTextWithShadow(client.textRenderer, statusText, x, y, statusColor);
    }

    public static boolean isUiHidden() {
        return hideModUI || PingExporter.suppressHudRendering;
    }
    
    /**
     * Get the current server address the player is connected to
     */
    public static String getCurrentServerAddress() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getCurrentServerEntry() != null) {
            return client.getCurrentServerEntry().address.toLowerCase();
        }
        if (client.isInSingleplayer()) {
            return "singleplayer";
        }
        return null;
    }
}
