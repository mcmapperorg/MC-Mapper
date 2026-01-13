package com.b2tmapper.client.gui;

import com.b2tmapper.B2TMapperMod;
import com.b2tmapper.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PingCreatorPopup extends BasePopup {

    private static final String API_BASE = "https://mc-mapper-production.up.railway.app/api";
    
    private static final String[] PING_TYPES = {"info", "danger", "landmark", "personal", "private"};
    private static final String[] PING_LABELS = {"Info", "Danger", "Landmark", "Personal", "Private"};
    private static final int[] PING_COLORS = {0xFF4444FF, 0xFFFF4444, 0xFFFFD700, 0xFF44FF44, 0xFFAA44FF};
    
    private int selectedType = 0;
    private int hoveredType = -1;
    private boolean submitHovered = false;
    private boolean cancelHovered = false;
    
    private TextFieldWidget descriptionField;
    
    private BufferedImage screenshot;
    private NativeImageBackedTexture screenshotTexture;
    private Identifier screenshotTextureId;
    private String screenshotBase64;
    
    private double exactX, exactY, exactZ;
    private int gridX, gridZ;
    private String serverAddress;
    private String dimension;
    private String playerName;
    private String playerUuid;
    
    private int[] credits = {0, 0, 0, 0, 0};
    private boolean creditsLoaded = false;
    private boolean isLoadingCredits = true;
    
    private boolean isSubmitting = false;
    private String statusMessage = null;
    private String errorMessage = null;
    
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();
    
    private static boolean pendingScreenshot = false;
    private static int screenshotDelayFrames = 0;
    private static boolean suppressHud = false;

    public PingCreatorPopup(Screen parent, BufferedImage screenshot) {
        super(parent, "Create Ping");
        this.screenshot = screenshot;
        captureLocationData();
        loadCredits();
    }
    
    public static void startDelayedCapture() {
        if (pendingScreenshot) return;
        pendingScreenshot = true;
        suppressHud = true;
        screenshotDelayFrames = 3;
    }
    
    public static void tickScreenshot() {
        if (!pendingScreenshot) return;
        
        if (screenshotDelayFrames > 0) {
            screenshotDelayFrames--;
            return;
        }
        
        pendingScreenshot = false;
        suppressHud = false;
        
        BufferedImage screenshot = captureScreenshotNow();
        if (screenshot != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            client.execute(() -> {
                client.setScreen(new PingCreatorPopup(null, screenshot));
            });
        } else {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                client.player.sendMessage(Text.literal("§cFailed to capture screenshot"), false);
            }
        }
    }
    
    public static boolean shouldSuppressHud() {
        return suppressHud;
    }
    
    private static BufferedImage captureScreenshotNow() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return null;
        
        try {
            boolean hudWasHidden = client.options.hudHidden;
            client.options.hudHidden = true;
            
            Framebuffer framebuffer = client.getFramebuffer();
            int width = framebuffer.textureWidth;
            int height = framebuffer.textureHeight;
            
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            
            try (NativeImage nativeImage = ScreenshotRecorder.takeScreenshot(framebuffer)) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int color = nativeImage.getColor(x, y);
                        int a = (color >> 24) & 0xFF;
                        int b = (color >> 16) & 0xFF;
                        int g = (color >> 8) & 0xFF;
                        int r = color & 0xFF;
                        image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                    }
                }
            }
            
            client.options.hudHidden = hudWasHidden;
            
            return image;
            
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    private void captureLocationData() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        
        exactX = client.player.getX();
        exactY = client.player.getY();
        exactZ = client.player.getZ();
        
        gridX = (int) Math.floor(exactX / 128.0);
        gridZ = (int) Math.floor(exactZ / 128.0);
        
        serverAddress = "singleplayer";
        ServerInfo serverInfo = client.getCurrentServerEntry();
        if (serverInfo != null) {
            serverAddress = serverInfo.address;
        }
        
        dimension = client.world.getRegistryKey().getValue().toString();
        playerName = client.player.getName().getString();
        playerUuid = client.player.getUuidAsString();
    }
    
    private void loadCredits() {
        ModConfig config = ModConfig.get();
        if (config.authToken == null || config.authToken.isEmpty()) {
            isLoadingCredits = false;
            errorMessage = "Not logged in - link account first";
            return;
        }
        
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE + "/auth/mod-session"))
                        .header("Authorization", "Bearer " + config.authToken)
                        .header("X-Mod-Version", B2TMapperMod.MOD_VERSION)
                        .GET()
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                    if (json.get("success").getAsBoolean()) {
                        JsonObject user = json.getAsJsonObject("user");
                        credits[0] = user.has("info_credits") ? user.get("info_credits").getAsInt() : 0;
                        credits[1] = user.has("danger_credits") ? user.get("danger_credits").getAsInt() : 0;
                        credits[2] = user.has("landmark_credits") ? user.get("landmark_credits").getAsInt() : 0;
                        credits[3] = user.has("personal_credits") ? user.get("personal_credits").getAsInt() : 0;
                        credits[4] = user.has("private_credits") ? user.get("private_credits").getAsInt() : 0;
                        creditsLoaded = true;
                    } else {
                        errorMessage = "Session expired - relink account";
                    }
                } else {
                    errorMessage = "Failed to load credits";
                }
            } catch (Exception e) {
                e.printStackTrace();
                errorMessage = "Connection error";
            }
            isLoadingCredits = false;
        });
    }
    
    @Override
    protected void init() {
        super.init();
        popupWidth = 340;
        popupHeight = 320;
        popupX = (width - popupWidth) / 2;
        popupY = (height - popupHeight) / 2;
        
        int fieldWidth = popupWidth - padding * 2 - 10;
        int fieldX = popupX + padding + 5;
        int fieldY = popupY + headerHeight + 130;
        
        descriptionField = new TextFieldWidget(textRenderer, fieldX, fieldY, fieldWidth, 20, Text.literal("Description"));
        descriptionField.setMaxLength(500);
        descriptionField.setPlaceholder(Text.literal("Describe this location..."));
        addDrawableChild(descriptionField);
        
        if (screenshot != null) {
            createScreenshotTexture();
            encodeScreenshotBase64();
        }
    }
    
    private void createScreenshotTexture() {
        try {
            int width = screenshot.getWidth();
            int height = screenshot.getHeight();
            
            NativeImage nativeImage = new NativeImage(width, height, false);
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int argb = screenshot.getRGB(x, y);
                    int a = (argb >> 24) & 0xFF;
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    nativeImage.setColor(x, y, (a << 24) | (b << 16) | (g << 8) | r);
                }
            }
            
            screenshotTexture = new NativeImageBackedTexture(nativeImage);
            screenshotTextureId = Identifier.of("b2tmapper", "ping_screenshot_" + UUID.randomUUID().toString().substring(0, 8));
            MinecraftClient.getInstance().getTextureManager().registerTexture(screenshotTextureId, screenshotTexture);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void encodeScreenshotBase64() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(screenshot, "PNG", baos);
            screenshotBase64 = Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        int contentX = popupX + padding;
        int contentY = popupY + headerHeight + padding;
        
        if (screenshotTextureId != null) {
            int previewWidth = 160;
            int previewHeight = 90;
            int previewX = contentX + (popupWidth - padding * 2 - previewWidth) / 2;
            
            context.fill(previewX - 2, contentY - 2, previewX + previewWidth + 2, contentY + previewHeight + 2, GREEN_BORDER());
            context.drawTexture(screenshotTextureId, previewX, contentY, 0, 0, previewWidth, previewHeight, previewWidth, previewHeight);
        } else {
            context.drawCenteredTextWithShadow(textRenderer, "No screenshot", popupX + popupWidth / 2, contentY + 40, GRAY);
        }
        
        contentY += 100;
        
        String coordsText = String.format("Location: %d, %d, %d (Grid: %d, %d)", 
            (int) exactX, (int) exactY, (int) exactZ, gridX, gridZ);
        context.drawCenteredTextWithShadow(textRenderer, coordsText, popupX + popupWidth / 2, contentY, GRAY);
        
        contentY += 18;
        contentY += 28;
        
        drawSectionHeader(context, contentX, contentY, "Ping Type");
        contentY += 14;
        
        hoveredType = -1;
        int btnWidth = 60;
        int btnHeight = 22;
        int spacing = 4;
        int totalWidth = (btnWidth * 5) + (spacing * 4);
        int startX = contentX + (popupWidth - padding * 2 - totalWidth) / 2;
        
        for (int i = 0; i < 5; i++) {
            int btnX = startX + (i * (btnWidth + spacing));
            boolean hovered = mouseX >= btnX && mouseX < btnX + btnWidth && mouseY >= contentY && mouseY < contentY + btnHeight;
            if (hovered) hoveredType = i;
            
            boolean hasCredits = credits[i] > 0;
            boolean isSelected = selectedType == i;
            
            int bg;
            if (isSelected) {
                bg = 0xD9337733;
            } else if (!hasCredits) {
                bg = 0x44333333;
            } else if (hovered) {
                bg = GREEN_HOVER();
            } else {
                bg = GREEN_BUTTON();
            }
            
            context.fill(btnX, contentY, btnX + btnWidth, contentY + btnHeight, bg);
            drawBorder(context, btnX, contentY, btnWidth, btnHeight, hasCredits ? PING_COLORS[i] : 0xFF666666);
            
            String label = PING_LABELS[i];
            int textColor = hasCredits ? PING_COLORS[i] : 0xFF666666;
            int textX = btnX + (btnWidth - textRenderer.getWidth(label)) / 2;
            context.drawTextWithShadow(textRenderer, label, textX, contentY + 4, textColor);
            
            String creditStr = String.valueOf(credits[i]);
            context.drawCenteredTextWithShadow(textRenderer, creditStr, btnX + btnWidth / 2, contentY + 13, hasCredits ? WHITE : 0xFF666666);
        }
        
        contentY += btnHeight + 12;
        
        if (errorMessage != null) {
            context.drawCenteredTextWithShadow(textRenderer, errorMessage, popupX + popupWidth / 2, contentY, 0xFFFF4444);
            contentY += 12;
        } else if (statusMessage != null) {
            context.drawCenteredTextWithShadow(textRenderer, statusMessage, popupX + popupWidth / 2, contentY, 0xFF44FF44);
            contentY += 12;
        } else if (isLoadingCredits) {
            context.drawCenteredTextWithShadow(textRenderer, "Loading credits...", popupX + popupWidth / 2, contentY, GRAY);
            contentY += 12;
        }
        
        contentY += 8;
        
        int actionBtnWidth = 100;
        int actionBtnHeight = 26;
        int actionSpacing = 20;
        int actionStartX = popupX + (popupWidth - actionBtnWidth * 2 - actionSpacing) / 2;
        
        boolean canSubmit = creditsLoaded && credits[selectedType] > 0 && !isSubmitting && screenshotBase64 != null;
        submitHovered = canSubmit && mouseX >= actionStartX && mouseX < actionStartX + actionBtnWidth 
                && mouseY >= contentY && mouseY < contentY + actionBtnHeight;
        
        int submitBg = !canSubmit ? 0x44333333 : (submitHovered ? 0xD944AA44 : 0xD9228822);
        context.fill(actionStartX, contentY, actionStartX + actionBtnWidth, contentY + actionBtnHeight, submitBg);
        drawBorder(context, actionStartX, contentY, actionBtnWidth, actionBtnHeight, canSubmit ? 0xFF44FF44 : 0xFF666666);
        String submitText = isSubmitting ? "Submitting..." : "Submit Ping";
        context.drawCenteredTextWithShadow(textRenderer, submitText, actionStartX + actionBtnWidth / 2, contentY + 9, canSubmit ? WHITE : 0xFF666666);
        
        int cancelX = actionStartX + actionBtnWidth + actionSpacing;
        cancelHovered = !isSubmitting && mouseX >= cancelX && mouseX < cancelX + actionBtnWidth 
                && mouseY >= contentY && mouseY < contentY + actionBtnHeight;
        
        int cancelBg = cancelHovered ? 0x66663333 : 0x44442222;
        context.fill(cancelX, contentY, cancelX + actionBtnWidth, contentY + actionBtnHeight, cancelBg);
        drawBorder(context, cancelX, contentY, actionBtnWidth, actionBtnHeight, 0xFF664444);
        context.drawCenteredTextWithShadow(textRenderer, "Cancel", cancelX + actionBtnWidth / 2, contentY + 9, WHITE);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (hoveredType >= 0 && credits[hoveredType] > 0) {
                selectedType = hoveredType;
                return true;
            }
            
            if (submitHovered) {
                submitPing();
                return true;
            }
            
            if (cancelHovered) {
                goBack();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    private void submitPing() {
        ModConfig config = ModConfig.get();
        if (config.authToken == null || screenshotBase64 == null) return;
        
        isSubmitting = true;
        statusMessage = "Submitting ping...";
        errorMessage = null;
        
        CompletableFuture.runAsync(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("pingType", PING_TYPES[selectedType]);
                body.addProperty("description", descriptionField.getText().trim());
                body.addProperty("screenshot", screenshotBase64);
                body.addProperty("worldX", (int) exactX);
                body.addProperty("worldY", (int) exactY);
                body.addProperty("worldZ", (int) exactZ);
                body.addProperty("gridX", gridX);
                body.addProperty("gridZ", gridZ);
                body.addProperty("serverAddress", serverAddress);
                body.addProperty("dimension", dimension);
                body.addProperty("playerName", playerName);
                body.addProperty("playerUuid", playerUuid);
                body.addProperty("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE + "/mod/pings/submit"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + config.authToken)
                        .header("X-Mod-Version", B2TMapperMod.MOD_VERSION)
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                        .build();
                
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                
                if (response.statusCode() == 200 && json.get("success").getAsBoolean()) {
                    statusMessage = "Ping submitted for approval!";
                    
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player != null) {
                        client.player.sendMessage(
                            Text.literal("\u00a7a\u2713 Ping submitted for admin approval!"),
                            false
                        );
                    }
                    
                    CompletableFuture.delayedExecutor(2, java.util.concurrent.TimeUnit.SECONDS)
                        .execute(() -> {
                            MinecraftClient.getInstance().execute(this::goBack);
                        });
                } else {
                    String error = json.has("error") ? json.get("error").getAsString() : "Submission failed";
                    errorMessage = error;
                    statusMessage = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
                errorMessage = "Connection failed";
                statusMessage = null;
            }
            isSubmitting = false;
        });
    }
    
    @Override
    public void close() {
        if (screenshotTextureId != null) {
            MinecraftClient.getInstance().getTextureManager().destroyTexture(screenshotTextureId);
        }
        super.close();
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (descriptionField.isFocused()) {
            if (keyCode == 257) {
                descriptionField.setFocused(false);
                return true;
            }
            return descriptionField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
