package com.fileexplorer.ui;

import java.io.*;
import java.nio.file.Path;
import java.util.Properties;

public class SettingsService {

    private final Properties props = new Properties();
    private final Path settingsFile;

    public SettingsService(Path file) {
        this.settingsFile = file;
        load();
    }

    private void load() {
        if (settingsFile != null && settingsFile.toFile().exists()) {
            try (InputStream is = new FileInputStream(settingsFile.toFile())) {
                props.load(is);
            } catch (IOException ignored) {}
        }
    }

    public void save() {
        if (settingsFile == null) return;
        try (OutputStream os = new FileOutputStream(settingsFile.toFile())) {
            props.store(os, "Explorer Settings");
        } catch (IOException ignored) {}
    }

    public String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public void set(String key, String value) {
        if (value == null) props.remove(key);
        else props.setProperty(key, value);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String v = props.getProperty(key);
        return v != null ? Boolean.parseBoolean(v) : defaultValue;
    }

    public void setBoolean(String key, boolean value) {
        props.setProperty(key, Boolean.toString(value));
    }
}
