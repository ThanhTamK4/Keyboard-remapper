package com.keyremapper.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.keyremapper.model.KeyMapping;
import com.keyremapper.model.Profile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ProfileManager {

    private static final String CONFIG_DIR =
            System.getProperty("user.home") + File.separator + ".keyremapper";
    private static final String PROFILES_FILE =
            CONFIG_DIR + File.separator + "profiles.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final List<Profile> profiles = new ArrayList<>();
    private int activeIndex = 0;
    private long lastLoadedTimestamp = 0;

    public ProfileManager() {
        load();
        if (profiles.isEmpty()) {
            profiles.add(new Profile("Profile 1"));
        }
    }

    public List<Profile> getProfiles() { return profiles; }
    public Profile getActiveProfile() {
        if (activeIndex < 0 || activeIndex >= profiles.size()) activeIndex = 0;
        return profiles.get(activeIndex);
    }
    public int getActiveIndex() { return activeIndex; }

    public void setActiveIndex(int index) {
        if (index >= 0 && index < profiles.size()) {
            activeIndex = index;
        }
    }

    public Profile addProfile(String name) {
        Profile p = new Profile(name);
        profiles.add(p);
        save();
        return p;
    }

    public boolean deleteProfile(int index) {
        if (profiles.size() <= 1) return false;
        profiles.remove(index);
        if (activeIndex >= profiles.size()) {
            activeIndex = profiles.size() - 1;
        }
        save();
        return true;
    }

    public void renameProfile(int index, String newName) {
        if (index >= 0 && index < profiles.size()) {
            profiles.get(index).setName(newName);
            save();
        }
    }

    public void exportProfile(Profile profile, File file) throws IOException {
        String json = GSON.toJson(profile);
        Files.write(file.toPath(), json.getBytes());
    }

    public Profile importProfile(File file) throws IOException {
        String json = new String(Files.readAllBytes(file.toPath()));
        Profile profile = GSON.fromJson(json, Profile.class);
        profile.setId(UUID.randomUUID().toString());
        profiles.add(profile);
        save();
        return profile;
    }

    /** Converts a profile's mappings to a VK-code map for the keyboard hook.
     *  Disabled mappings are excluded — the hook should never see them. */
    public static Map<Integer, Integer> toVkMap(Profile profile) {
        Map<Integer, Integer> map = new HashMap<>();
        for (KeyMapping m : profile.getMappings()) {
            if (m.isEnabled()) {
                map.put(m.getFromKeyCode(), m.getToKeyCode());
            }
        }
        return map;
    }

    /** Converts a VK-code map back into a list of KeyMapping objects. */
    public static List<KeyMapping> fromVkMap(Map<Integer, Integer> map) {
        List<KeyMapping> list = new ArrayList<>();
        for (Map.Entry<Integer, Integer> e : map.entrySet()) {
            list.add(new KeyMapping(
                    e.getKey(), e.getValue(),
                    KeyUtils.getKeyLabel(e.getKey()),
                    KeyUtils.getKeyLabel(e.getValue())));
        }
        return list;
    }

    private static final java.util.concurrent.ExecutorService SAVE_EXECUTOR =
            java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "ProfileSaver");
                t.setDaemon(true);
                return t;
            });

    public void save() {
        ProfileData data = new ProfileData();
        data.profiles = new ArrayList<>(profiles);
        data.activeIndex = activeIndex;
        final String json = GSON.toJson(data);
        SAVE_EXECUTOR.execute(() -> {
            try {
                Files.createDirectories(Paths.get(CONFIG_DIR));
                Files.write(Paths.get(PROFILES_FILE), json.getBytes());
            } catch (IOException e) {
                System.err.println("Failed to save profiles: " + e.getMessage());
            }
        });
    }

    /** Synchronous save for shutdown. */
    public void saveSync() {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
            ProfileData data = new ProfileData();
            data.profiles = new ArrayList<>(profiles);
            data.activeIndex = activeIndex;
            Files.write(Paths.get(PROFILES_FILE), GSON.toJson(data).getBytes());
        } catch (IOException e) {
            System.err.println("Failed to save profiles: " + e.getMessage());
        }
    }

    /** Reloads profiles from disk if the file has been modified externally. */
    public boolean reloadIfChanged() {
        try {
            Path path = Paths.get(PROFILES_FILE);
            if (Files.exists(path)) {
                long modTime = Files.getLastModifiedTime(path).toMillis();
                if (modTime > lastLoadedTimestamp) {
                    profiles.clear();
                    load();
                    if (profiles.isEmpty()) {
                        profiles.add(new Profile("Profile 1"));
                    }
                    return true;
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to check profile file: " + e.getMessage());
        }
        return false;
    }

    private void load() {
        try {
            Path path = Paths.get(PROFILES_FILE);
            if (Files.exists(path)) {
                String json = new String(Files.readAllBytes(path));
                ProfileData data = GSON.fromJson(json, ProfileData.class);
                if (data != null && data.profiles != null) {
                    profiles.addAll(data.profiles);
                    activeIndex = Math.min(data.activeIndex, profiles.size() - 1);
                }
                lastLoadedTimestamp = Files.getLastModifiedTime(path).toMillis();
            }
        } catch (Exception e) {
            System.err.println("Failed to load profiles: " + e.getMessage());
        }
    }

    private static class ProfileData {
        List<Profile> profiles;
        int activeIndex;
    }
}
