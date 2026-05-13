// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk.utils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

// abstract class that implements Command, just to separate the execute boilerplate
public abstract class BaseCommand implements Command {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        executeCommand(event);
    }

    // subclasses just need to implement this method
    protected abstract void executeCommand(SlashCommandInteractionEvent event);
}