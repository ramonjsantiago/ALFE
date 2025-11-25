package com.fileexplorer.ui;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * ConfigService
 *  - Loads & saves persistent config as JSON
 *  - Stores window size, last folder, favorites, etc.
 */
public class ConfigService {

    private final Path configPath;
    private final ObjectMapper mapper;
    private Map<String, Object> data = new HashMap<>();

    public ConfigService(Path configPath) {
        this.configPath = configPath;
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
        load();
    }

    public void load() {
        try {
            if (Files.exists(configPath)) {
                data = mapper.readValue(configPath.toFile(), Map.class);
            }
        } catch (Exception e) {
            data = new HashMap<>();
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            mapper.writeValue(configPath.toFile(), data);
        } catch (IOException ignored) {}
    }

    public Object get(String key) {
        return data.get(key);
    }

    public void set(String key, Object value) {
        data.put(key, value);
        save();
    }

    public Map<String, Object> all() {
        return Collections.unmodifiableMap(data);
    }
}
