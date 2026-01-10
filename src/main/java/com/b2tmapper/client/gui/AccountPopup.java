package com.b2tmapper.client.gui;

import com.b2tmapper.config.ModConfig;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

public class AccountPopup extends BasePopup {

    private static final String API_BASE = "https://mc-mapper-production.up.railway.app/api";

    private boolean linkHovered = false;
    private boolean logoutHovered = false;
    private boolean isLinked = false;
    private String linkedUsername = null;
    private String errorMessage = null;
    private String statusMessage = null;
    private boolean isLoading = false;

    private TextFieldWidget codeField;
    private boolean showCodeEntry = false;
    private boolean submitHovered = false;
    private boolean cancelHovered = false;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    public AccountPopup(Screen parent) {
        super(parent, "Account");
        checkExistingSession();
    }

    @Override
    protected void init() {
        super.init();
        popupWidth = 280;
        popupHeight = 220;
        popupX = (width - popupWidth) / 2;
        popupY = (height - popupHeight) / 2;

        int fieldWidth = 160;
        int fieldX = popupX + (popupWidth - fieldWidth) / 2;
        int fieldY = popupY + headerHeight + 60;

        codeField = new TextFieldWidget(textRenderer, fieldX, fieldY, fieldWidth, 20, Text.literal("Code"));
        codeField.setMaxLength(14); // MCM-XXXX-XXXX
        codeField.setPlaceholder(Text.literal("MCM-XXXX-XXXX"));
        codeField.setVisible(showCodeEntry);
        codeField.setEditable(true);
        addDrawableChild(codeField);
    }

    private void checkExistingSession() {
        ModConfig config = ModConfig.get();
        if (config.authToken != null && !config.authToken.isEmpty()) {
            validateSession();
        }
    }

