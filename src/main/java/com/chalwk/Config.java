// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk;

import org.yaml.snakeyaml.Yaml;

import java.awt.*;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class Config {

    private final Map<String, Object> data;
    private final Map<String, EventEmbedConfig> eventEmbedConfigs = new HashMap<>();
    private final Map<String, HaloServerConfig> serverConfigs = new HashMap<>();

    public Config() {
        try (InputStream input = new FileInputStream("config.yml")) {
            Yaml yaml = new Yaml();
            data = yaml.load(input);
            loadEventEmbedConfigs();
            loadHaloServers();
        } catch (Exception e) {
            throw new RuntimeException("Failed to load config.yml", e);
        }
    }

    private void loadHaloServers() {
        Object serversObj = data.get("HALO_SERVERS");
        if (serversObj instanceof List<?>) {
            for (Object item : (List<?>) serversObj) {
                if (item instanceof Map<?, ?> map) {
                    String name = (String) map.get("name");
                    Object portObj = map.get("port");
                    int port = (portObj instanceof Number) ? ((Number) portObj).intValue() : 0;
                    if (port <= 0 || name == null || name.isBlank()) continue;

                    Map<String, String> perServerChannels = new HashMap<>();
                    Object channelsObj = map.get("channels");
                    if (channelsObj instanceof Map<?, ?>) {
                        for (Map.Entry<?, ?> entry : ((Map<?, ?>) channelsObj).entrySet()) {
                            String key = entry.getKey().toString();
                            Object val = entry.getValue();
                            if (val instanceof String) {
                                perServerChannels.put(key, (String) val);
                            }
                        }
                    }
                    serverConfigs.put(name, new HaloServerConfig(name, port, perServerChannels));
                }
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

    public String getEventChannelId(String serverName, String channelKey) {
        if (channelKey == null) return null;
        HaloServerConfig server = serverConfigs.get(serverName);
        if (server != null && server.perServerChannels().containsKey(channelKey)) {
            return server.perServerChannels().get(channelKey);
        }
        return null;
    }

    public List<HaloServerConfig> getHaloServers() {
        return new ArrayList<>(serverConfigs.values());
    }

    public String getDiscordBotToken() {
        return System.getenv("HALO_DISCORD_BOT_TOKEN");
    }

    public record HaloServerConfig(String name, int port, Map<String, String> perServerChannels) {}
}