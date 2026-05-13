// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk.tcp;

import com.chalwk.Config;
import com.chalwk.EventEmbedConfig;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GameEventProcessor {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)}");

    private final JDA jda;
    private final Config config;
    private final String serverName;
    private final int serverPort;

    private final AtomicLong totalEventsProcessed = new AtomicLong(0);
    private final AtomicReference<Instant> lastEventTime = new AtomicReference<>(null);
    private final Instant startTime = Instant.now();
    private volatile boolean hasConnectedClient = false;

    public GameEventProcessor(JDA jda, Config config, String serverName, int serverPort) {
        this.jda = jda;
        this.config = config;
        this.serverName = serverName;
        this.serverPort = serverPort;
    }

    public void processEvent(String rawLine) {
        totalEventsProcessed.incrementAndGet();
        lastEventTime.set(Instant.now());

        String[] parts = rawLine.split("\\|");
        if (parts.length == 0) return;

        String eventType = parts[0];
        Map<String, String> data = new HashMap<>();
        for (int i = 1; i < parts.length; i++) {
            String p = parts[i];
            int eq = p.indexOf('=');
            if (eq > 0 && eq < p.length() - 1) {
                String key = p.substring(0, eq);
                String value = unescape(p.substring(eq + 1));
                data.put(key, value);
            }
        }

        EmbedBuilder embed = buildEmbedFromConfig(eventType, data);
        if (embed == null) return;

        EventEmbedConfig embedConfig = config.getEventEmbedConfig(eventType);
        String channelId = null;
        if (embedConfig != null && embedConfig.channelKey() != null) {
            // Use per‑server channel lookup
            channelId = config.getEventChannelId(serverName, embedConfig.channelKey());
        }
        if (channelId == null || channelId.isBlank()) {
            System.err.println("No destination channel configured for event '" + eventType +
                    "' on server '" + serverName + "' and no global fallback.");
            return;
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            System.err.println("Invalid channel ID: " + channelId + " for event " + eventType + " on server " + serverName);
            return;
        }

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    private EmbedBuilder buildEmbedFromConfig(String eventType, Map<String, String> data) {
        EventEmbedConfig embedConfig = config.getEventEmbedConfig(eventType);
        if (embedConfig == null || !embedConfig.enabled()) {
            return null;
        }

        String title = replacePlaceholders(embedConfig.title(), data);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        eb.setColor(embedConfig.color());

        String subtypeStr = data.get("subtype");
        String description = null;
        if (subtypeStr != null && embedConfig.typeDescriptions() != null) {
            try {
                int subtype = Integer.parseInt(subtypeStr);
                description = embedConfig.typeDescriptions().get(subtype);
            } catch (NumberFormatException ignored) {
            }
        }
        if (description == null) {
            description = embedConfig.description();
        }

        if (embedConfig.fields() != null && !embedConfig.fields().isEmpty()) {
            for (EventEmbedConfig.FieldConfig field : embedConfig.fields()) {
                String fieldName = replacePlaceholders(field.name(), data);
                String fieldValue = replacePlaceholders(field.value(), data);
                eb.addField(fieldName, fieldValue, field.inline());
            }
        } else if (description != null) {
            String replacedDesc = replacePlaceholders(description, data);
            eb.setDescription(replacedDesc);
        } else {
            eb.setDescription("```\n" + data + "\n```");
        }

        return eb;
    }

    private String replacePlaceholders(String template, Map<String, String> data) {
        if (template == null) return "";
        Matcher m = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String replacement = data.getOrDefault(key, "?");
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String unescape(String s) {
        if (s == null) return "";
        return s.replace("\\|", "|")
                .replace("\\n", "\n")
                .replace("\\r", "\r");
    }

    public void setHasConnectedClient(boolean has) {
        this.hasConnectedClient = has;
    }

    public long getTotalEventsProcessed() {
        return totalEventsProcessed.get();
    }

    public Instant getLastEventTime() {
        return lastEventTime.get();
    }

    public Instant getStartTime() {
        return startTime;
    }

    public boolean hasConnectedClient() {
        return hasConnectedClient;
    }

    public String getServerName() {
        return serverName;
    }

    public int getServerPort() {
        return serverPort;
    }
}