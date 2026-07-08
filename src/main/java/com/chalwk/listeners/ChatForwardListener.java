// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk.listeners;

import com.chalwk.Config;
import com.chalwk.tcp.GameEventProcessor;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatForwardListener extends ListenerAdapter {

    private final Map<String, List<Config.HaloServerConfig>> channelToServers = new HashMap<>();
    private final Map<String, GameEventProcessor> processorByServerName;

    public ChatForwardListener(Config config, List<GameEventProcessor> processors) {
        processorByServerName = new HashMap<>();
        for (GameEventProcessor proc : processors) {
            processorByServerName.put(proc.getServerName(), proc);
        }

        // I only care about channels that have a mapping for whatever event_chat uses.
        for (Config.HaloServerConfig server : config.getHaloServers()) {
            for (String channelId : server.perServerChannels().values()) {
                channelToServers.computeIfAbsent(channelId, k -> new ArrayList<>()).add(server);
            }
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot())
            return;

        String channelId = event.getChannel().getId();
        List<Config.HaloServerConfig> targetServers = channelToServers.get(channelId);
        if (targetServers == null || targetServers.isEmpty())
            return;

        // String author = event.getAuthor().getName();
        String author;
        Member member = event.getMember();
        if (member != null) {
            author = member.getEffectiveName(); // guild nickname or username
        } else {
            author = event.getAuthor().getGlobalName();
            if (author == null)
                author = event.getAuthor().getName();
        }

        String content = event.getMessage().getContentDisplay();

        for (Config.HaloServerConfig server : targetServers) {
            GameEventProcessor processor = processorByServerName.get(server.name());
            if (processor != null) {
                processor.sendChatToGame(author, content);
            }
        }
    }
}