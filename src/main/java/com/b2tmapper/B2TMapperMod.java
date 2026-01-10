package com.b2tmapper;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.map.MapState;
import net.minecraft.item.FilledMapItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.MapIdComponent;
import org.lwjgl.glfw.GLFW;
import com.b2tmapper.MapDataExporter;
import com.b2tmapper.util.PingExporter;
import com.b2tmapper.util.MapRegenerator;
import com.b2tmapper.client.GridHudOverlay;
import com.b2tmapper.client.CoordinateHud;
import com.b2tmapper.client.MiniGridMap;
import com.b2tmapper.client.ExplorationMap;
import com.b2tmapper.client.GridFillMap;
import com.b2tmapper.client.GridBeacon;
import com.b2tmapper.client.LiveViewBroadcaster;
import com.b2tmapper.client.gui.MenuBarScreen;
import com.b2tmapper.client.ping.TrackedPingRenderer;
import com.b2tmapper.client.ping.TrackedPingManager;
import com.b2tmapper.config.ModConfig;

import java.util.HashSet;
import java.util.Set;

public class B2TMapperMod implements ClientModInitializer {

    public static final String MOD_ID = "b2t-mapper";
    public static final String MOD_VERSION = "1.0.0";

    private static KeyBinding exportKeyBinding;
    private static KeyBinding beaconToggleKey;
    private static KeyBinding pingKeyBinding;
    private static KeyBinding hideUiKey;
    private static KeyBinding menuKeyBinding;
    private static MapTrackingData mapTrackingData = null;

    private static final Set<Integer> invalidatedMapIds = new HashSet<>();

    private static final Set<Integer> exportedMapIds = new HashSet<>();

    private static final Set<Integer> modCreatedMapIds = new HashSet<>();

    private static final Set<Integer> colorRegeneratedMapIds = new HashSet<>();

    private static int pendingRegenMapId = -1;
    private static int pendingRegenGridX = 0;
    private static int pendingRegenGridZ = 0;
    private static int pendingRegenTicksRemaining = 0;

    public static boolean hideModUI = false;

