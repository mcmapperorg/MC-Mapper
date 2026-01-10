package com.b2tmapper.client.gui;

import com.b2tmapper.config.ModConfig;
import com.b2tmapper.config.ModConfig.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

public class MapSettingsPopup extends BasePopup {

    private static final int DISABLED_BG = 0xD9442222;
    private static final int DISABLED_BORDER = 0xFF663333;
    private static final int DISABLED_TEXT = 0xFF888888;

    private boolean showMapHovered;
    private boolean modeGridHovered, modeGridFillHovered, modeExploreHovered;
    private boolean posTLHovered, posTRHovered, posBLHovered, posBRHovered;
    private boolean shapeSquareHovered, shapeCircleHovered;
    private boolean selectServerHovered;
    private boolean sizeSliderDragging = false;

    private int sliderY;

    public MapSettingsPopup(Screen parent) {
        super(parent, "Map Settings");
    }

    @Override
    protected void init() {
        super.init();
        popupWidth = 280;
        popupHeight = 310;
        popupX = (width - popupWidth) / 2;
        popupY = (height - popupHeight) / 2;
    }

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        int contentX = popupX + padding;
        int contentY = popupY + headerHeight + padding;
        int btnWidth = 70;
        int btnHeight = 18;
        int spacing = 4;

        ModConfig config = ModConfig.get();
        boolean isGridMode = config.mapMode == MapMode.GRID;
        boolean isGridFillMode = config.mapMode == MapMode.GRID_FILL;

        drawSectionHeader(context, contentX, contentY, "Show Map");
        contentY += 14;

        showMapHovered = drawSelectButton(context, contentX, contentY, btnWidth, btnHeight,
            config.showMap ? "Visible" : "Hidden", true, mouseX, mouseY);

        int indicatorColor = config.showMap ? 0xFF44FF44 : 0xFFFF4444;
        context.fill(contentX + btnWidth + spacing, contentY + 4, contentX + btnWidth + spacing + 10, contentY + btnHeight - 4, indicatorColor);

        contentY += btnHeight + 12;

        drawSectionHeader(context, contentX, contentY, "Map Mode");
        contentY += 14;

        int smallBtnWidth = 58;
        modeGridHovered = drawSelectButton(context, contentX, contentY, smallBtnWidth, btnHeight,
            "Grid", isGridMode, mouseX, mouseY);
        modeGridFillHovered = drawSelectButton(context, contentX + smallBtnWidth + spacing, contentY, smallBtnWidth, btnHeight,
            "Grid Fill", isGridFillMode, mouseX, mouseY);
        modeExploreHovered = false;
        drawDisabledButton(context, contentX + (smallBtnWidth + spacing) * 2, contentY, smallBtnWidth + 30, btnHeight, "Explore - Soon");

        contentY += btnHeight + 8;
        
        if (isGridFillMode) {
            String serverText = config.gridFillServerName != null ? config.gridFillServerName : "No Server Selected";
            if (serverText.length() > 20) serverText = serverText.substring(0, 18) + "..";
            
            context.drawTextWithShadow(textRenderer, "Server: " + serverText, contentX, contentY, 
                config.gridFillServerId > 0 ? 0xFF44FF44 : 0xFFFF8844);
            contentY += 12;
            
            selectServerHovered = drawSelectButton(context, contentX, contentY, 100, btnHeight,
                "Select Server", false, mouseX, mouseY);
            contentY += btnHeight + 4;
        } else {
            selectServerHovered = false;
        }

        contentY += 4;

        drawSectionHeader(context, contentX, contentY, "Position");
        contentY += 14;

        int smallBtn = 52;
        posTLHovered = drawSelectButton(context, contentX, contentY, smallBtn, btnHeight,
            "TL", config.mapPosition == MapPosition.TOP_LEFT, mouseX, mouseY);
        posTRHovered = drawSelectButton(context, contentX + smallBtn + spacing, contentY, smallBtn, btnHeight,
            "TR", config.mapPosition == MapPosition.TOP_RIGHT, mouseX, mouseY);
        posBLHovered = drawSelectButton(context, contentX + (smallBtn + spacing) * 2, contentY, smallBtn, btnHeight,
            "BL", config.mapPosition == MapPosition.BOTTOM_LEFT, mouseX, mouseY);
        posBRHovered = drawSelectButton(context, contentX + (smallBtn + spacing) * 3, contentY, smallBtn, btnHeight,
            "BR", config.mapPosition == MapPosition.BOTTOM_RIGHT, mouseX, mouseY);

        contentY += btnHeight + 12;

        boolean disableSizeShape = isGridMode || isGridFillMode;
        int headerColor = disableSizeShape ? DISABLED_TEXT : SECTION_TEXT();
        context.drawTextWithShadow(textRenderer, "Size: " + config.mapSize + "px", contentX, contentY, headerColor);
        contentY += 14;

