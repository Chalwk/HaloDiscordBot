// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk.utils;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

public interface Command {
    CommandData getCommandData();

    String getDescription();

    void execute(SlashCommandInteractionEvent event);

    default Permission getRequiredPermission() {
        return null;
    }
}