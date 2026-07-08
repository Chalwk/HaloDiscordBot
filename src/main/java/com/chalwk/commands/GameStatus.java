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
import java.util.Collection;

public class GameStatus extends BaseCommand {

    private final Collection<GameEventProcessor> processors;

    // keep track of all the game event processors for each server
    public GameStatus(Collection<GameEventProcessor> processors) {
        this.processors = processors;
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash("game_status", "Show game event status for all connected SAPP servers");
    }

    @Override
    public String getDescription() {
        return "Show game event status";
    }

    @Override
    protected void executeCommand(SlashCommandInteractionEvent event) {
        if (processors.isEmpty()) {
            event.reply("No SAPP servers are configured.").setEphemeral(true).queue();
            return;
        }

        StringBuilder description = new StringBuilder();
        // loop through each server and collect its stats
        for (GameEventProcessor proc : processors) {
            long events = proc.getTotalEventsProcessed();
            Instant last = proc.getLastEventTime();
            Instant start = proc.getStartTime();
            boolean hasClient = proc.hasConnectedClient();

            // if we never got an event, just say Never instead of showing null
            String lastStr = last != null ? last.toString() : "Never";
            String uptime = start != null ? formatDuration(Duration.between(start, Instant.now())) : "N/A";

            // build a little tree view for each server's info
            description.append(String.format("**%s (port %d)**\n", proc.getServerName(), proc.getServerPort()))
                    .append(String.format("└ Connected: %s\n", hasClient ? "✅ Yes" : "❌ No"))
                    .append(String.format("└ Events: %d\n", events))
                    .append(String.format("└ Last event: %s\n", lastStr))
                    .append(String.format("└ Uptime: %s\n\n", uptime));
        }

        EmbedBuilder embed = new EmbedBuilder(EmbedUtil.createEmbed("📡 Game Event Status", description.toString()));
        event.replyEmbeds(embed.build()).setEphemeral(true).queue();
    }

    // turns a Duration object into a nice HH:MM:SS string, because nobody wants raw
    // seconds!
    private String formatDuration(Duration d) {
        long seconds = d.getSeconds();
        long hours = seconds / 3600;
        long mins = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, mins, secs);
    }
}