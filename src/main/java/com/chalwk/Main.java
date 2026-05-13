// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk;

import com.chalwk.commands.GameStatus;
import com.chalwk.listeners.SlashCommandListener;
import com.chalwk.tcp.GameEventTcpServer;
import com.chalwk.utils.CommandManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;

public class Main {
    public static void main(String[] args) throws Exception {
        Config config = new Config();
        String token = config.getDiscordBotToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("HALO_DISCORD_BOT_TOKEN environment variable is not set.");
        }

        JDA jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .setChunkingFilter(ChunkingFilter.NONE)
                .build();
        jda.awaitReady();

        GameEventTcpServer server = new GameEventTcpServer(jda, config);
        server.start();

        CommandManager cmdManager = new CommandManager();
        cmdManager.register(new GameStatus(server.getProcessor()));

        cmdManager.loadPermissionsFromConfig("config.yml");

        jda.addEventListener(new SlashCommandListener(cmdManager));
        jda.updateCommands().addCommands(cmdManager.getCommandDataList()).queue();

        System.out.println("HaloDiscordBot online. Listening on port " + config.getTcpPort());
    }
}