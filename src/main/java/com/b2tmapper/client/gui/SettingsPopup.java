package com.b2tmapper.client.gui;

import com.b2tmapper.config.ModConfig;
import com.b2tmapper.config.ModConfig.MenuBarPosition;
import com.b2tmapper.config.ModConfig.UITheme;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

public class SettingsPopup extends BasePopup {

    private boolean topHovered, leftHovered, rightHovered;
    private boolean themeGreenHovered, themeBlueHovered, themeRedHovered, themeDefaultHovered;

    public SettingsPopup(Screen parent) {
        super(parent, "Settings");
    }

    @Override
    protected void init() {
        super.init();
        popupWidth = 260;
        popupHeight = 160;
        popupX = (width - popupWidth) / 2;
        popupY = (height - popupHeight) / 2;
    }

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        int contentX = popupX + padding;
        int contentY = popupY + headerHeight + padding;

        ModConfig config = ModConfig.get();

        drawSectionHeader(context, contentX, contentY, "Menu Bar Position");
        contentY += 16;

        MenuBarPosition currentPos = config.menuBarPosition;
        int btnWidth = 65;
        int btnHeight = 18;
        int spacing = 6;

        topHovered = drawSelectButton(context, contentX, contentY, btnWidth, btnHeight,
            "Top", currentPos == MenuBarPosition.TOP, mouseX, mouseY);
        leftHovered = drawSelectButton(context, contentX + btnWidth + spacing, contentY, btnWidth, btnHeight,
            "Left", currentPos == MenuBarPosition.LEFT, mouseX, mouseY);
        rightHovered = drawSelectButton(context, contentX + (btnWidth + spacing) * 2, contentY, btnWidth, btnHeight,
            "Right", currentPos == MenuBarPosition.RIGHT, mouseX, mouseY);

        contentY += btnHeight + 16;

        drawSectionHeader(context, contentX, contentY, "UI Theme");
        contentY += 16;

        UITheme currentTheme = config.uiTheme;
        int themeBtnWidth = 55;

        themeGreenHovered = drawSelectButton(context, contentX, contentY, themeBtnWidth, btnHeight,
            "Green", currentTheme == UITheme.GREEN, mouseX, mouseY);
        themeBlueHovered = drawSelectButton(context, contentX + themeBtnWidth + spacing, contentY, themeBtnWidth, btnHeight,
            "Blue", currentTheme == UITheme.BLUE, mouseX, mouseY);
        themeRedHovered = drawSelectButton(context, contentX + (themeBtnWidth + spacing) * 2, contentY, themeBtnWidth, btnHeight,
            "Red", currentTheme == UITheme.RED, mouseX, mouseY);
        themeDefaultHovered = drawSelectButton(context, contentX + (themeBtnWidth + spacing) * 3, contentY, themeBtnWidth, btnHeight,
            "Default", currentTheme == UITheme.DEFAULT, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            ModConfig config = ModConfig.get();
            boolean changed = false;

            if (topHovered) {
                config.menuBarPosition = MenuBarPosition.TOP;
                changed = true;
            }
            if (leftHovered) {
                config.menuBarPosition = MenuBarPosition.LEFT;
                changed = true;
            }
            if (rightHovered) {
                config.menuBarPosition = MenuBarPosition.RIGHT;
                changed = true;
            }

            if (themeGreenHovered) {
                config.uiTheme = UITheme.GREEN;
                changed = true;
            }
            if (themeBlueHovered) {
                config.uiTheme = UITheme.BLUE;
                changed = true;
            }
            if (themeRedHovered) {
                config.uiTheme = UITheme.RED;
                changed = true;
            }
            if (themeDefaultHovered) {
                config.uiTheme = UITheme.DEFAULT;
                changed = true;
            }

            if (changed) {
                ModConfig.save();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
