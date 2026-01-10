package com.b2tmapper.client.gui;

import com.b2tmapper.config.ModConfig;
import com.b2tmapper.config.ModConfig.UITheme;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public abstract class BasePopup extends Screen {

    protected static final Identifier LOGO_TEXTURE = Identifier.of("b2tmapper", "textures/logo.png");

    protected static final int WHITE = 0xFFFFFFFF;
    protected static final int GRAY = 0xFFAAAAAA;

    protected int GREEN_BG() { return getTheme().bg; }
    protected int GREEN_HOVER() { return getTheme().hover; }
    protected int GREEN_BORDER() { return getTheme().border; }
    protected int GREEN_BUTTON() { return getTheme().button; }
    protected int BLUE_SELECTED() { return getTheme().selectedBg; }
    protected int BLUE_BORDER() { return getTheme().selectedBorder; }
    protected int BLUE_HOVER() { return getTheme().selectedHover; }
    protected int HEADER_BG() { return getTheme().headerBg; }
    protected int SECTION_TEXT() { return getTheme().sectionText; }

    protected UITheme getTheme() {
        return ModConfig.get().uiTheme;
    }

    protected int popupX, popupY, popupWidth, popupHeight;
    protected int logoSize = 20;
    protected int headerHeight = 28;
    protected int padding = 6;

    protected final Screen parent;
    protected final String popupTitle;
    protected boolean closeHovered = false;

    protected BasePopup(Screen parent, String title) {
        super(Text.literal(title));
        this.parent = parent;
        this.popupTitle = title;
    }

    @Override
    protected void init() {
        super.init();
        popupWidth = 240;
        popupHeight = 260;
        popupX = (width - popupWidth) / 2;
        popupY = (height - popupHeight) / 2;
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0x88000000);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);

        context.fill(popupX, popupY, popupX + popupWidth, popupY + popupHeight, GREEN_BG());
        drawBorder(context, popupX, popupY, popupWidth, popupHeight, GREEN_BORDER());

        context.fill(popupX, popupY, popupX + popupWidth, popupY + headerHeight, HEADER_BG());
        context.fill(popupX, popupY + headerHeight - 1, popupX + popupWidth, popupY + headerHeight, GREEN_BORDER());

        try {
            context.drawTexture(LOGO_TEXTURE, popupX + padding, popupY + 4, 0, 0, logoSize, logoSize, logoSize, logoSize);
        } catch (Exception e) {
        }

        context.drawTextWithShadow(textRenderer, popupTitle, popupX + padding + logoSize + 6, popupY + 10, WHITE);

        int closeX = popupX + popupWidth - 22;
        int closeY = popupY + 4;
        int closeSize = 18;

        closeHovered = mouseX >= closeX && mouseX < closeX + closeSize && mouseY >= closeY && mouseY < closeY + closeSize;

        int closeBg = closeHovered ? 0xD9AA3333 : 0xD9662222;
        context.fill(closeX, closeY, closeX + closeSize, closeY + closeSize, closeBg);
        drawBorder(context, closeX, closeY, closeSize, closeSize, 0xFFAA3333);
        context.drawCenteredTextWithShadow(textRenderer, "X", closeX + closeSize/2, closeY + 5, WHITE);

        renderContent(context, mouseX, mouseY, delta);
    }

    protected abstract void renderContent(DrawContext context, int mouseX, int mouseY, float delta);

    protected void drawBorder(DrawContext context, int x, int y, int w, int h, int color) {
        context.fill(x, y, x + w, y + 1, color);
        context.fill(x, y + h - 1, x + w, y + h, color);
        context.fill(x, y, x + 1, y + h, color);
        context.fill(x + w - 1, y, x + w, y + h, color);
    }

    protected boolean drawButton(DrawContext context, int x, int y, int w, int h, String text, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

        int bg = hovered ? GREEN_HOVER() : GREEN_BUTTON();
        context.fill(x, y, x + w, y + h, bg);
        drawBorder(context, x, y, w, h, GREEN_BORDER());

        int textX = x + (w - textRenderer.getWidth(text)) / 2;
        int textY = y + (h - 8) / 2;
        context.drawTextWithShadow(textRenderer, text, textX, textY, WHITE);

        return hovered;
    }

    protected boolean drawSelectButton(DrawContext context, int x, int y, int w, int h, String text, boolean selected, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

        int bg, border;
        if (selected) {
            bg = hovered ? BLUE_HOVER() : BLUE_SELECTED();
            border = BLUE_BORDER();
        } else {
            bg = hovered ? GREEN_HOVER() : GREEN_BUTTON();
            border = GREEN_BORDER();
        }

        context.fill(x, y, x + w, y + h, bg);
        drawBorder(context, x, y, w, h, border);

        int textX = x + (w - textRenderer.getWidth(text)) / 2;
        int textY = y + (h - 8) / 2;
        context.drawTextWithShadow(textRenderer, text, textX, textY, WHITE);

        return hovered;
    }

    protected boolean drawCheckbox(DrawContext context, int x, int y, String label, boolean checked, int mouseX, int mouseY) {
        int boxSize = 14;
        boolean hovered = mouseX >= x && mouseX < x + boxSize && mouseY >= y && mouseY < y + boxSize;

        int bg, border;
        if (checked) {
            bg = hovered ? BLUE_HOVER() : BLUE_SELECTED();
            border = BLUE_BORDER();
        } else {
            bg = hovered ? GREEN_HOVER() : GREEN_BUTTON();
            border = GREEN_BORDER();
        }

        context.fill(x, y, x + boxSize, y + boxSize, bg);
        drawBorder(context, x, y, boxSize, boxSize, border);

        if (checked) {
            context.drawCenteredTextWithShadow(textRenderer, "o", x + boxSize/2, y + 3, WHITE);
        }

        context.drawTextWithShadow(textRenderer, label, x + boxSize + 6, y + 3, WHITE);

        return hovered;
    }

    protected void drawSlider(DrawContext context, int x, int y, int w, int h, float value, String label) {
        context.fill(x, y, x + w, y + h, GREEN_BUTTON());
        drawBorder(context, x, y, w, h, GREEN_BORDER());

        int fillWidth = (int)(w * value);
        if (fillWidth > 1) {
            context.fill(x + 1, y + 1, x + fillWidth, y + h - 1, BLUE_SELECTED());
        }

        if (value > 0) {
            drawBorder(context, x, y, w, h, BLUE_BORDER());
        }
    }

    protected void drawSectionHeader(DrawContext context, int x, int y, String text) {
        context.drawTextWithShadow(textRenderer, text, x, y, SECTION_TEXT());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && closeHovered) {
            goBack();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    protected void goBack() {
        MinecraftClient.getInstance().setScreen(parent);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            goBack();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
