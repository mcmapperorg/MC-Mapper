package com.b2tmapper.client.ping;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class TrackedPingManager {
    
    private static TrackedPingManager INSTANCE;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_TRACKED = 20; // Maximum tracked pings
    
    private List<TrackedPing> trackedPings = new ArrayList<>();
    private File saveFile;
    
    private TrackedPingManager() {
        saveFile = new File(FabricLoader.getInstance().getConfigDir().toFile(), "mcmapper-tracked-pings.json");
        load();
    }
    
    public static TrackedPingManager get() {
        if (INSTANCE == null) {
            INSTANCE = new TrackedPingManager();
        }
        return INSTANCE;
    }
    
    public List<TrackedPing> getTrackedPings() {
        return new ArrayList<>(trackedPings);
    }
    
    public boolean isTracked(int pingId) {
        return trackedPings.stream().anyMatch(p -> p.id == pingId);
    }
    
    public boolean track(TrackedPing ping) {
        if (isTracked(ping.id)) {
            return false; // Already tracked
        }
        
        if (trackedPings.size() >= MAX_TRACKED) {
            return false; // At max capacity
        }
        
        trackedPings.add(ping);
        save();
        return true;
    }
    
    public boolean untrack(int pingId) {
        Optional<TrackedPing> found = trackedPings.stream().filter(p -> p.id == pingId).findFirst();
        if (found.isPresent()) {
            trackedPings.remove(found.get());
            save();
            return true;
        }
        return false;
    }
    
    public void untrackAll() {
        trackedPings.clear();
        save();
    }
    
    public int getTrackedCount() {
        return trackedPings.size();
    }
    
    public int getMaxTracked() {
        return MAX_TRACKED;
    }
    
    public void save() {
        try (FileWriter writer = new FileWriter(saveFile)) {
            GSON.toJson(trackedPings, writer);
        } catch (Exception e) {
        }
    }
    
    public void load() {
        if (!saveFile.exists()) {
            trackedPings = new ArrayList<>();
            return;
        }
        
        try (FileReader reader = new FileReader(saveFile)) {
            Type listType = new TypeToken<ArrayList<TrackedPing>>(){}.getType();
            List<TrackedPing> loaded = GSON.fromJson(reader, listType);
            if (loaded != null) {
                trackedPings = loaded;
            }
        } catch (Exception e) {
            trackedPings = new ArrayList<>();
        }
    }
}
