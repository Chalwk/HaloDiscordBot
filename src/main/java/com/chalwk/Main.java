// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk;

import com.chalwk.commands.GameStatus;
import com.chalwk.commands.SAPPCommand;
import com.chalwk.listeners.ChatForwardListener;
import com.chalwk.listeners.SlashCommandListener;
import com.chalwk.tcp.GameEventProcessor;
import com.chalwk.tcp.GameEventTcpServer;
import com.chalwk.utils.CommandManager;
import com.chalwk.utils.LoggerUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;

import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        Config config = new Config();
        String token = config.getDiscordBotToken();
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("HALO_DISCORD_BOT_TOKEN environment variable is not set.");
        }

        LoggerUtil.info("Starting HaloDiscordBot...");

        // fire up the JDA instance and wait for it to be ready
        JDA jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .setChunkingFilter(ChunkingFilter.NONE)
                .build();
        jda.awaitReady();

        List<GameEventProcessor> allProcessors = new ArrayList<>();
        List<Config.HaloServerConfig> servers = config.getHaloServers();

        // start a TCP server for each configured Halo server
        for (Config.HaloServerConfig serverConfig : servers) {
            GameEventTcpServer server = new GameEventTcpServer(jda, config, serverConfig.name(), serverConfig.port());
            server.start();
            allProcessors.add(server.getProcessor());
        }

        // set up slash commands and permissions
        CommandManager cmdManager = new CommandManager();
        cmdManager.register(new GameStatus(allProcessors));
        cmdManager.register(new SAPPCommand(allProcessors, config));
        cmdManager.loadPermissionsFromConfig("config.yml");

        // Register listeners: slash commands and chat forwarding
        jda.addEventListener(new SlashCommandListener(cmdManager));
        jda.addEventListener(new ChatForwardListener(config, allProcessors));

        jda.updateCommands().addCommands(cmdManager.getCommandDataList()).queue();

        LoggerUtil.info("\nHaloDiscordBot online. Listening on {} port(s).", servers.size());
    }
}