# HaloDiscordBot

A Java application using [JDA](https://github.com/discord-jda/JDA) that connects multiple Halo servers to Discord,
forwarding in-game events as rich Discord embeds. Supports SAPP and Phasor.

## Features

- Real-time event notifications: chat, deaths, scores, joins, leaves, map starts/ends, admin logins, and more.
- Multiple server support: one Discord bot can handle several Halo servers simultaneously, each on its own TCP port.
- Per-server Discord channels: each game server can send its events to different Discord channels.
- Configurable embeds: custom titles, colors, descriptions, and per-subtype death/score messages.
- TCP communication with automatic reconnection from the Lua script.
- Slash command: `/game_status` shows bot health and per-server event statistics.

---

## Requirements

- Java 17+
- SAPP (10.2.1) or Phasor V2
- LuaJIT Socket supporting Lua 5.1 (
  from [CapsAdmin/luajitsocket](https://github.com/CapsAdmin/luajitsocket/blob/master/ljsocket.lua))

---

## Download

The latest stable version is packaged as a zip file containing:

- `HaloDiscordBot.jar` - the main bot application
- `config.yml` - configuration file (edit before running)
- `discord.lua` - Lua script to place in SAPP's Lua folder

[![Download Latest Release](https://img.shields.io/badge/Download-Latest%20Release-brightgreen?logo=github&logoColor=white)](https://github.com/Chalwk/HaloDiscordBot/releases/latest)

**How to download:**

1. Click the badge above to go to the Releases page.
2. Look for the latest release (e.g., `v1.0.0`).
3. Under Assets, click `HaloDiscordBot.zip` to download.
4. Extract the zip - you will get all three files.

> Development builds (bleeding edge) are available from GitHub Actions artifacts, but the Releases page is recommended
> for most users.

---

## Installation

### 1. Place Files (on the same machine as the game servers)

From the downloaded zip, copy the following files to your Halo server's root directory (where `sapp.dll` resides):

- `HaloDiscordBot.jar`
- `config.yml`

Additionally, place `discord.lua` inside your server's `lua` folder.

For multiple servers, place `discord.lua` in each server's `lua` folder and use a unique port per server (configured in
both `discord.lua` and `config.yml`).

> [!IMPORTANT]
> The Lua script requires `ljsocket.lua`
> from [CapsAdmin/luajitsocket](https://github.com/CapsAdmin/luajitsocket/blob/master/ljsocket.lua). Place it in your
> server's root directory.

### 2. Discord Bot Token

> The bot requires the environment variable `HALO_DISCORD_BOT_TOKEN`. Get a token from
> the [Discord Developer Portal](https://discord.com/developers/applications/).

- **Windows:** Press Windows key, type `environment`, select *Edit the system environment variables* → *Environment
  Variables* → *New*. Name: `HALO_DISCORD_BOT_TOKEN`, Value: `your-token-here`.
- **Linux:** Add to `~/.bashrc`:  
  `export HALO_DISCORD_BOT_TOKEN="your-token-here"`  
  then run `source ~/.bashrc`.

### 3. Discord Intents & Bot Invite

In the Discord Developer Portal:

- Enable Privileged Gateway Intents: `MESSAGE CONTENT` (required) and `GUILD_MESSAGES`.
- Generate an invite URL with permissions: `Send Messages`, `Embed Links`, `Use Slash Commands`. Scope: `bot` +
  `applications.commands`.

Invite the bot to your server.

### 4. Running the Bot

From the server root directory (where `config.yml` is located):

```bash
java -jar HaloDiscordBot.jar
```

The bot will:

- Connect to Discord.
- Start TCP servers on all ports defined in `HALO_SERVERS` inside `config.yml`.
- Load slash commands automatically.

Leave the terminal open. The bot must be running before each game server loads `discord.lua`.

---

## Lua Script Configuration

At the top of `discord.lua` you can adjust:

```lua
local host = "127.0.0.1"     -- bot address (usually loopback)
local port = 47652           -- bot port, must match a port in config.yml
local auto_connect = true    -- automatically connect on script load
local reconnect_interval = 5 -- seconds between reconnection attempts
local max_queue_size = 200   -- maximum message queue size
```

If you run multiple Halo servers, give each server's `discord.lua` a different port (e.g., 47652, 47653, ...) and define
those ports in the bot's `HALO_SERVERS` list.

The script automatically reconnects if the connection drops.

---

## Protocol Specification

Events are sent as plain text lines with the format:

```
event_type|key1=value1|key2=value2|...|timestamp=unix_seconds
```

Special characters are escaped: `|` → `\|`, newline → `\n`, carriage return → `\r`.

Example:  
`event_chat|name=Player1|msg=Hello world|timestamp=1700000000`

The bot parses these lines and builds Discord embeds according to `config.yml`.

---

## Configuration File (`config.yml`)

This file controls which game servers the bot listens to, what permissions commands need, and how each game event
appears on Discord.

### `HALO_SERVERS`

A list of game servers the bot should accept connections from. Each server entry has:

- `name` - A name used in logs and the `/game_status` command.
- `port` - The TCP port this server will connect to (must match the port in its `discord.lua`).
- `channels` - A mapping of *channel keys* (like `general`, `admin`) to actual Discord channel IDs. These keys are
  referenced later in `GAME_EVENTS.embeds` to decide where each event type is sent.

Example with two servers:

```yaml
HALO_SERVERS:
  - name: "Main Server"
    port: 47652
    channels:
      general: "1264839502716843950"
      admin: "9081743625517409283"
  - name: "Secondary Server"
    port: 47653
    channels:
      general: "4739201864405713928"
      admin: "6813572049931847261"
```

You can add as many servers as you like. Each server can use the same or different Discord channels.

### `COMMAND_PERMISSIONS`

Maps a slash command name to a Discord permission string. Only users with that permission can use the command. If a
command is not listed, it is open to everyone.

Example:

```yaml
COMMAND_PERMISSIONS:
  game_status: "ADMINISTRATOR"
```

### `GAME_EVENTS.embeds`

Under `embeds`, you define how each game event type (like `event_chat`, `event_death`) looks on Discord. The name of
each block (e.g., `event_chat`) must match the event type sent by the Lua script.

Each event block can have these fields:

| Field         | Description                                                                                                                                                                            |
|---------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `enabled`     | `true` or `false`. If `false`, the event is ignored.                                                                                                                                   |
| `channel`     | A *channel key* that must exist in the server's `channels` mapping (inside `HALO_SERVERS`). The bot sends the embed to that Discord channel.                                           |
| `title`       | The embed's title. You can use placeholders like `{name}`.                                                                                                                             |
| `color`       | A Discord color name (e.g., `RED`, `BLUE`, `GREEN`, `MAGENTA`) or a hex code like `#FFAA00`.                                                                                           |
| `description` | The embed's main text. Supports placeholders. Ignored if `fields` is used.                                                                                                             |
| `type`        | A map of subtype numbers to custom descriptions. Used for events that have multiple variants, like `event_death` (different death types) or `event_score` (different scoring methods). |
| `fields`      | A list of embed fields. Each field has `name`, `value`, and `inline` (true/false). If you use `fields`, the `description` field is ignored.                                            |

#### Placeholders

You can use `{key}` in `title`, `description`, `type` messages, and field `name`/`value`. The key is replaced with the
actual value from the game event. Common keys:

- `{name}` - player name
- `{msg}` - chat message
- `{map}` - current map name
- `{gt}` - game type (e.g., CTF, Slayer)
- `{ffa}` - "yes" or "no" for free-for-all
- `{team}` - team name (Red, Blue, etc.)
- `{score}` - player's score
- `{killer_name}` - name of the killer
- `{victim_name}` - name of the victim
- `{total}` - current player count
- `{lvl}` - admin level of a logged-in admin
- `{cmd}` - command string executed

#### Example 1: Chat event

```yaml
event_chat:
  enabled: true
  channel: "general"
  title: " "
  color: "WHITE"
  description: "**💬 Chat** → `{name}`: *{msg}*"
```

This sends a white embed to the Discord channel that the server maps to the key `general`. The description shows the
player name and message.

#### Example 2: Death event with subtypes

```yaml
event_death:
  enabled: true
  channel: "general"
  title: " "
  color: "RED"
  type:
    1: "**☠️ Death:** {killer_name} drew first blood on {victim_name}"
    4: "**☠️ Death:** {victim_name} was killed by {killer_name}"
    5: "**☠️ Death:** {victim_name} committed suicide"
```

The Lua script sends a `subtype` number (e.g., 1, 4, 5). The bot picks the matching message. If a subtype is not listed,
the event uses the `description` field (or does nothing if `description` is missing).

#### Example 3: Score event using fields

```yaml
event_score:
  enabled: true
  channel: "general"
  title: "Score Update"
  color: "YELLOW"
  fields:
    - name: "Player"
      value: "{name}"
      inline: true
    - name: "Team"
      value: "{team}"
      inline: true
    - name: "Score"
      value: "{score}"
      inline: true
```

Instead of a single description, this creates three small inline fields inside the embed.

#### Linking an event to a channel key

The `channel` value (e.g., `"general"`) must match one of the keys inside a server's `channels` mapping in
`HALO_SERVERS`. The bot looks at the server that sent the event, finds that key in its own `channels`, and sends the
embed to that Discord channel ID.

If a server does not define that channel key, the event is dropped. This allows different servers to send the same event
type to different Discord channels.

---

## Slash Commands

### `/game_status`

Shows an ephemeral summary (only visible to you) for each configured game server:

- Server name and port
- TCP client connected - whether a game server is currently connected
- Events processed - total events received from that server since bot start
- Last event - timestamp of the most recent event from that server
- Uptime - how long that server's connection processor has been running

Requires `ADMINISTRATOR` permission by default (configurable in `COMMAND_PERMISSIONS`).

## License

Copyright (c) 2026 Jericho Crosby (Chalwk). See the [LICENSE](LICENSE) file for details.