    private void validateSession() {
        ModConfig config = ModConfig.get();
        if (config.authToken == null || config.authToken.isEmpty()) {
            return;
        }

        isLoading = true;
        statusMessage = "Checking session...";

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE + "/auth/mod-session"))
                        .header("Authorization", "Bearer " + config.authToken)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonObject json = gson.fromJson(response.body(), JsonObject.class);
                    if (json.get("success").getAsBoolean()) {
                        JsonObject user = json.getAsJsonObject("user");
                        linkedUsername = user.get("username").getAsString();
                        isLinked = true;
                        statusMessage = null;
                    } else {
                        clearSession();
                    }
                } else {
                    clearSession();
                }
            } catch (Exception e) {
                e.printStackTrace();
                statusMessage = "Connection error";
            }
            isLoading = false;
        });
    }

    private void clearSession() {
        ModConfig config = ModConfig.get();
        config.authToken = null;
        config.linkedUsername = null;
        config.save();
        isLinked = false;
        linkedUsername = null;
        statusMessage = null;
    }

    private void submitCode() {
        String code = codeField.getText().trim().toUpperCase();
        if (code.isEmpty()) {
            errorMessage = "Please enter a code";
            return;
        }

        isLoading = true;
        errorMessage = null;
        statusMessage = "Verifying code...";

        MinecraftClient client = MinecraftClient.getInstance();
        String mcUsername = client.getSession().getUsername();
        String mcUuid = client.getSession().getUuidOrNull() != null 
            ? client.getSession().getUuidOrNull().toString() : null;

        CompletableFuture.runAsync(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("code", code);
                body.addProperty("minecraftUsername", mcUsername);
                if (mcUuid != null) {
                    body.addProperty("minecraftUuid", mcUuid);
                }

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE + "/auth/verify-mod-code"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body)))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                JsonObject json = gson.fromJson(response.body(), JsonObject.class);

                if (response.statusCode() == 200 && json.get("success").getAsBoolean()) {
                    String token = json.get("token").getAsString();
                    JsonObject user = json.getAsJsonObject("user");
                    String username = user.get("username").getAsString();

                    ModConfig config = ModConfig.get();
                    config.authToken = token;
                    config.linkedUsername = username;
                    config.save();

                    linkedUsername = username;
                    isLinked = true;
                    showCodeEntry = false;
                    codeField.setVisible(false);
                    statusMessage = "Successfully linked!";
                    errorMessage = null;

                    CompletableFuture.delayedExecutor(3, java.util.concurrent.TimeUnit.SECONDS)
                        .execute(() -> statusMessage = null);

                } else {
                    String error = json.has("error") ? json.get("error").getAsString() : "Invalid code";
                    errorMessage = error;
                    statusMessage = null;
                }
            } catch (Exception e) {
                e.printStackTrace();
                errorMessage = "Connection failed";
                statusMessage = null;
            }
            isLoading = false;
        });
    }

    private void logout() {
        ModConfig config = ModConfig.get();
        if (config.authToken == null) return;

        isLoading = true;
        statusMessage = "Logging out...";

        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE + "/auth/mod-logout"))
                        .header("Authorization", "Bearer " + config.authToken)
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();

                httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                e.printStackTrace();
            }

            clearSession();
            isLoading = false;
        });
    }

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float delta) {
        int contentX = popupX + padding;
        int contentY = popupY + headerHeight + padding;
        int contentWidth = popupWidth - padding * 2;

        if (showCodeEntry) {
            renderCodeEntry(context, mouseX, mouseY, contentX, contentY, contentWidth);
        } else if (isLinked) {
            renderLinkedState(context, mouseX, mouseY, contentX, contentY, contentWidth);
        } else {
            renderUnlinkedState(context, mouseX, mouseY, contentX, contentY, contentWidth);
        }
    }

    private void renderUnlinkedState(DrawContext context, int mouseX, int mouseY, int contentX, int contentY, int contentWidth) {
        drawSectionHeader(context, contentX, contentY, "Status");
        contentY += 16;

        if (isLoading && statusMessage != null) {
            context.drawTextWithShadow(textRenderer, statusMessage, contentX, contentY, GRAY);
        } else {
            context.drawTextWithShadow(textRenderer, "Not Linked", contentX, contentY, 0xFFFF6666);
        }
        contentY += 24;

        int btnWidth = 180;
        int btnHeight = 24;
        int btnX = contentX + (contentWidth - btnWidth) / 2;

        linkHovered = mouseX >= btnX && mouseX < btnX + btnWidth && mouseY >= contentY && mouseY < contentY + btnHeight;

        int bg = linkHovered ? BLUE_HOVER() : BLUE_SELECTED();
        context.fill(btnX, contentY, btnX + btnWidth, contentY + btnHeight, bg);
        drawBorder(context, btnX, contentY, btnWidth, btnHeight, BLUE_BORDER());
        context.drawCenteredTextWithShadow(textRenderer, "Enter Link Code", btnX + btnWidth / 2, contentY + 8, WHITE);

        contentY += btnHeight + 20;

        context.fill(contentX, contentY, contentX + contentWidth, contentY + 1, GREEN_BORDER());
        contentY += 10;

        context.drawTextWithShadow(textRenderer, "To link your account:", contentX, contentY, WHITE);
        contentY += 14;
        context.drawTextWithShadow(textRenderer, "1. Log in at mcmapper.com", contentX + 6, contentY, GRAY);
        contentY += 12;
        context.drawTextWithShadow(textRenderer, "2. Go to Account > Link Mod", contentX + 6, contentY, GRAY);
        contentY += 12;
        context.drawTextWithShadow(textRenderer, "3. Copy the code shown", contentX + 6, contentY, GRAY);
        contentY += 12;
        context.drawTextWithShadow(textRenderer, "4. Enter it here", contentX + 6, contentY, GRAY);
    }

    private void renderCodeEntry(DrawContext context, int mouseX, int mouseY, int contentX, int contentY, int contentWidth) {
        drawSectionHeader(context, contentX, contentY, "Enter Link Code");
        contentY += 16;

        context.drawTextWithShadow(textRenderer, "Get code from mcmapper.com", contentX, contentY, GRAY);
        contentY += 20;

        codeField.setY(contentY);
        codeField.setX(popupX + (popupWidth - 160) / 2);
        contentY += 28;

        if (errorMessage != null) {
            context.drawCenteredTextWithShadow(textRenderer, errorMessage, popupX + popupWidth / 2, contentY, 0xFFFF4444);
            contentY += 14;
        }

        if (statusMessage != null) {
            context.drawCenteredTextWithShadow(textRenderer, statusMessage, popupX + popupWidth / 2, contentY, GRAY);
            contentY += 14;
        }

        contentY += 10;

        int btnWidth = 80;
        int btnHeight = 22;
        int spacing = 16;
        int totalWidth = btnWidth * 2 + spacing;
        int startX = contentX + (contentWidth - totalWidth) / 2;

        submitHovered = !isLoading && mouseX >= startX && mouseX < startX + btnWidth 
                && mouseY >= contentY && mouseY < contentY + btnHeight;
        int submitBg = isLoading ? 0x44446644 : (submitHovered ? GREEN_HOVER() : GREEN_BUTTON());
        context.fill(startX, contentY, startX + btnWidth, contentY + btnHeight, submitBg);
        drawBorder(context, startX, contentY, btnWidth, btnHeight, GREEN_BORDER());
        context.drawCenteredTextWithShadow(textRenderer, isLoading ? "..." : "Submit", 
                startX + btnWidth / 2, contentY + 7, isLoading ? GRAY : WHITE);

        int cancelX = startX + btnWidth + spacing;
        cancelHovered = !isLoading && mouseX >= cancelX && mouseX < cancelX + btnWidth 
                && mouseY >= contentY && mouseY < contentY + btnHeight;
        int cancelBg = cancelHovered ? 0x66663333 : 0x44442222;
        context.fill(cancelX, contentY, cancelX + btnWidth, contentY + btnHeight, cancelBg);
        drawBorder(context, cancelX, contentY, btnWidth, btnHeight, 0xFF664444);
        context.drawCenteredTextWithShadow(textRenderer, "Cancel", cancelX + btnWidth / 2, contentY + 7, WHITE);
    }

    private void renderLinkedState(DrawContext context, int mouseX, int mouseY, int contentX, int contentY, int contentWidth) {
        drawSectionHeader(context, contentX, contentY, "Status");
        contentY += 16;

        if (statusMessage != null) {
            context.drawTextWithShadow(textRenderer, statusMessage, contentX, contentY, 0xFF44FF44);
        } else {
            context.drawTextWithShadow(textRenderer, "Linked", contentX, contentY, 0xFF44FF44);
        }
        contentY += 20;

        context.drawTextWithShadow(textRenderer, "Account:", contentX, contentY, GRAY);
        context.drawTextWithShadow(textRenderer, linkedUsername != null ? linkedUsername : "Unknown", 
                contentX + 60, contentY, WHITE);
        contentY += 24;

        context.fill(contentX, contentY, contentX + contentWidth, contentY + 1, GREEN_BORDER());
        contentY += 16;

        context.drawTextWithShadow(textRenderer, "You can now:", contentX, contentY, WHITE);
        contentY += 14;
        context.drawTextWithShadow(textRenderer, "- View your private pings", contentX + 6, contentY, GRAY);
        contentY += 12;
        context.drawTextWithShadow(textRenderer, "- Sync pings from website", contentX + 6, contentY, GRAY);
        contentY += 12;
        context.drawTextWithShadow(textRenderer, "- Access premium features", contentX + 6, contentY, GRAY);
        contentY += 24;

        int btnWidth = 100;
        int btnHeight = 22;
        int btnX = contentX + (contentWidth - btnWidth) / 2;

        logoutHovered = !isLoading && mouseX >= btnX && mouseX < btnX + btnWidth 
                && mouseY >= contentY && mouseY < contentY + btnHeight;
        int bg = logoutHovered ? 0x66663333 : 0x44442222;
        context.fill(btnX, contentY, btnX + btnWidth, contentY + btnHeight, bg);
        drawBorder(context, btnX, contentY, btnWidth, btnHeight, 0xFF664444);
        context.drawCenteredTextWithShadow(textRenderer, isLoading ? "..." : "Unlink", 
                btnX + btnWidth / 2, contentY + 7, WHITE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (showCodeEntry) {
                if (submitHovered && !isLoading) {
                    submitCode();
                    return true;
                }
                if (cancelHovered && !isLoading) {
                    showCodeEntry = false;
                    codeField.setVisible(false);
                    errorMessage = null;
                    statusMessage = null;
                    return true;
                }
            } else if (isLinked) {
                if (logoutHovered && !isLoading) {
                    logout();
                    return true;
                }
            } else {
                if (linkHovered && !isLoading) {
                    showCodeEntry = true;
                    codeField.setVisible(true);
                    codeField.setFocused(true);
                    codeField.setText("");
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (showCodeEntry && codeField.isFocused()) {
            if (keyCode == 257) { // GLFW_KEY_ENTER
                submitCode();
                return true;
            }
            if (keyCode == 256) { // GLFW_KEY_ESCAPE
                showCodeEntry = false;
                codeField.setVisible(false);
                errorMessage = null;
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
