// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk.listeners;

import com.chalwk.utils.Command;
import com.chalwk.utils.CommandManager;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class SlashCommandListener extends ListenerAdapter {
    private final CommandManager manager;

    public SlashCommandListener(CommandManager manager) {
        this.manager = manager;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String cmdName = event.getName();
        Command cmd = manager.get(cmdName);
        if (cmd == null) {
            event.reply("Unknown command.").setEphemeral(true).queue();
            return;
        }

        Permission requiredPerm = manager.getRequiredPermission(cmdName);
        if (requiredPerm != null) {
            Member member = event.getMember();
            if (member == null || !member.hasPermission(requiredPerm)) {
                event.reply("You need the `" + requiredPerm.getName() + "` permission to use this command.")
                        .setEphemeral(true).queue();
                return;
            }
        }

        cmd.execute(event);
    }
}