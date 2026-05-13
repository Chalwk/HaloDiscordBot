# HaloDiscordBot

A Java application using [JDA](https://github.com/discord-jda/JDA) that connects Halo servers (SAPP/Phasor) to Discord,
forwarding in-game events as rich Discord embeds.

---

## Features

- **Real-time event forwarding** - Chat, deaths, scores, joins, leaves, map starts/ends, admin logins, and more.
- **Configurable embeds** - Custom titles, colors, descriptions, and even per-subtype death/score messages.
- **TCP communication** - Lightweight text protocol over a configurable port.
- **Automatic reconnection** - The Lua script automatically reconnects if the connection drops.
- **Slash command** - `/game_status` shows bot health and event statistics.

---

## How It Works

1. The Java bot opens a TCP server on a configurable port (default: `47652`).
2. The SAPP server runs a Lua script (`discord.lua`) that connects to the bot.
3. In-game events are packed into key-value strings and sent to the bot, which formats them as Discord embeds.
4. Discord users can use `/game_status` to check the bot's connection and event count.

> [!NOTE]
> This bot is currently **one-way** (game → Discord). It does **not** yet send commands back to the game server.

---

## Requirements

- **Java 17+**
- **SAPP (10.2.1)** or **Phasor V2**
- **LuaJIT** with a TCP library supporting Lua 5.1 (
  e.g., [CapsAdmin/luajitsocket](https://github.com/CapsAdmin/luajitsocket) or any compatible alternative)

---

## Installation

### 1. Place Files (on the same machine as the game server)

Put the following in your server's root directory (where `sapp.dll` resides):

- `HaloDiscordBot.jar`
- `config.yml`
- `ljsocket.lua` (or your chosen TCP library)

Place `discord.lua` in your server's `lua` folder.

### 2. Discord Bot Token

> [!IMPORTANT]
> The bot requires the environment variable `HALO_DISCORD_BOT_TOKEN`.  
> Get a token from the [Discord Developer Portal](https://discord.com/developers/applications/).

- **Windows:**  
  Press Windows key, type `environment`, select *Edit the system environment variables* → *Environment Variables* →
  *New*.  
  Name: `HALO_DISCORD_BOT_TOKEN`, Value: `your-token-here`.

- **Linux:**  
  Add to `~/.bashrc`:
  ```bash
  export HALO_DISCORD_BOT_TOKEN="your-token-here"
  source ~/.bashrc
  ```

### 3. Discord Intents & Bot Invite

In the Discord Developer Portal:

- Enable **Privileged Gateway Intents**:
    - `MESSAGE CONTENT` (required for reading commands)
    - `GUILD_MESSAGES`
- Generate an invite URL with the following permissions:  
  `Send Messages`, `Embed Links`, `Use Slash Commands`  
  Scope: `bot` + `applications.commands`

Invite the bot to your server.

### 4. Configure Channels

Find your Discord channel IDs (enable Developer Mode in Discord → right-click channel → *Copy ID*).  
Edit `config.yml`:

```yaml
EVENT_CHANNELS:
  general: "123456789012345678"
  admin: "876543210987654321"
```

Then reference these keys in each event's `channel:` field.

---

## Running the Bot

From the server root directory (where `config.yml` is located):

```bash
java -jar HaloDiscordBot.jar
```

The bot will:

- Connect to Discord
- Start a TCP server on the port defined in `config.yml` (default `47652`)
- Load slash commands automatically

Leave the terminal open. The bot must be **running before** the game server loads `discord.lua`.

---

## Lua Script Configuration

At the top of `discord.lua` you can adjust:

```lua
local host = "127.0.0.1"       -- bot's IP (same machine)
local port = 47652             -- must match TCP_PORT in config.yml
local auto_connect = true      -- automatically connect on script load
local reconnect_interval = 5   -- seconds between reconnection attempts
local max_queue_size = 200     -- max queued messages if disconnected
```

The script automatically reconnects if the connection drops.

---

## Protocol Specification

Events are sent as **plain text lines** with the format:

```
event_type|key1=value1|key2=value2|...|timestamp=unix_seconds
```

- Special characters are escaped: `|` → `\|`, newline → `\n`, carriage return → `\r`.
- Example:  
  `event_chat|name=Player1|msg=Hello world|timestamp=1700000000`

The bot parses these lines and builds Discord embeds according to `config.yml`.

---

## Configuration File (`config.yml`)

### `TCP_PORT`

Port the bot listens on. Must match the port in `discord.lua`.

### `EVENT_CHANNELS`

Maps a short name (e.g., `general`, `admin`) to a Discord channel ID.

### `COMMAND_PERMISSIONS`

Maps a slash command name to a Discord permission (e.g., `ADMINISTRATOR`).  
Only `/game_status` exists; you can add others if you extend the bot.

### `GAME_EVENTS.embeds`

Each event type (`event_chat`, `event_death`, …) can have:

| Field         | Description                                                              |
|---------------|--------------------------------------------------------------------------|
| `enabled`     | `true` or `false`                                                        |
| `channel`     | Key from `EVENT_CHANNELS`                                                |
| `title`       | Embed title (supports placeholders like `{name}`)                        |
| `color`       | Discord colour name (`RED`, `BLUE`, etc.) or hex (`#FFAA00`)             |
| `description` | Embed description (supports placeholders)                                |
| `type`        | Map of subtype numbers to custom descriptions (used for deaths / scores) |
| `fields`      | List of `{ name, value, inline }` objects for advanced embeds            |

#### Placeholders

Use `{key}` to insert values from the event data. Common keys:  
`name`, `msg`, `map`, `gt`, `ffa`, `team`, `score`, `killer_name`, `victim_name`, `total`, `lvl`, `cmd`, etc.

#### Subtype-specific messages (e.g., deaths)

```yaml
event_death:
  type:
    1: "**☠️ Death:** {killer_name} drew first blood on {victim_name}"
    4: "**☠️ Death:** {victim_name} was killed by {killer_name}"
    5: "**☠️ Death:** {victim_name} committed suicide"
```

#### Fields example

```yaml
event_score:
  fields:
    - name: "Player"
      value: "{name}"
      inline: true
    - name: "Team"
      value: "{team}"
      inline: true
```

If `fields` is present, the `description` field is ignored.

---

## Slash Commands

### `/game_status`

Shows (ephemeral, only visible to you):

- **TCP Client connected** - whether a game server is currently connected
- **Events processed** - total number of events received since bot start
- **Last event** - timestamp of the most recent event
- **Uptime** - how long the bot has been running

Requires `ADMINISTRATOR` permission by default (configurable in `COMMAND_PERMISSIONS`).

---

## Troubleshooting

| Issue                                 | Likely Fix                                                                                                                         |
|---------------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| Bot doesn't respond to `/game_status` | Re-invite the bot with `applications.commands` scope.                                                                              |
| No events appear in Discord           | Check that the game server can reach the bot's IP/port. Use `netstat` to verify the bot is listening.                              |
| `Connection refused` in game console  | Start the bot **before** loading `discord.lua`, or use `/discord reconnect` in-game.                                               |
| `HALO_DISCORD_BOT_TOKEN not set`      | Ensure the environment variable is set **before** running the jar. Restart your terminal.                                          |
| Embeds show `?` instead of values     | The Lua script isn't sending that key. Check the `discord.lua` `format_event` call.                                                |
| Lua script can't find `ljsocket`      | Place the `.lua` file in a directory where LuaJIT's `package.path` can find it, or modify `discord.lua` to `require("full.path")`. |

---

## License

Copyright (c) 2026 Jericho Crosby (Chalwk)  
Licensed under the MIT License - see the [LICENSE](LICENSE) file.