// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk.commands;

import com.chalwk.tcp.GameEventProcessor;
import com.chalwk.utils.BaseCommand;
import com.chalwk.utils.EmbedUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.time.Duration;
import java.time.Instant;

public class GameStatus extends BaseCommand {

    private final GameEventProcessor processor;

    public GameStatus(GameEventProcessor processor) {
        this.processor = processor;
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash("game_status", "Show game event forwarder status");
    }

    @Override
    public String getDescription() {
        return "Show game event forwarder status";
    }

    @Override
    protected void executeCommand(SlashCommandInteractionEvent event) {
        long events = processor.getTotalEventsProcessed();
        Instant last = processor.getLastEventTime();
        Instant start = processor.getStartTime();
        boolean hasClient = processor.hasConnectedClient();

        String lastStr = last != null ? last.toString() : "Never";
        String uptime = start != null ? formatDuration(Duration.between(start, Instant.now())) : "N/A";

        String desc = String.format("**TCP Client connected:** %s\n**Events processed:** %d\n**Last event:** %s\n**Uptime:** %s", hasClient ? "✅ Yes" : "❌ No", events, lastStr, uptime);

        EmbedBuilder embed = new EmbedBuilder(EmbedUtil.createEmbed("📡 Game Event Forwarder Status", desc));
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    private String formatDuration(Duration d) {
        long seconds = d.getSeconds();
        long hours = seconds / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, mins, secs);
    }
}