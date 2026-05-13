// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk.utils;

import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CommandManager {
    private final Map<String, Command> commands = new HashMap<>();
    private final Map<String, Permission> requiredPermissions = new HashMap<>();

    public void register(Command cmd) {
        commands.put(cmd.getCommandData().getName(), cmd);
    }

    public Command get(String name) {
        return commands.get(name);
    }

    public List<CommandData> getCommandDataList() {
        return commands.values().stream()
                .map(Command::getCommandData)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public void loadPermissionsFromConfig(String configPath) {
        try (FileInputStream fis = new FileInputStream(configPath)) {
            Yaml yaml = new Yaml();
            Map<String, Object> config = yaml.load(fis);
            Map<String, String> permsMap = (Map<String, String>) config.get("COMMAND_PERMISSIONS");
            if (permsMap == null) return;

            for (Map.Entry<String, String> entry : permsMap.entrySet()) {
                String cmdName = entry.getKey();
                String permName = entry.getValue();
                if (permName != null && !permName.isEmpty()) {
                    try {
                        Permission perm = Permission.valueOf(permName.toUpperCase());
                        requiredPermissions.put(cmdName, perm);
                    } catch (IllegalArgumentException e) {
                        LoggerUtil.error("Invalid permission '{}' for command {}", permName, cmdName);
                    }
                }
            }
        } catch (FileNotFoundException e) {
            LoggerUtil.error("Config file not found: {}", configPath);
        } catch (Exception e) {
            LoggerUtil.error("Failed to load permissions from config", e);
        }
    }

    public Permission getRequiredPermission(String commandName) {
        return requiredPermissions.get(commandName);
    }
}