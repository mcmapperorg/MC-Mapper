package com.b2tmapper.client.gui;

import com.b2tmapper.config.ModConfig;
import com.b2tmapper.config.ModConfig.MenuBarPosition;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class MenuBarScreen extends Screen {

    private static final Identifier LOGO_TEXTURE = Identifier.of("b2tmapper", "textures/logo.png");

    private static final int WHITE = 0xFFFFFFFF;
    
    private int GREEN_BG() { return ModConfig.get().uiTheme.bg; }
    private int GREEN_HOVER() { return ModConfig.get().uiTheme.hover; }
    private int GREEN_BORDER() { return ModConfig.get().uiTheme.border; }

    private static final String[] TABS = {"Settings", "Map Streaming", "Ping List", "Live View", "Account"};

    private int barX, barY, barWidth, barHeight;
    private int logoSize = 20;
    private int tabHeight = 18;
    private int tabWidth = 80;
    private int padding = 4;

    private int hoveredTab = -1;

    public MenuBarScreen() {
        super(Text.literal("MCMapper Menu"));
    }

    @Override
    protected void init() {
        super.init();
        calculateLayout();
    }

    private void calculateLayout() {
        MenuBarPosition pos = ModConfig.get().menuBarPosition;

        switch (pos) {
            case TOP:
                barWidth = logoSize + padding * 3 + (TABS.length * tabWidth) + (TABS.length - 1) * 2;
                barHeight = tabHeight + padding * 2;
                barX = (width - barWidth) / 2;
                barY = 8;
                break;
            case LEFT:
                barWidth = tabWidth + padding * 2;
                barHeight = logoSize + padding * 2 + (TABS.length * (tabHeight + 2));
                barX = 8;
                barY = (height - barHeight) / 2;
                break;
            case RIGHT:
                barWidth = tabWidth + padding * 2;
                barHeight = logoSize + padding * 2 + (TABS.length * (tabHeight + 2));
                barX = width - barWidth - 8;
                barY = (height - barHeight) / 2;
                break;
        }
    }

    @Override
    protected void applyBlur(float delta) {
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, width, height, 0x88000000);
        renderMenuBar(context, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
    }

    private void renderMenuBar(DrawContext context, int mouseX, int mouseY) {
        MenuBarPosition pos = ModConfig.get().menuBarPosition;

        context.fill(barX, barY, barX + barWidth, barY + barHeight, GREEN_BG());
        drawBorder(context, barX, barY, barWidth, barHeight, GREEN_BORDER());

        if (pos == MenuBarPosition.TOP) {
            renderHorizontalLayout(context, mouseX, mouseY);
        } else {
            renderVerticalLayout(context, mouseX, mouseY);
        }
    }

    private void renderHorizontalLayout(DrawContext context, int mouseX, int mouseY) {
        int logoX = barX + padding;
        int logoY = barY + (barHeight - logoSize) / 2;

        try {
            context.drawTexture(LOGO_TEXTURE, logoX, logoY, 0, 0, logoSize, logoSize, logoSize, logoSize);
        } catch (Exception e) {
            context.fill(logoX, logoY, logoX + logoSize, logoY + logoSize, GREEN_HOVER());
        }

        hoveredTab = -1;
        int tabStartX = logoX + logoSize + padding * 2;

        for (int i = 0; i < TABS.length; i++) {
            int tabX = tabStartX + (i * (tabWidth + 2));
            int tabY = barY + padding;

            boolean hovered = mouseX >= tabX && mouseX < tabX + tabWidth && mouseY >= tabY && mouseY < tabY + tabHeight;
            if (hovered) hoveredTab = i;

            int bg = hovered ? GREEN_HOVER() : GREEN_BG();
            context.fill(tabX, tabY, tabX + tabWidth, tabY + tabHeight, bg);
            drawBorder(context, tabX, tabY, tabWidth, tabHeight, GREEN_BORDER());

            int textX = tabX + (tabWidth - textRenderer.getWidth(TABS[i])) / 2;
            int textY = tabY + (tabHeight - 8) / 2;
            context.drawTextWithShadow(textRenderer, TABS[i], textX, textY, WHITE);
        }
    }

    private void renderVerticalLayout(DrawContext context, int mouseX, int mouseY) {
        int logoX = barX + (barWidth - logoSize) / 2;
        int logoY = barY + padding;

        try {
            context.drawTexture(LOGO_TEXTURE, logoX, logoY, 0, 0, logoSize, logoSize, logoSize, logoSize);
        } catch (Exception e) {
            context.fill(logoX, logoY, logoX + logoSize, logoY + logoSize, GREEN_HOVER());
        }

        hoveredTab = -1;
        int tabStartY = logoY + logoSize + padding;

        for (int i = 0; i < TABS.length; i++) {
            int tabX = barX + padding;
            int tabY = tabStartY + (i * (tabHeight + 2));

            boolean hovered = mouseX >= tabX && mouseX < tabX + tabWidth && mouseY >= tabY && mouseY < tabY + tabHeight;
            if (hovered) hoveredTab = i;

            int bg = hovered ? GREEN_HOVER() : GREEN_BG();
            context.fill(tabX, tabY, tabX + tabWidth, tabY + tabHeight, bg);
            drawBorder(context, tabX, tabY, tabWidth, tabHeight, GREEN_BORDER());

            int textX = tabX + (tabWidth - textRenderer.getWidth(TABS[i])) / 2;
            int textY = tabY + (tabHeight - 8) / 2;
            context.drawTextWithShadow(textRenderer, TABS[i], textX, textY, WHITE);
        }
    }

    private void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hoveredTab >= 0) {
            openPopup(hoveredTab);
            return true;
        }

        if (!isMouseOverBar((int)mouseX, (int)mouseY)) {
            close();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean isMouseOverBar(int mouseX, int mouseY) {
        return mouseX >= barX && mouseX < barX + barWidth && mouseY >= barY && mouseY < barY + barHeight;
    }

    private void openPopup(int tabIndex) {
        MinecraftClient client = MinecraftClient.getInstance();

        switch (tabIndex) {
            case 0:
                client.setScreen(new SettingsPopup(this));
                break;
            case 1:
                client.setScreen(new MapStreamingPopup(this));
                break;
            case 2:
                client.setScreen(new PingListPopup(this));
                break;
            case 3:
                client.setScreen(new LiveViewPopup(this));
                break;
            case 4:
                client.setScreen(new AccountPopup(this));
                break;
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256 || keyCode == 344) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
