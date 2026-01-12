package com.b2tmapper.client.gui;

import com.b2tmapper.config.ModConfig;
import com.b2tmapper.config.ModConfig.MenuBarPosition;
import com.b2tmapper.config.ModConfig.UITheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class SettingsPopup extends Screen {

    private final Screen parent;
    private int popupX, popupY, popupWidth, popupHeight;

    private int GREEN_BG() { return ModConfig.get().uiTheme.bg; }
    private int GREEN_HOVER() { return ModConfig.get().uiTheme.hover; }
    private int GREEN_BORDER() { return ModConfig.get().uiTheme.border; }
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFAAAAAA;

    public SettingsPopup(Screen parent) {
        super(Text.literal("Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        popupWidth = 260;
        popupHeight = 180;
        popupX = (width - popupWidth) / 2;
        popupY = (height - popupHeight) / 2;

        ModConfig config = ModConfig.get();

        // Menu Position
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Position: " + config.menuBarPosition.name()),
            button -> {
                ModConfig c = ModConfig.get();
                MenuBarPosition[] positions = MenuBarPosition.values();
                int next = (c.menuBarPosition.ordinal() + 1) % positions.length;
                c.menuBarPosition = positions[next];
                ModConfig.save();
                button.setMessage(Text.literal("Position: " + c.menuBarPosition.name()));
            }
        ).dimensions(popupX + 20, popupY + 40, 220, 20).build());

        // Theme
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Theme: " + config.uiTheme.displayName),
            button -> {
                ModConfig c = ModConfig.get();
                UITheme[] themes = UITheme.values();
                int next = (c.uiTheme.ordinal() + 1) % themes.length;
                c.uiTheme = themes[next];
                ModConfig.save();
                button.setMessage(Text.literal("Theme: " + c.uiTheme.displayName));
                // Re-init to apply theme
                MinecraftClient.getInstance().setScreen(new SettingsPopup(parent));
            }
        ).dimensions(popupX + 20, popupY + 70, 220, 20).build());

        // Show Ping Beacons
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Ping Beacons: " + (config.showPingBeacons ? "ON" : "OFF")),
            button -> {
                ModConfig c = ModConfig.get();
                c.showPingBeacons = !c.showPingBeacons;
                ModConfig.save();
                button.setMessage(Text.literal("Ping Beacons: " + (c.showPingBeacons ? "ON" : "OFF")));
            }
        ).dimensions(popupX + 20, popupY + 100, 220, 20).build());

        // Back
        addDrawableChild(ButtonWidget.builder(
            Text.literal("Back"),
            button -> close()
        ).dimensions(popupX + popupWidth/2 - 40, popupY + 140, 80, 20).build());
    }

    // Override to disable Minecraft 1.21's blur effect
    @Override
    protected void applyBlur(float delta) {
        // Don't apply blur
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x88000000);
        
        context.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, GREEN_BG());
        drawBorder(context, popupX, popupY, popupWidth, popupHeight, GREEN_BORDER());

        String title = "Settings";
        int titleX = popupX + (popupWidth - textRenderer.getWidth(title)) / 2;
        context.drawTextWithShadow(textRenderer, title, titleX, popupY + 10, WHITE);

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
