// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk.commands;

import com.chalwk.tcp.GameEventProcessor;
import com.chalwk.utils.BaseCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class HaloCommand extends BaseCommand {

    private final List<GameEventProcessor> processors;

    public HaloCommand(List<GameEventProcessor> processors) {
        this.processors = processors;
    }

    @Override
    public CommandData getCommandData() {
        OptionData commandOption = new OptionData(OptionType.STRING, "command",
                "Server command to execute (e.g., 'pl', 'map bloodgulch ctf')", true);

        if (processors.size() > 1) {
            OptionData serverOption = new OptionData(OptionType.STRING, "server", "Which Halo server to target", true);
            for (GameEventProcessor p : processors) {
                serverOption.addChoice(p.getServerName(), p.getServerName());
            }
            return Commands.slash("halo", "Execute a server command on the game server").addOptions(commandOption,
                    serverOption);
        } else {
            return Commands.slash("halo", "Execute a server command on the game server").addOptions(commandOption);
        }
    }

    @Override
    public String getDescription() {
        return "Execute a server command on the game server";
    }

    @Override
    protected void executeCommand(SlashCommandInteractionEvent event) {
        String command = Objects.requireNonNull(event.getOption("command")).getAsString();
        GameEventProcessor targetProcessor;

        if (processors.size() == 1) {
            targetProcessor = processors.get(0);
        } else {
            String serverName = Objects.requireNonNull(event.getOption("server")).getAsString();
            targetProcessor = processors.stream()
                    .filter(p -> p.getServerName().equals(serverName))
                    .findFirst()
                    .orElse(null);
            if (targetProcessor == null) {
                event.reply("Invalid server selection.").setEphemeral(true).queue();
                return;
            }
        }

        event.deferReply().queue();

        targetProcessor.executeSappCommand(command, 5000, TimeUnit.MILLISECONDS)
                .thenAccept(output -> {
                    if (output == null || output.trim().isEmpty()) {
                        event.getHook().sendMessage("✅ Command executed (no output)").queue();
                    } else {
                        String finalOutput = output.length() > 2000 ? output.substring(0, 1997) + "..." : output;
                        EmbedBuilder embed = new EmbedBuilder()
                                .setTitle("📟 Server Command: `" + command + "`")
                                .setDescription("```\n" + finalOutput + "\n```")
                                .setColor(Color.CYAN);
                        event.getHook().sendMessageEmbeds(embed.build()).queue();
                    }
                })
                .exceptionally(ex -> {
                    event.getHook().sendMessage("❌ Error executing command: " + ex.getMessage()).queue();
                    return null;
                });
    }
}