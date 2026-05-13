// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk;

import org.yaml.snakeyaml.Yaml;

import java.awt.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Config {

    private final Map<String, Object> data;
    private final Map<String, EventEmbedConfig> eventEmbedConfigs = new HashMap<>();
    private final Map<String, String> eventChannels = new HashMap<>();

    public Config() {
        try (InputStream input = new FileInputStream("config.yml")) {
            Yaml yaml = new Yaml();
            data = yaml.load(input);
            loadEventChannels();
            loadEventEmbedConfigs();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config.yml", e);
        }
    }

    private void loadEventChannels() {
        Map<String, Object> channelsMap = (Map<String, Object>) data.get("EVENT_CHANNELS");
        if (channelsMap == null) return;
        for (Map.Entry<String, Object> entry : channelsMap.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (val instanceof String) {
                eventChannels.put(key, (String) val);
            }
        }
    }

    private void loadEventEmbedConfigs() {
        Map<String, Object> gameEvents = (Map<String, Object>) data.get("GAME_EVENTS");
        if (gameEvents == null) return;

        Map<String, Object> embeds = (Map<String, Object>) gameEvents.get("embeds");
        if (embeds == null) return;

        for (Map.Entry<String, Object> entry : embeds.entrySet()) {
            String eventType = entry.getKey();
            Map<String, Object> cfg = (Map<String, Object>) entry.getValue();

            boolean enabled = getBoolean(cfg, "enabled", true);
            String title = getString(cfg, "title", "Game Event");
            Color color = parseColor(getString(cfg, "color", null));
            String description = getString(cfg, "description", null);
            String channelKey = getString(cfg, "channel", null);

            List<EventEmbedConfig.FieldConfig> fields = null;
            Object fieldsObj = cfg.get("fields");
            if (fieldsObj instanceof List) {
                fields = ((List<Map<String, Object>>) fieldsObj).stream()
                        .map(fieldMap -> {
                            String name = getString(fieldMap, "name", "");
                            String value = getString(fieldMap, "value", "");
                            boolean inline = getBoolean(fieldMap, "inline", false);
                            return new EventEmbedConfig.FieldConfig(name, value, inline);
                        })
                        .collect(Collectors.toList());
            }

            Map<Integer, String> typeDescriptions = null;
            Object typeObj = cfg.get("type");
            if (typeObj instanceof Map<?, ?> typeMap) {
                typeDescriptions = new HashMap<>();
                for (Map.Entry<?, ?> typeEntry : typeMap.entrySet()) {
                    try {
                        int subtype = Integer.parseInt(typeEntry.getKey().toString());
                        String desc = typeEntry.getValue().toString();
                        typeDescriptions.put(subtype, desc);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }

            eventEmbedConfigs.put(eventType, new EventEmbedConfig(enabled, title, color, description, fields, typeDescriptions, channelKey));
        }
    }

    private String getString(Map<String, Object> map, String key, String defaultValue) {
        Object val = map.get(key);
        return val instanceof String ? (String) val : defaultValue;
    }

    private boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object val = map.get(key);
        return val instanceof Boolean ? (Boolean) val : defaultValue;
    }

    private Color parseColor(String colorStr) {
        if (colorStr == null) return null;
        try {
            java.lang.reflect.Field field = Color.class.getField(colorStr.toUpperCase());
            return (Color) field.get(null);
        } catch (Exception ignored) {
        }
        if (colorStr.startsWith("#")) {
            try {
                return Color.decode(colorStr);
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    public EventEmbedConfig getEventEmbedConfig(String eventType) {
        return eventEmbedConfigs.getOrDefault(eventType, null);
    }

    public String getEventChannelId(String channelKey) {
        if (channelKey == null) return null;
        return eventChannels.get(channelKey);
    }

    public int getTcpPort() {
        Object port = data.get("TCP_PORT");
        if (port instanceof Number) {
            return ((Number) port).intValue();
        }
        return 12345;
    }


    public String getDiscordBotToken() {
        return System.getenv("HALO_DISCORD_BOT_TOKEN");
    }
}