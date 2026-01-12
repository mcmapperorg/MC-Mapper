package com.b2tmapper.client.gui;

import com.b2tmapper.client.MapStreamingService;
import com.b2tmapper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class MapStreamingPopup extends Screen {

    private final Screen parent;
    private TextFieldWidget safeZoneInput;
    
    private int popupX, popupY, popupWidth, popupHeight;

    private int GREEN_BG() { return ModConfig.get().uiTheme.bg; }
    private int GREEN_HOVER() { return ModConfig.get().uiTheme.hover; }
    private int GREEN_BORDER() { return ModConfig.get().uiTheme.border; }
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int YELLOW = 0xFFFFFF00;
    private static final int RED = 0xFFFF5555;
    private static final int GREEN = 0xFF55FF55;
    private static final int ORANGE = 0xFFFF8800;

    public MapStreamingPopup(Screen parent) {
        super(Text.literal("Map Streaming"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        
        popupWidth = 320;
        popupHeight = 280;
        popupX = (width - popupWidth) / 2;
        popupY = (height - popupHeight) / 2;

        ModConfig config = ModConfig.get();

        // Safe Zone Input
        safeZoneInput = new TextFieldWidget(
            textRenderer,
            popupX + 180, popupY + 175, 80, 18,
            Text.literal("Safe Zone")
        );
        safeZoneInput.setText(String.valueOf(config.safeZoneRadius));
        safeZoneInput.setChangedListener(this::onSafeZoneChanged);
        addDrawableChild(safeZoneInput);

        // Toggle Streaming Button
        addDrawableChild(ButtonWidget.builder(
            Text.literal(config.streamingEnabled ? "Disable Streaming" : "Enable Streaming"),
            button -> toggleStreaming(button)
        ).dimensions(popupX + 20, popupY + 220, 130, 20).build());

        // Safe Zone Toggle Button
        addDrawableChild(ButtonWidget.builder(
            Text.literal(config.safeZoneEnabled ? "Safe Zone: ON" : "Safe Zone: OFF"),
            button -> toggleSafeZone(button)
        ).dimensions(popupX + 170, popupY + 220, 130, 20).build());

        // Back Button
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Back"),
            button -> close()
        ).dimensions(popupX + popupWidth / 2 - 40, popupY + 250, 80, 20).build());
    }

    private void toggleStreaming(ButtonWidget button) {
        ModConfig config = ModConfig.get();
        config.streamingEnabled = !config.streamingEnabled;
        ModConfig.save();
        
        if (config.streamingEnabled) {
            MapStreamingService.start();
            button.setMessage(Text.literal("Disable Streaming"));
        } else {
            MapStreamingService.stop();
            button.setMessage(Text.literal("Enable Streaming"));
        }
    }

    private void toggleSafeZone(ButtonWidget button) {
        ModConfig config = ModConfig.get();
        config.safeZoneEnabled = !config.safeZoneEnabled;
        ModConfig.save();
        
        button.setMessage(Text.literal(config.safeZoneEnabled ? "Safe Zone: ON" : "Safe Zone: OFF"));
    }

    private void onSafeZoneChanged(String value) {
        try {
            int radius = Integer.parseInt(value);
            if (radius >= 0) {
                ModConfig config = ModConfig.get();
                config.safeZoneRadius = radius;
                ModConfig.save();
            }
        } catch (NumberFormatException e) {
            // Ignore invalid input
        }
    }

    // Override to disable Minecraft 1.21's blur effect
    @Override
    protected void applyBlur(float delta) {
        // Don't apply blur
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Darken background (no blur)
        context.fill(0, 0, width, height, 0x88000000);
        
        // Popup background
        context.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, GREEN_BG());
        drawBorder(context, popupX, popupY, popupWidth, popupHeight, GREEN_BORDER());

        ModConfig config = ModConfig.get();
        int y = popupY + 10;

        // Title
        String title = "Map Streaming";
        int titleX = popupX + (popupWidth - textRenderer.getWidth(title)) / 2;
        context.drawTextWithShadow(textRenderer, title, titleX, y, WHITE);
        y += 20;

        // Warning Box
        int warningY = y;
        context.fill(popupX + 10, warningY, popupX + popupWidth - 10, warningY + 50, 0xAA442200);
        drawBorder(context, popupX + 10, warningY, popupWidth - 20, 50, ORANGE);
        
        context.drawTextWithShadow(textRenderer, "⚠ WARNING", popupX + 20, warningY + 5, YELLOW);
        context.drawTextWithShadow(textRenderer, "Map data you stream is PUBLIC and", popupX + 20, warningY + 18, GRAY);
        context.drawTextWithShadow(textRenderer, "visible to all users on the map.", popupX + 20, warningY + 30, GRAY);
        y += 60;

        // Status Section
        context.drawTextWithShadow(textRenderer, "Status:", popupX + 20, y, WHITE);
        
        String statusText;
        int statusColor;
        if (config.streamingEnabled && MapStreamingService.isRunning()) {
            if (MapStreamingService.isInSafeZone()) {
                statusText = "Paused (In Safe Zone)";
                statusColor = YELLOW;
            } else {
                statusText = "Active - Mapping";
                statusColor = GREEN;
            }
        } else if (config.streamingEnabled) {
            statusText = "Enabled (Not Connected)";
            statusColor = YELLOW;
        } else {
            statusText = "Disabled";
            statusColor = RED;
        }
        context.drawTextWithShadow(textRenderer, statusText, popupX + 80, y, statusColor);
        y += 20;

        // Server Section
        context.drawTextWithShadow(textRenderer, "Server:", popupX + 20, y, WHITE);
        String serverName = config.streamingServerName != null ? config.streamingServerName : "Not Selected";
        context.drawTextWithShadow(textRenderer, serverName, popupX + 80, y, config.streamingServerId > 0 ? GREEN : GRAY);
        y += 15;
        
        if (config.streamingServerAddress != null) {
            context.drawTextWithShadow(textRenderer, "Address: " + config.streamingServerAddress, popupX + 20, y, GRAY);
        }
        y += 25;

        // Safe Zone Section
        context.fill(popupX + 10, y, popupX + popupWidth - 10, y + 55, 0x44000000);
        drawBorder(context, popupX + 10, y, popupWidth - 20, 55, GREEN_BORDER());
        
        context.drawTextWithShadow(textRenderer, "Safe Zone Settings", popupX + 20, y + 5, WHITE);
        context.drawTextWithShadow(textRenderer, "Radius from 0,0:", popupX + 20, y + 22, GRAY);
        
        String safeZoneStatus = config.safeZoneEnabled ? 
            "Streaming pauses within " + config.safeZoneRadius + " blocks of spawn" :
            "Safe zone protection is disabled";
        context.drawTextWithShadow(textRenderer, safeZoneStatus, popupX + 20, y + 40, 
            config.safeZoneEnabled ? YELLOW : GRAY);

        super.render(context, mouseX, mouseY, delta);
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
