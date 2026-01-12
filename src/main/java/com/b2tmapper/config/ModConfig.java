package com.b2tmapper.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class ModConfig {
    private static ModConfig INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static File configFile;

    // UI Settings
    public MenuBarPosition menuBarPosition = MenuBarPosition.TOP;
    public UITheme uiTheme = UITheme.GREEN;

    // Auth
    public String authToken = null;
    public String linkedUsername = null;

    // Live View Settings
    public boolean liveViewEnabled = false;
    public int liveViewServerId = -1;
    public String liveViewServerName = null;

    // Map Streaming Settings
    public boolean streamingEnabled = false;
    public int streamingServerId = -1;
    public String streamingServerName = null;
    public String streamingServerAddress = null;
    
    // Safe Zone - blocks from 0,0 where streaming is disabled
    public int safeZoneRadius = 0; // 0 = disabled
    public boolean safeZoneEnabled = false;

    // Ping Settings
    public boolean showPingBeacons = true;
    public int maxActivePings = 5;

    public enum MenuBarPosition {
        TOP, LEFT, RIGHT
    }

    public enum UITheme {
        GREEN("Green", 
            0xD9225522, 0xD9337733, 0xFF33AA33, 0xD9226622,
            0xD93355AA, 0xFF5588FF, 0xD94466BB,
            0xD9113311, 0xFF88FF88),
        BLUE("Blue",
            0xD9223355, 0xD9334477, 0xFF3377AA, 0xD9224466,
            0xD9335588, 0xFF5588DD, 0xD9446699,
            0xD9112233, 0xFF88BBFF),
        RED("Red",
            0xD9552222, 0xD9773333, 0xFFAA3333, 0xD9662222,
            0xD9553355, 0xFFAA55AA, 0xD9664466,
            0xD9331111, 0xFFFF8888),
        DEFAULT("Minecraft",
            0xC0101010, 0xC0505050, 0xFF000000, 0xC0404040,
            0xC0606060, 0xFF000000, 0xC0707070,
            0xC0202020, 0xFFFFFFFF);

        public final String displayName;
        public final int bg, hover, border, button;
        public final int selectedBg, selectedBorder, selectedHover;
        public final int headerBg, sectionText;

        UITheme(String displayName, int bg, int hover, int border, int button,
                int selectedBg, int selectedBorder, int selectedHover,
                int headerBg, int sectionText) {
            this.displayName = displayName;
            this.bg = bg;
            this.hover = hover;
            this.border = border;
            this.button = button;
            this.selectedBg = selectedBg;
            this.selectedBorder = selectedBorder;
            this.selectedHover = selectedHover;
            this.headerBg = headerBg;
            this.sectionText = sectionText;
        }
    }

    public static ModConfig get() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "mcmapper.json");
        if (configFile.exists()) {
            try (FileReader reader = new FileReader(configFile)) {
                INSTANCE = GSON.fromJson(reader, ModConfig.class);
            } catch (Exception e) {
                INSTANCE = new ModConfig();
            }
        } else {
            INSTANCE = new ModConfig();
            save();
        }
    }

    public static void save() {
        if (INSTANCE == null) {
            INSTANCE = new ModConfig();
        }
        try {
            if (configFile == null) {
                configFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "mcmapper.json");
            }
            try (FileWriter writer = new FileWriter(configFile)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            // Silent fail
        }
    }
}
