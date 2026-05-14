# HaloDiscordBot

A Java application using [JDA](https://github.com/discord-jda/JDA) that connects multiple Halo servers (SAPP/Phasor) to
Discord, forwarding in-game events as rich Discord embeds.

---

## Features

- **Real-time event forwarding** - Chat, deaths, scores, joins, leaves, map starts/ends, admin logins, and more.
- **Multiple server support** - One Discord bot can handle several Halo servers simultaneously, each on its own TCP
  port.
- **Per‑server Discord channels** - Each game server can send its events to different Discord channels, avoiding
  cross‑server spam.
- **Configurable embeds** - Custom titles, colours, descriptions, and even per-subtype death/score messages.
- **TCP communication** - Lightweight text protocol over configurable ports.
- **Automatic reconnection** - The Lua script automatically reconnects if the connection drops.
- **Slash command** - `/game_status` shows bot health and per-server event statistics.

---

## How It Works

1. The Java bot opens one or more TCP servers on the ports defined in `HALO_SERVERS` inside `config.yml`.
2. Each Halo server runs a Lua script (`discord.lua`) that connects to the bot on its dedicated port.
3. In-game events are packed into key-value strings and sent to the bot, which formats them as Discord embeds.
4. For each event, the bot first checks if the **source game server** defines its own channel mapping for that event
   type (via an optional `channels` block). If not, it falls back to the global `EVENT_CHANNELS`.
5. Discord users can use `/game_status` to check the connection and event count for **each** connected game server.

> [!NOTE]
> This bot is currently **one-way** (game → Discord). It does **not** yet send commands back to the game servers.

---

## Requirements

- **Java 17+**
- **SAPP (10.2.1)** or **Phasor V2**
- **LuaJIT Socket** supporting Lua 5.1 [CapsAdmin/luajitsocket](https://github.com/CapsAdmin/luajitsocket/blob/master/ljsocket.lua)

---

## Installation

### 1. Place Files (on the same machine as the game servers)

Put the following in your server's root directory (where `sapp.dll` resides):

- `HaloDiscordBot.jar`
- `config.yml`
- `ljsocket.lua`

Place `discord.lua` in your server's `lua` folder.  
For **multiple servers**, you can either:

- Copy `discord.lua` into each server's `lua` folder, or
- Use a shared location and modify each server's `sapp.ini` to point to the same script - but each server must connect
  on a **different port**.

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

---

## Running the Bot

From the server root directory (where `config.yml` is located):

```bash
java -jar HaloDiscordBot.jar
```

The bot will:

- Connect to Discord
- Start TCP servers on **all ports** defined in `HALO_SERVERS`
- Load slash commands automatically

Leave the terminal open. The bot must be **running before** each game server loads `discord.lua`.

---

## Lua Script Configuration

At the top of `discord.lua` you can adjust:

```lua
local host = "127.0.0.1"       -- bot's IP (same machine)
local port = 47652             -- must match one of the ports in HALO_SERVERS
local auto_connect = true      -- automatically connect on script load
local reconnect_interval = 5   -- seconds between reconnection attempts
local max_queue_size = 200     -- max queued messages if disconnected
```

If you run multiple Halo servers, give each server's `discord.lua` a **different port** (e.g., 47652, 47653, …) and
define those ports in the bot's `HALO_SERVERS` list.

The script automatically reconnects if the connection drops.

---

## Protocol Specification

Events are sent as **plain text lines** with the format:

```
event_type|key1=value1|key2=value2|...|timestamp=unix_seconds
```

- Special characters are escaped: `|` → `\|`, newline → `\n`, carriage return → `\r`.
- Example: `event_chat|name=Player1|msg=Hello world|timestamp=1700000000`

The bot parses these lines and builds Discord embeds according to `config.yml`.

---

## Configuration File (`config.yml`)

### `HALO_SERVERS`

List of game servers the bot should listen for. Each entry requires a unique `name` and `port`, and `channels` map.  

```yaml
HALO_SERVERS:
  - name: "Main Server"
    port: 47652
    channels:
      admin: "xxxxxxxxxxxxxxxxxxx"
      general: "xxxxxxxxxxxxxxxxxxx"
  - name: "Secondary Server"
    port: 47653
    channels:
      admin: "xxxxxxxxxxxxxxxxxxx"
      general: "xxxxxxxxxxxxxxxxxxx"
```

### `EVENT_CHANNELS`

Maps a short name (e.g., `general`, `admin`) to a Discord channel ID. These are used as the global fallback.

### `COMMAND_PERMISSIONS`

Maps a slash command name to a Discord permission (e.g., `ADMINISTRATOR`).  
Only `/game_status` exists; you can add others if you extend the bot.

### `GAME_EVENTS.embeds`

Each event type (`event_chat`, `event_death`, …) can have:

| Field         | Description                                                              |
|---------------|--------------------------------------------------------------------------|
| `enabled`     | `true` or `false`                                                        |
| `channel`     | Key from `EVENT_CHANNELS` (or a server’s per‑server `channels`)          |
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

Shows (ephemeral, only visible to you) a summary for **each** configured game server:

- **Server name and port**
- **TCP Client connected** - whether a game server is currently connected
- **Events processed** - total events received from that server since bot start
- **Last event** - timestamp of the most recent event from that server
- **Uptime** - how long that server's connection processor has been running

Requires `ADMINISTRATOR` permission by default (configurable in `COMMAND_PERMISSIONS`).

---

## License

Copyright (c) 2026 Jericho Crosby (Chalwk)  
Licensed under the MIT License - see the [LICENSE](LICENSE) file.