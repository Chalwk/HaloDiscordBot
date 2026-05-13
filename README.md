# HaloDiscordBot

A Java application using [JDA](https://github.com/discord-jda/JDA) that connects Halo servers (SAPP/Phasor) to Discord,
forwarding in-game events and allowing remote server control via Discord slash commands.

---

## Features

- **Real-time event forwarding** - Chat, deaths, scores, joins, leaves, map starts/ends, admin logins, and more are sent
  as rich Discord embeds.
- **Bidirectional TCP communication** - The bot and the SAPP Lua script communicate over TCP using a lightweight text
  protocol.
- **Slash command support** - Execute server commands directly from Discord.
- **Configurable embeds** - Customize titles, colors, descriptions, and even per-subtype death messages
  via [config.yml](config.yml).
- **Automatic reconnection** - The Lua script automatically reconnects to the bot if the connection drops.
- **Permission system** - Restrict commands (e.g., `/game_status`, `/sapp`) to specific Discord permissions.

---

## How It Works

1. The Java bot opens a TCP server on a configurable port (default: 47652)
2. The SAPP server runs a Lua script ([discord.lua](discord.lua)) that connects to the bot.
3. In‑game events are packed into key‑value strings and sent to the bot, which formats them as Discord embeds.
4. Discord users can use `/sapp players` or `/sapp execute <command>`. The bot sends the command over the same TCP
   connection, and the Lua script executes it via SAPP's `execute_command_sequence`.

---

## Requirements

- **Java 17+**
- **SAPP (10.2.1)** or **Phasor V2**
- **LuaJitSocket** - The Lua script requires `ljsocket.lua`
  from [CapsAdmin/luajitsocket](https://github.com/CapsAdmin/luajitsocket).

---

## Installation

### 1. Place Files (on the same machine as the game server)

Place the following files in your **server's root directory** (where `sapp.dll` resides):

- `HaloDiscordBot.jar`
- `config.yml`
- `ljsocket.lua`

Place `discord.lua` in your server's `lua` folder.

### 2. Discord Bot Token

> [!IMPORTANT]
> The bot requires the `HALO_DISCORD_BOT_TOKEN` environment variable to be set permanently. Get it from
> the [Discord Developer Portal](https://discord.com/developers/applications/?utm_source=chatgpt.com).

**Windows:** Press the Windows key, type `environment`, select `Edit the system environment variables`, then
`Environment Variables`. Click `New`. Set the name to `HALO_DISCORD_BOT_TOKEN` and its value to the token you received
from the Discord Developer Portal.

**Linux:** Open a terminal and run:

```bash
echo 'export HALO_DISCORD_BOT_TOKEN="<your-token>"' >> ~/.bashrc
source ~/.bashrc
```

Replace `<your-token>` with the token you received from the Discord Developer Portal.

---

## [License](LICENSE)

Copyright (c) 2026 Jericho Crosby (Chalwk)