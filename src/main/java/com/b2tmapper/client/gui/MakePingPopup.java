package com.b2tmapper.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

public class MakePingPopup extends BasePopup {
    
    private static final String[] PING_TYPES = {"Danger", "Landmark", "Info", "Private", "Personal"};
    private static final int[] PING_COLORS = {0xFFFF4444, 0xFFFFD700, 0xFF4444FF, 0xFFAA44FF, 0xFF44FF44};
    
    private int selectedType = 4; // Default to Personal
    private int hoveredType = -1;
    private boolean createHovered = false;
    private boolean useCurrentHovered = false;
    
    private int pingX, pingY, pingZ;
    
    public MakePingPopup(Screen parent) {
        super(parent, "Make a Ping");
        updateCoordinates();
    }
    
    private void updateCoordinates() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            pingX = (int) client.player.getX();
            pingY = (int) client.player.getY();
            pingZ = (int) client.player.getZ();
        }
    }
    
    @Override
    protected void init() {
        super.init();
        popupWidth = 280;
        popupHeight = 240;
        popupX = (width - popupWidth) / 2;
        popupY = (height - popupHeight) / 2;
    }
    
    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        int contentX = popupX + padding;
        int contentY = popupY + headerHeight + padding;
        
        drawSectionHeader(context, contentX, contentY, "Ping Type");
        contentY += 16;
        
        hoveredType = -1;
        int btnWidth = 75;
        int btnHeight = 22;
        int spacing = 4;
        
        for (int i = 0; i < 3; i++) {
            int btnX = contentX + (i * (btnWidth + spacing));
            boolean hovered = mouseX >= btnX && mouseX < btnX + btnWidth && mouseY >= contentY && mouseY < contentY + btnHeight;
            if (hovered) hoveredType = i;
            
            int bg = (selectedType == i) ? 0xD9337733 : (hovered ? GREEN_HOVER() : GREEN_BUTTON());
            context.fill(btnX, contentY, btnX + btnWidth, contentY + btnHeight, bg);
            drawBorder(context, btnX, contentY, btnWidth, btnHeight, PING_COLORS[i]);
            
            String label = (selectedType == i) ? "[" + PING_TYPES[i] + "]" : PING_TYPES[i];
            int textX = btnX + (btnWidth - textRenderer.getWidth(label)) / 2;
            context.drawTextWithShadow(textRenderer, label, textX, contentY + 7, PING_COLORS[i]);
        }
        
        contentY += btnHeight + spacing;
        
        for (int i = 3; i < 5; i++) {
            int btnX = contentX + ((i - 3) * (btnWidth + spacing));
            boolean hovered = mouseX >= btnX && mouseX < btnX + btnWidth && mouseY >= contentY && mouseY < contentY + btnHeight;
            if (hovered) hoveredType = i;
            
            int bg = (selectedType == i) ? 0xD9337733 : (hovered ? GREEN_HOVER() : GREEN_BUTTON());
            context.fill(btnX, contentY, btnX + btnWidth, contentY + btnHeight, bg);
            drawBorder(context, btnX, contentY, btnWidth, btnHeight, PING_COLORS[i]);
            
            String label = (selectedType == i) ? "[" + PING_TYPES[i] + "]" : PING_TYPES[i];
            int textX = btnX + (btnWidth - textRenderer.getWidth(label)) / 2;
            context.drawTextWithShadow(textRenderer, label, textX, contentY + 7, PING_COLORS[i]);
        }
        
        contentY += btnHeight + 18;
        
        drawSectionHeader(context, contentX, contentY, "Location");
        contentY += 16;
        
        String coordsText = String.format("X: %d  Y: %d  Z: %d", pingX, pingY, pingZ);
        context.fill(contentX, contentY, contentX + 180, contentY + 22, GREEN_BUTTON());
        drawBorder(context, contentX, contentY, 180, 22, GREEN_BORDER());
        context.drawTextWithShadow(textRenderer, coordsText, contentX + 8, contentY + 7, WHITE);
        
        int useBtnX = contentX + 188;
        useCurrentHovered = mouseX >= useBtnX && mouseX < useBtnX + 70 && mouseY >= contentY && mouseY < contentY + 22;
        int useBg = useCurrentHovered ? GREEN_HOVER() : GREEN_BUTTON();
        context.fill(useBtnX, contentY, useBtnX + 70, contentY + 22, useBg);
        drawBorder(context, useBtnX, contentY, 70, 22, GREEN_BORDER());
        context.drawCenteredTextWithShadow(textRenderer, "Update", useBtnX + 35, contentY + 7, WHITE);
        
        contentY += 36;
        
        int createBtnWidth = 120;
        int createBtnX = popupX + (popupWidth - createBtnWidth) / 2;
        createHovered = mouseX >= createBtnX && mouseX < createBtnX + createBtnWidth && mouseY >= contentY && mouseY < contentY + 28;
        
        int createBg = createHovered ? 0xD944AA44 : 0xD9228822;
        context.fill(createBtnX, contentY, createBtnX + createBtnWidth, contentY + 28, createBg);
        drawBorder(context, createBtnX, contentY, createBtnWidth, 28, 0xFF44FF44);
        context.drawCenteredTextWithShadow(textRenderer, "Create Ping", createBtnX + createBtnWidth / 2, contentY + 10, WHITE);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hoveredType >= 0) {
                selectedType = hoveredType;
                return true;
            }
            
            if (useCurrentHovered) {
                updateCoordinates();
                return true;
            }
            
            if (createHovered) {
                createPing();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    private void createPing() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        
        String typeName = PING_TYPES[selectedType];
        int color = PING_COLORS[selectedType];
        
        client.player.sendMessage(
            net.minecraft.text.Text.literal("§a✓ Ping created: §f" + typeName + " §7at §f" + pingX + ", " + pingY + ", " + pingZ),
            false
        );
        
        
        goBack();
    }
}