        int sliderWidth = popupWidth - padding * 2 - 10;
        int sliderX = contentX;
        sliderY = contentY;
        int sliderH = 14;

        float sizeNorm = (config.mapSize - 50) / 200f;

        if (disableSizeShape) {
            context.fill(sliderX, sliderY, sliderX + sliderWidth, sliderY + sliderH, DISABLED_BG);
            drawBorder(context, sliderX, sliderY, sliderWidth, sliderH, DISABLED_BORDER);
        } else {
            drawSlider(context, sliderX, sliderY, sliderWidth, sliderH, sizeNorm, "");
        }

        contentY += sliderH + 12;

        headerColor = disableSizeShape ? DISABLED_TEXT : SECTION_TEXT();
        context.drawTextWithShadow(textRenderer, "Shape", contentX, contentY, headerColor);
        contentY += 14;

        if (disableSizeShape) {
            shapeSquareHovered = false;
            shapeCircleHovered = false;
            drawDisabledButton(context, contentX, contentY, btnWidth, btnHeight, "Square");
            drawDisabledButton(context, contentX + btnWidth + spacing, contentY, btnWidth, btnHeight, "Circle");
        } else {
            shapeSquareHovered = drawSelectButton(context, contentX, contentY, btnWidth, btnHeight,
                "Square", config.mapShape == MapShape.SQUARE, mouseX, mouseY);
            shapeCircleHovered = drawSelectButton(context, contentX + btnWidth + spacing, contentY, btnWidth, btnHeight,
                "Circle", config.mapShape == MapShape.CIRCLE, mouseX, mouseY);
        }
    }

    private void drawDisabledButton(DrawContext context, int x, int y, int w, int h, String text) {
        context.fill(x, y, x + w, y + h, DISABLED_BG);
        drawBorder(context, x, y, w, h, DISABLED_BORDER);

        int textX = x + (w - textRenderer.getWidth(text)) / 2;
        int textY = y + (h - 8) / 2;
        context.drawTextWithShadow(textRenderer, text, textX, textY, DISABLED_TEXT);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            ModConfig config = ModConfig.get();
            boolean changed = false;
            boolean isGridMode = config.mapMode == MapMode.GRID;
            boolean isGridFillMode = config.mapMode == MapMode.GRID_FILL;

            if (showMapHovered) { config.showMap = !config.showMap; changed = true; }

            if (modeGridHovered) { config.mapMode = MapMode.GRID; changed = true; }
            if (modeGridFillHovered) { config.mapMode = MapMode.GRID_FILL; changed = true; }
            if (modeExploreHovered) { config.mapMode = MapMode.EXPLORATION; changed = true; }
            
            if (selectServerHovered && isGridFillMode) {
                MinecraftClient.getInstance().setScreen(new GridFillServerPopup(this));
                return true;
            }

            if (posTLHovered) { config.mapPosition = MapPosition.TOP_LEFT; changed = true; }
            if (posTRHovered) { config.mapPosition = MapPosition.TOP_RIGHT; changed = true; }
            if (posBLHovered) { config.mapPosition = MapPosition.BOTTOM_LEFT; changed = true; }
            if (posBRHovered) { config.mapPosition = MapPosition.BOTTOM_RIGHT; changed = true; }

            if (!isGridMode && !isGridFillMode) {
                if (shapeSquareHovered) { config.mapShape = MapShape.SQUARE; changed = true; }
                if (shapeCircleHovered) { config.mapShape = MapShape.CIRCLE; changed = true; }

                int sliderX = popupX + padding;
                int sliderWidth = popupWidth - padding * 2 - 10;
                int sliderH = 14;

                if (mouseX >= sliderX && mouseX < sliderX + sliderWidth && mouseY >= sliderY && mouseY < sliderY + sliderH) {
                    sizeSliderDragging = true;
                    updateSizeFromMouse(mouseX, sliderX, sliderWidth);
                    changed = true;
                }
            }

            if (changed) {
                ModConfig.save();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void updateSizeFromMouse(double mouseX, int sliderX, int sliderWidth) {
        float newNorm = (float)(mouseX - sliderX) / sliderWidth;
        newNorm = Math.max(0, Math.min(1, newNorm));
        ModConfig.get().mapSize = (int)(50 + newNorm * 200);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) sizeSliderDragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && sizeSliderDragging && ModConfig.get().mapMode == MapMode.EXPLORATION) {
            int sliderX = popupX + padding;
            int sliderWidth = popupWidth - padding * 2 - 10;
            updateSizeFromMouse(mouseX, sliderX, sliderWidth);
            ModConfig.save();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
}
