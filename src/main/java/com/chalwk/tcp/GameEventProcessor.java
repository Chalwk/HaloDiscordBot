// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk.tcp;

import com.chalwk.Config;
import com.chalwk.EventEmbedConfig;
import com.chalwk.utils.LoggerUtil;
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

    // matches {key} placeholders in embed templates
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{(\\w+)}");

    private final JDA jda;
    private final Config config;
    private final String serverName;
    private final int serverPort;

    // stats tracking - using atomic because multiple threads might access these
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

    // Takes a raw line from the TCP stream, parses and sends a Discord embed
    public void processEvent(String rawLine) {
        totalEventsProcessed.incrementAndGet();
        lastEventTime.set(Instant.now());

        // format: eventType|key1=value1|key2=value2|...
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
        if (embed == null) return; // disabled or no config found

        // figure out which Discord channel this event should go to
        EventEmbedConfig embedConfig = config.getEventEmbedConfig(eventType);
        String channelId = null;
        if (embedConfig != null && embedConfig.channelKey() != null) {
            channelId = config.getEventChannelId(serverName, embedConfig.channelKey());
        }
        if (channelId == null || channelId.isBlank()) {
            LoggerUtil.error("No destination channel configured for event '{}' on server '{}'.", eventType, serverName);
            return;
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            LoggerUtil.error("Invalid channel ID: {} for event {} on server {}", channelId, eventType, serverName);
            return;
        }

        // send the embed to discord
        channel.sendMessageEmbeds(embed.build()).queue();
    }

    // takes the event type and data map, and builds an embed based on config.yml rules
    private EmbedBuilder buildEmbedFromConfig(String eventType, Map<String, String> data) {
        EventEmbedConfig embedConfig = config.getEventEmbedConfig(eventType);
        if (embedConfig == null || !embedConfig.enabled()) {
            return null;
        }

        String title = replacePlaceholders(embedConfig.title(), data);
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(title);
        eb.setColor(embedConfig.color());

        // type mapping: maps integer subtype (for event_death and event_score)
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

        // either use explicit fields, or fall back to description, or just dump the raw data
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

    // replaces {key} placeholders in a template string with values from the data map
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

    // unescapes common sequences like \| \n \r that might come from discord.lua
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