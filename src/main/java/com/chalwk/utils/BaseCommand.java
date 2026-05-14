// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk.utils;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public abstract class BaseCommand implements Command {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        executeCommand(event);
    }

    protected abstract void executeCommand(SlashCommandInteractionEvent event);
}