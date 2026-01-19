package com.b2tmapper.client.gui;

import com.b2tmapper.client.MapStreamingService;
import com.b2tmapper.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class MapStreamingPopup extends Screen {

    private final Screen parent;
    
    private int popupX, popupY, popupWidth, popupHeight;
    
    private ButtonWidget streamingToggleButton;
    private ButtonWidget safeZoneToggleButton;

    private int GREEN_BG() { return ModConfig.get().uiTheme.bg; }
    private int GREEN_HOVER() { return ModConfig.get().uiTheme.hover; }
    private int GREEN_BORDER() { return ModConfig.get().uiTheme.border; }
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;
    private static final int DARK_GRAY = 0xFF666666;
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
        
        popupWidth = 340;
        popupHeight = 400;
        popupX = (width - popupWidth) / 2;
        popupY = (height - popupHeight) / 2;

        ModConfig config = ModConfig.get();
        
        int safetyBoxY = popupY + 150;
        int buttonY = popupY + 310;

        addDrawableChild(ButtonWidget.builder(
            Text.literal("<"),
            button -> adjustSafeZone(-1)
        ).dimensions(popupX + 200, safetyBoxY + 20, 20, 16).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal(">"),
            button -> adjustSafeZone(1)
        ).dimensions(popupX + 295, safetyBoxY + 20, 20, 16).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal("<"),
            button -> adjustStreamDelay(-1)
        ).dimensions(popupX + 200, safetyBoxY + 45, 20, 16).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal(">"),
            button -> adjustStreamDelay(1)
        ).dimensions(popupX + 295, safetyBoxY + 45, 20, 16).build());

        addDrawableChild(ButtonWidget.builder(
            Text.literal(config.streamButtonsLocked ? "Lock Stream Buttons: ON" : "Lock Stream Buttons: OFF"),
            button -> toggleStreamButtonsLock(button)
        ).dimensions(popupX + 20, popupY + 265, 180, 20).build());

        streamingToggleButton = ButtonWidget.builder(
            Text.literal(config.streamingEnabled ? "Disable Streaming" : "Enable Streaming"),
            button -> toggleStreaming(button)
        ).dimensions(popupX + 20, popupY + 320, 140, 20).build();
        streamingToggleButton.active = !config.streamButtonsLocked;
        addDrawableChild(streamingToggleButton);

        safeZoneToggleButton = ButtonWidget.builder(
            Text.literal(config.safeZoneEnabled ? "Safe Zone: ON" : "Safe Zone: OFF"),
            button -> toggleSafeZone(button)
        ).dimensions(popupX + 180, popupY + 320, 140, 20).build();
        addDrawableChild(safeZoneToggleButton);

        addDrawableChild(ButtonWidget.builder(
            Text.literal("Back"),
            button -> close()
        ).dimensions(popupX + popupWidth / 2 - 40, popupY + 350, 80, 20).build());
    }

    private void adjustSafeZone(int direction) {
        ModConfig config = ModConfig.get();
        int newValue = config.safeZoneRadius + (direction * ModConfig.SAFE_ZONE_INCREMENT);
        
        if (newValue < 0) newValue = 0;
        if (newValue > ModConfig.SAFE_ZONE_MAX) newValue = ModConfig.SAFE_ZONE_MAX;
        
        config.safeZoneRadius = newValue;
        ModConfig.save();
    }

    private void adjustStreamDelay(int direction) {
        ModConfig config = ModConfig.get();
        int[] options = ModConfig.STREAM_DELAY_OPTIONS;
        
        int currentIndex = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i] == config.streamDelayMinutes) {
                currentIndex = i;
                break;
            }
        }
        
        int newIndex = currentIndex + direction;
        if (newIndex < 0) newIndex = 0;
        if (newIndex >= options.length) newIndex = options.length - 1;
        
        config.streamDelayMinutes = options[newIndex];
        ModConfig.save();
    }

    private void toggleStreamButtonsLock(ButtonWidget button) {
        ModConfig config = ModConfig.get();
        config.streamButtonsLocked = !config.streamButtonsLocked;
        ModConfig.save();
        
        button.setMessage(Text.literal(config.streamButtonsLocked ? 
            "Lock Stream Buttons: ON" : "Lock Stream Buttons: OFF"));
        
        if (streamingToggleButton != null) {
            streamingToggleButton.active = !config.streamButtonsLocked;
        }
    }

    private void toggleStreaming(ButtonWidget button) {
        ModConfig config = ModConfig.get();
        
        if (config.streamButtonsLocked) {
            return;
        }
        
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

    @Override
    protected void applyBlur(float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x88000000);
        
        context.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, GREEN_BG());
        drawBorder(context, popupX, popupY, popupWidth, popupHeight, GREEN_BORDER());

        ModConfig config = ModConfig.get();
        int y = popupY + 10;

        String title = "Map Streaming";
        int titleX = popupX + (popupWidth - textRenderer.getWidth(title)) / 2;
        context.drawTextWithShadow(textRenderer, title, titleX, y, WHITE);
        y += 20;

        int warningY = y;
        context.fill(popupX + 10, warningY, popupX + popupWidth - 10, warningY + 50, 0xAA442200);
        drawBorder(context, popupX + 10, warningY, popupWidth - 20, 50, ORANGE);
        
        context.drawTextWithShadow(textRenderer, "⚠ WARNING", popupX + 20, warningY + 5, YELLOW);
        context.drawTextWithShadow(textRenderer, "Map data you stream is PUBLIC and", popupX + 20, warningY + 18, GRAY);
        context.drawTextWithShadow(textRenderer, "visible to all users on the map.", popupX + 20, warningY + 30, GRAY);
        y += 60;

        context.drawTextWithShadow(textRenderer, "Status:", popupX + 20, y, WHITE);
        
        String statusText;
        int statusColor;
        if (config.streamingEnabled && MapStreamingService.isRunning()) {
            statusText = "Active - Streaming";
            statusColor = GREEN;
        } else if (config.streamingEnabled) {
            statusText = "Enabled (Not Connected)";
            statusColor = YELLOW;
        } else {
            statusText = "Disabled";
            statusColor = RED;
        }
        context.drawTextWithShadow(textRenderer, statusText, popupX + 80, y, statusColor);
        y += 20;

        context.drawTextWithShadow(textRenderer, "Server:", popupX + 20, y, WHITE);
        String detectedName = MapStreamingService.getDetectedServerName();
        int detectedId = MapStreamingService.getDetectedServerId();
        String serverName = detectedName != null ? detectedName : "Not Detected";
        context.drawTextWithShadow(textRenderer, serverName, popupX + 80, y, detectedId > 0 ? GREEN : GRAY);
        y += 15;
        
        String currentServer = com.b2tmapper.B2TMapperMod.getCurrentServerAddress();
        if (currentServer == null) currentServer = "singleplayer";
        context.drawTextWithShadow(textRenderer, "Connected: " + currentServer, popupX + 20, y, GRAY);
        y += 25;

        int safetyBoxY = popupY + 150;
        context.fill(popupX + 10, safetyBoxY, popupX + popupWidth - 10, safetyBoxY + 70, 0x44000000);
        drawBorder(context, popupX + 10, safetyBoxY, popupWidth - 20, 70, GREEN_BORDER());
        
        context.drawTextWithShadow(textRenderer, "Safety Settings", popupX + 20, safetyBoxY + 5, WHITE);
        
        context.drawTextWithShadow(textRenderer, "Safe Zone:", popupX + 20, safetyBoxY + 24, GRAY);
        String safeZoneText = formatDistance(config.safeZoneRadius);
        int safeZoneTextWidth = textRenderer.getWidth(safeZoneText);
        int safeZoneCenterX = popupX + 257;
        context.drawTextWithShadow(textRenderer, safeZoneText, safeZoneCenterX - safeZoneTextWidth / 2, safetyBoxY + 24, WHITE);
        
        context.drawTextWithShadow(textRenderer, "Stream Delay:", popupX + 20, safetyBoxY + 49, GRAY);
        String delayText = config.streamDelayMinutes == 0 ? "None" : config.streamDelayMinutes + " min";
        int delayTextWidth = textRenderer.getWidth(delayText);
        int delayCenterX = popupX + 257;
        context.drawTextWithShadow(textRenderer, delayText, delayCenterX - delayTextWidth / 2, safetyBoxY + 49, WHITE);

        if (config.safeZoneEnabled && config.safeZoneRadius > 0) {
            String safeZoneWarning = "⚠ Leaving zone will STOP streaming";
            context.drawTextWithShadow(textRenderer, safeZoneWarning, popupX + 20, popupY + 230, ORANGE);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    private String formatDistance(int blocks) {
        if (blocks >= 1000000) {
            return String.format("%.1fm", blocks / 1000000.0);
        } else if (blocks >= 1000) {
            return String.format("%dk", blocks / 1000);
        } else {
            return String.valueOf(blocks);
        }
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