    @Override
    public void onInitializeClient() {

        ModConfig.load();
        
        LiveViewBroadcaster.initialize();

        CoordinateHud.register();
        GridHudOverlay.register();
        MiniGridMap.register();
        ExplorationMap.register();
        GridFillMap.register();

        exportKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.b2tmapper.export",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_ALT,
            "category.b2tmapper"
        ));

        beaconToggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
            "key.b2tmapper.toggle_beacon",
            InputUtil.Type.KEYSYM,
            GLFW.GLFW_KEY_RIGHT_CONTROL,
            "category.b2tmapper"
        ));

        pingKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.b2tmapper.ping",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                "category.b2tmapper"
        ));

        hideUiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.b2tmapper.hide_ui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                "category.b2tmapper"
        ));

        menuKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.b2tmapper.menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                "category.b2tmapper"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            PingExporter.tickScreenshot();

            tickPendingColorRegeneration(client);

            if (client.player == null || client.world == null) {
                mapTrackingData = null;
                return;
            }

            ItemStack heldItem = client.player.getMainHandStack();

            if (heldItem.getItem() == Items.FILLED_MAP) {
                int mapId = getMapId(heldItem);

                MapState mapState = FilledMapItem.getMapState(heldItem, client.world);

                if (mapState != null && mapId != -1) {
                    int currentSlot = client.player.getInventory().selectedSlot;

                    int mapCenterX = 0;
                    int mapCenterZ = 0;

                    try {
                        mapCenterX = mapState.centerX;
                        mapCenterZ = mapState.centerZ;
                    } catch (Exception e) {
                    }

                    if (mapCenterX == 0 && mapCenterZ == 0) {
                        int playerXInt = (int) client.player.getX();
                        int playerZInt = (int) client.player.getZ();
                        mapCenterX = ((playerXInt >> 7) << 7);
                        mapCenterZ = ((playerZInt >> 7) << 7);
                    }

                    int mapGridX = Math.floorDiv(mapCenterX + 64, 128);
                    int mapGridZ = Math.floorDiv(mapCenterZ + 64, 128);

                    int playerXPos = (int) client.player.getX();
                    int playerZPos = (int) client.player.getZ();
                    int playerGridX = Math.floorDiv(playerXPos + 64, 128);
                    int playerGridZ = Math.floorDiv(playerZPos + 64, 128);

                    if (mapTrackingData == null || mapTrackingData.mapId != mapId) {

                        if (invalidatedMapIds.contains(mapId)) {
                            mapTrackingData = new MapTrackingData(
                                mapId, mapGridX, mapGridZ,
                                playerGridX, playerGridZ,
                                mapCenterX, mapCenterZ,
                                currentSlot, false // not created at center - it's junk
                            );
                            mapTrackingData.isInvalidated = true;
                            return;
                        }

                        if (exportedMapIds.contains(mapId)) {
                            mapTrackingData = new MapTrackingData(
                                mapId, mapGridX, mapGridZ,
                                playerGridX, playerGridZ,
                                mapCenterX, mapCenterZ,
                                currentSlot, false
                            );
                            mapTrackingData.alreadyExported = true;
                            return;
                        }

                        int gridCenterX = mapGridX * 128;
                        int gridCenterZ = mapGridZ * 128;
                        double exactPlayerX = client.player.getX();
                        double exactPlayerZ = client.player.getZ();
                        double distX = Math.abs(exactPlayerX - gridCenterX);
                        double distZ = Math.abs(exactPlayerZ - gridCenterZ);
                        boolean createdAtCenter = (distX <= 0.5 && distZ <= 0.5);

                        if (createdAtCenter && !modCreatedMapIds.contains(mapId)) {
                            modCreatedMapIds.add(mapId);

                            if (!colorRegeneratedMapIds.contains(mapId)) {
                                scheduleColorRegeneration(mapId, mapGridX, mapGridZ);
                            }
                        }

                        mapTrackingData = new MapTrackingData(
                            mapId, mapGridX, mapGridZ,
                            playerGridX, playerGridZ,
                            mapCenterX, mapCenterZ,
                            currentSlot,
                            createdAtCenter
                        );

                    } else {
                        mapTrackingData.playerGridX = playerGridX;
                        mapTrackingData.playerGridZ = playerGridZ;
                        mapTrackingData.mapCenterX = mapCenterX;
                        mapTrackingData.mapCenterZ = mapCenterZ;

                        if (!mapTrackingData.movementDetected && !mapTrackingData.isInvalidated) {
                            if (isMovementKeyPressed(client)) {
                                mapTrackingData.movementDetected = true;
                                mapTrackingData.isInvalidated = true;
                                invalidatedMapIds.add(mapId);  // Permanently invalidate this map
                                if (client.player != null) {
                                    client.player.sendMessage(
                                        net.minecraft.text.Text.literal("§c§l[!] Map INVALIDATED - you moved! This map cannot be exported."),
                                        false
                                    );
                                }
                            }
                        }

                        if (currentSlot != mapTrackingData.originalSlot && !mapTrackingData.slotChanged && !mapTrackingData.isInvalidated) {
                            mapTrackingData.slotChanged = true;
                            mapTrackingData.isInvalidated = true;
                            invalidatedMapIds.add(mapId);  // Permanently invalidate this map
                            if (client.player != null) {
                                client.player.sendMessage(
                                    net.minecraft.text.Text.literal("§c§l[!] Map INVALIDATED - slot changed! This map cannot be exported."),
                                    false
                                );
                            }
                        }
                    }
                } else {
                    mapTrackingData = null;
                }
            } else {
                mapTrackingData = null;
            }

            while (exportKeyBinding.wasPressed()) {
                handleExportKey(client);
            }
            while (pingKeyBinding.wasPressed()) {
                PingExporter.exportPing();
            }

            while (beaconToggleKey.wasPressed()) {
                GridBeacon.toggle();
            }

            while (hideUiKey.wasPressed()) {
                hideModUI = !hideModUI;
                if (client.player != null) {
                    String status = hideModUI ? "\u00A7c\u00A7lHIDDEN" : "\u00A7a\u00A7lVISIBLE";
                    client.player.sendMessage(
                        net.minecraft.text.Text.literal("\u00A77[MCMapper] UI: " + status),
                        true
                    );
                }
            }

            while (menuKeyBinding.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new MenuBarScreen());
                } else if (client.currentScreen instanceof MenuBarScreen) {
                    client.setScreen(null);
                }
            }
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (!hideModUI) {
                float tickDelta = context.tickCounter().getTickDelta(false);
                
                if (GridBeacon.isEnabled()) {
                    GridBeacon.render(context.matrixStack(), tickDelta);
                }
            }
        });
        
        WorldRenderEvents.LAST.register(context -> {
            if (!hideModUI) {
                float tickDelta = context.tickCounter().getTickDelta(false);
                
                VertexConsumerProvider vertexConsumers = context.consumers();
                if (vertexConsumers != null) {
                    TrackedPingRenderer.get().renderWorld(
                        context.matrixStack(),
                        vertexConsumers,
                        context.camera(), 
                        tickDelta
                    );
                }
            }
        });

        HudRenderCallback.EVENT.register((context, tickCounter) -> {
            if (!hideModUI) {
                TrackedPingRenderer.get().renderHUD(context, tickCounter.getTickDelta(false));
            }
        });

    }

    private void scheduleColorRegeneration(int mapId, int gridX, int gridZ) {
        pendingRegenMapId = mapId;
        pendingRegenGridX = gridX;
        pendingRegenGridZ = gridZ;
        pendingRegenTicksRemaining = 10; // Wait 10 ticks (0.5 seconds)
    }

    private void tickPendingColorRegeneration(MinecraftClient client) {
        if (pendingRegenTicksRemaining <= 0 || pendingRegenMapId == -1) {
            return;
        }

        pendingRegenTicksRemaining--;

        if (pendingRegenTicksRemaining == 0) {
            if (client.player != null && client.world != null) {
                ItemStack heldItem = client.player.getMainHandStack();

                if (heldItem.getItem() == Items.FILLED_MAP) {
                    int currentMapId = getMapId(heldItem);

                    if (currentMapId == pendingRegenMapId) {
                        MapState mapState = FilledMapItem.getMapState(heldItem, client.world);

                        if (mapState != null && MapRegenerator.hasMinimumRenderDistance()) {

                            boolean success = MapRegenerator.regenerateColors(mapState, pendingRegenGridX, pendingRegenGridZ);

                            if (success) {
                                colorRegeneratedMapIds.add(pendingRegenMapId);

                                client.player.sendMessage(
                                    net.minecraft.text.Text.literal("§a✓ Map colors updated"),
                                    true
                                );
                            } else {
                            }
                        }
                    } else {
                    }
                }
            }

            pendingRegenMapId = -1;
        }
    }

    private int getMapId(ItemStack mapStack) {
        try {
            MapIdComponent mapIdComponent = mapStack.get(DataComponentTypes.MAP_ID);
            if (mapIdComponent != null) {
                return mapIdComponent.id();
            }
        } catch (Exception e) {
        }
        return -1;
    }

    private boolean isMovementKeyPressed(MinecraftClient client) {
        long window = client.getWindow().getHandle();

        if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_W)) return true;
        if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_A)) return true;
        if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_S)) return true;
        if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_D)) return true;
        if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_SPACE)) return true;
        if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT)) return true;
        if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT)) return true;
        if (InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_CONTROL)) return true;

        return false;
    }

    private void handleExportKey(MinecraftClient client) {

        if (client.player == null || client.world == null) {
            return;
        }

        ItemStack heldItem = client.player.getMainHandStack();

        if (heldItem.getItem() != Items.FILLED_MAP) {
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§c✗ You must be holding a filled map!"),
                false
            );
            return;
        }

        if (mapTrackingData == null) {
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§c✗ Map data not ready. Hold the map for a moment."),
                false
            );
            return;
        }

        if (mapTrackingData.isInvalidated) {
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§c✗ This map is INVALIDATED and cannot be exported!"),
                false
            );
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§eYou moved or changed slots. Create a new map at grid center."),
                false
            );
            return;
        }

        if (mapTrackingData.alreadyExported) {
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§c✗ This map has ALREADY been exported!"),
                false
            );
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§eEach map can only be exported once."),
                false
            );
            return;
        }

        if (!modCreatedMapIds.contains(mapTrackingData.mapId)) {
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§c✗ This map was not created properly with MCMapper!"),
                false
            );
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§eStand at grid center and create a NEW map."),
                false
            );
            return;
        }

        if (!mapTrackingData.createdAtCenter) {
            int gridCenterX = mapTrackingData.mapGridX * 128;
            int gridCenterZ = mapTrackingData.mapGridZ * 128;
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§c✗ Map not created at grid center!"),
                false
            );
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§eGo to: " + gridCenterX + ", " + gridCenterZ),
                false
            );
            return;
        }

        if (!MapRegenerator.hasMinimumRenderDistance()) {
            int currentRenderDist = MapRegenerator.getRenderDistance();
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§c✗ Render distance too low! Current: " + currentRenderDist + ", Required: 5+"),
                false
            );
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§eIncrease render distance in Video Settings to export."),
                false
            );
            return;
        }

        MapState mapState = FilledMapItem.getMapState(heldItem, client.world);
        if (mapState != null) {
            client.player.sendMessage(
                net.minecraft.text.Text.literal("§7Regenerating map colors..."),
                true
            );

            boolean regenSuccess = MapRegenerator.regenerateColors(mapState, mapTrackingData.mapGridX, mapTrackingData.mapGridZ);
            if (regenSuccess) {
            } else {
            }
        }

        String playerName = client.player.getName().getString();

        boolean success = MapDataExporter.exportMapData(
            heldItem,
            mapTrackingData.mapGridX,
            mapTrackingData.mapGridZ,
            mapTrackingData.playerGridX,
            mapTrackingData.playerGridZ,
            playerName,
            mapTrackingData.originalSlot,
            mapTrackingData.originalSlot,
            mapTrackingData.createdAtCenter,
            mapTrackingData.mapId
        );

        if (success) {
            exportedMapIds.add(mapTrackingData.mapId);
            mapTrackingData = null;
        }

    }

    public static MapTrackingData getMapTrackingData() {
        return mapTrackingData;
    }

    public static boolean isUiHidden() {
        return hideModUI || PingExporter.suppressHudRendering;
    }

    public static class MapTrackingData {
        public final int mapId;           // Unique map ID
        public final int mapGridX;
        public final int mapGridZ;
        public int playerGridX;
        public int playerGridZ;
        public int mapCenterX;
        public int mapCenterZ;
        public final int originalSlot;
        public final boolean createdAtCenter;
        public boolean movementDetected;
        public boolean slotChanged;
        public boolean isInvalidated;     // Map is JUNK - can never export
        public boolean alreadyExported;   // Map was already exported

        public MapTrackingData(int mapId, int mapGridX, int mapGridZ, int playerGridX, int playerGridZ,
                              int mapCenterX, int mapCenterZ, int originalSlot, boolean createdAtCenter) {
            this.mapId = mapId;
            this.mapGridX = mapGridX;
            this.mapGridZ = mapGridZ;
            this.playerGridX = playerGridX;
            this.playerGridZ = playerGridZ;
            this.mapCenterX = mapCenterX;
            this.mapCenterZ = mapCenterZ;
            this.originalSlot = originalSlot;
            this.createdAtCenter = createdAtCenter;
            this.movementDetected = false;
            this.slotChanged = false;
            this.isInvalidated = false;
            this.alreadyExported = false;
        }
    }
}
