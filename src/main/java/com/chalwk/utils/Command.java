// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk.utils;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

// every slash command implements this interface
public interface Command {
    CommandData getCommandData();

    String getDescription();

    void execute(SlashCommandInteractionEvent event);

    // by default, commands have no special permission requirement
    default Permission getRequiredPermission() {
        return null;
    }
}