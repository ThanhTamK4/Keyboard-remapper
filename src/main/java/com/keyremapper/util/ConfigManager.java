package com.keyremapper.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.keyremapper.model.KeyMapping;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class ConfigManager {

    private static final String CONFIG_DIR =
            System.getProperty("user.home") + File.separator + ".keyremapper";
    private static final String CONFIG_FILE =
            CONFIG_DIR + File.separator + "mappings.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ConfigManager() {}

    public static List<KeyMapping> loadMappings() {
        try {
            Path path = Paths.get(CONFIG_FILE);
            if (Files.exists(path)) {
                String json = new String(Files.readAllBytes(path));
                Type listType = new TypeToken<List<KeyMapping>>() {}.getType();
                List<KeyMapping> mappings = GSON.fromJson(json, listType);
                return mappings != null ? new ArrayList<>(mappings) : new ArrayList<>();
            }
        } catch (IOException e) {
            System.err.println("Failed to load config: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    public static void saveMappings(List<KeyMapping> mappings) {
        try {
            Files.createDirectories(Paths.get(CONFIG_DIR));
            String json = GSON.toJson(mappings);
            Files.write(Paths.get(CONFIG_FILE), json.getBytes());
        } catch (IOException e) {
            System.err.println("Failed to save config: " + e.getMessage());
        }
    }
}
