# HaloDiscordBot

A Java application using [JDA](https://github.com/discord-jda/JDA) that connects multiple Halo servers to Discord,
forwarding in-game events as rich Discord embeds. Supports SAPP only.

## Table of Contents

* [Features](#features)
* [Requirements](#requirements)
* [Important Notice](#important-notice)
* [Download](#download)
* [Installation](#installation)
    * [1. Place Files](#1-place-files)
    * [2. Discord Setup](#2-discord-setup)
    * [3. Running the Bot](#3-running-the-bot)
* [Lua Script Configuration](#lua-script-configuration)
* [Configuration File (`config.yml`)](#configuration-file-configyml)
    * [`HALO_SERVERS`](#halo_servers)
    * [`COMMAND_PERMISSIONS`](#command_permissions)
    * [`GAME_EVENTS.embeds`](#game_eventsembeds)
        * [Placeholders](#placeholders)
        * [Examples](#examples)
* [Slash Commands](#slash-commands)
    * [`/game_status`](#game_status)
    * [`/halo`](#halo)
* [Protocol Specification](#protocol-specification)
* [License](#license)

---

## Features

- Real-time event notifications: chat, deaths, scores, joins, leaves, map starts/ends, admin logins, and more.
- **Bidirectional chat**: Send messages from Discord to in-game chat (channel-to-server mapping).
- **Execute server commands from Discord**: Run any SAPP command and see the output directly in Discord.
- Multiple server support: one Discord bot can handle several Halo servers simultaneously, each on its own TCP port.
- Per-server Discord channels: each game server can send its events to different Discord channels.
- Configurable embeds: custom titles, colors, descriptions, and per-subtype death/score messages.
- **Secure remote hosting**: Run the bot on a different machine than your game servers with built‑in authentication and
  IP whitelisting.
- TCP communication with automatic reconnection from the Lua script.
- Slash commands:
    - `/game_status` - shows bot health and per-server event statistics.
    - `/halo` - execute a console command on any connected server.

---

## Requirements

- Java 17+
- SAPP (10.2.1) with Lua API version "1.11.0.0+"
- LuaJIT Socket (`ljsocket.lua`) supporting Lua
  5.1 ([CapsAdmin/luajitsocket](https://github.com/CapsAdmin/luajitsocket/blob/master/ljsocket.lua))

---

## Important Notice

> **The bot can now run on a separate machine from your Halo game servers.**  
> Use the `bind_address`, `secret_key`, and `allowed_ips` settings (see [Configuration](#configuration-file-configyml))
> to securely accept remote connections.

### Port Forwarding (when bot and game server are on different machines)

If your Java bot runs on a different machine (e.g., your home PC) and the Halo SAPP server is on a remote VPS or another
network, you must:

- **Forward the TCP port** (e.g., `47652`) from your bot machine's router to the bot machine's local IP address.
- **Add the game server's public IP** to the `allowed_ips` list in `config.yml` for that server's entry.
- Set `bind_address: "0.0.0.0"` in `config.yml` so the bot listens on all network interfaces.
- In the Lua script (`sapp_discord.lua`), set `host` to your bot machine's **public IP**.

Without port forwarding, the remote game server cannot establish a TCP connection to the bot.

---

## Download

The latest stable version is packaged as a zip file containing:

- `HaloDiscordBot.jar` - the main bot application
- `config.yml` - configuration file (edit before running)
- `run.bat` - Windows launcher script (optional)
- `run.sh` - Linux/macOS launcher script (optional)
- `sapp_discord.lua` - Lua script for SAPP servers

[![Download Latest Release](https://img.shields.io/badge/Download-Latest%20Release-brightgreen?logo=github&logoColor=white)](https://github.com/Chalwk/HaloDiscordBot/releases/latest)

**How to download:**

1. Click the badge above to go to the Releases page.
2. Look for the latest release (e.g., `v1.0.2`).
3. Under Assets, click `HaloDiscordBot.zip` to download.
4. Extract the zip - you will get all five files.

---

## Installation

### 1. Place Files

Copy the following files to your **bot machine** (this can be the same as the game server or a different one):

- `HaloDiscordBot.jar`
- `config.yml`

Place `sapp_discord.lua` inside **each Halo server's** `lua` folder.

For multiple servers, place the Lua script in each server's script folder and use a unique port per server (configured
in both the Lua script and `config.yml`).

> [!IMPORTANT]
> The SAPP Lua script requires `ljsocket.lua`
> from [CapsAdmin/luajitsocket](https://github.com/CapsAdmin/luajitsocket/blob/master/ljsocket.lua). Place it in your
> game server's root directory.

### 2. Discord Setup

> [!IMPORTANT]
> The bot requires the environment variable `HALO_DISCORD_BOT_TOKEN`. Get a token from
> the [Discord Developer Portal](https://discord.com/developers/applications/).

- **Windows:** Press Windows key, type `environment`, select *Edit the system environment variables* → *Environment
  Variables* → *New*. Name: `HALO_DISCORD_BOT_TOKEN`, Value: `your-token-here`.
- **Linux/macOS:** Add to `~/.bashrc` (or `~/.zshrc`):  
  `export HALO_DISCORD_BOT_TOKEN="your-token-here"`  
  then run `source ~/.bashrc`.

In the Discord Developer Portal:

- Enable Privileged Gateway Intents: `MESSAGE CONTENT` (required) and `GUILD_MESSAGES`.
- Generate an invite URL with permissions: `Send Messages`, `Embed Links`, `Use Slash Commands`. Scope: `bot` +
  `applications.commands`.

Invite the bot to your server.

### 3. Running the Bot

From the directory where `config.yml` is located (on the bot machine):

```bash
java -jar HaloDiscordBot.jar
```

**Alternatively, use the provided launcher scripts:**

- **Windows:** double-click `run.bat`. The batch file simplifies the process and keeps the terminal window open after
  the bot stops.
- **Linux/macOS:** open a terminal, make the script executable, then run it:
  ```bash
  chmod +x run.sh
  ./run.sh
  ```

The bot will:

- Connect to Discord.
- Start TCP servers on all ports defined in `HALO_SERVERS` inside `config.yml`.
- Load slash commands automatically.

---

## Lua Script Configuration

At the top of `sapp_discord.lua` you can adjust:

```lua
local host = "127.0.0.1"     -- Bot's IP address. Use the bot machine's public/private IP if remote.
local port = 47652           -- Bot port, must match a port in config.yml
local secret_key = "your-very-secret-key"  -- Must match the secret_key in config.yml
local auto_connect = true    -- automatically connect on script load
local reconnect_interval = 5 -- seconds between reconnection attempts
local max_queue_size = 200   -- maximum message queue size
```

- `host`: If the bot runs on a different machine, set this to the bot's IP address (e.g., `"192.168.1.10"` or a public
  IP).
- `secret_key`: **Required for remote connections**. Must be identical to the `secret_key` defined for the corresponding
  server in `config.yml`.

If you run multiple Halo servers, give each server's Lua script a different port (e.g., 47652, 47653, ...) and define
those ports in the bot's `HALO_SERVERS` list.

The script automatically reconnects if the connection drops and sends an authentication handshake (`AUTH|<secret>`)
immediately after connecting.

---

## Configuration File (`config.yml`)

This file controls which game servers the bot listens to, what permissions commands need, and how each game event
appears on Discord.

### `HALO_SERVERS`

A list of game servers the bot should accept connections from. Each server entry has:

| Field          | Description                                                                                                                                              |
|----------------|----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `name`         | A name used in logs and the `/game_status` command.                                                                                                      |
| `port`         | The TCP port this server will connect to (must match the port in its Lua script configuration).                                                          |
| `bind_address` | (Optional) IP address the bot listens on. Default `"127.0.0.1"`. Use `"0.0.0.0"` to accept connections from any network interface.                       |
| `secret_key`   | **(Required for remote connections)** A shared secret that the Lua script must send. Keep this secure.                                                   |
| `allowed_ips`  | (Optional) List of IP addresses or CIDR ranges (e.g., `"203.0.113.0/24"`) allowed to connect. If empty, any IP can connect (but still needs the secret). |
| `channels`     | A mapping of *channel keys* (like `general`, `admin`) to actual Discord channel IDs. These keys are referenced later in `GAME_EVENTS.embeds`.            |

**Example with two servers, one local and one remote:**

```yaml
HALO_SERVERS:
  - name: "Local Server"
    port: 47652
    bind_address: "127.0.0.1"
    secret_key: ""                     # No secret required for localhost
    allowed_ips: [ ]                   # Not needed
    channels:
      general: "1264839502716843950"
      admin: "9081743625517409283"

  - name: "Remote VPS Server"
    port: 47653
    bind_address: "0.0.0.0"           # Listen on all interfaces
    secret_key: "super-secret-123"    # Must match the key in sapp_discord.lua
    allowed_ips:
      - "203.0.113.45"                # Your VPS public IP
      - "192.168.1.0/24"              # Optional: allow local subnet
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
  halo: "ADMINISTRATOR"
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
- `{ffa}` - "true" or "false" for free-for-all
- `{team}` - team name (Red, Blue, etc.)
- `{score}` - player's score
- `{killer_name}` - name of the killer
- `{victim_name}` - name of the victim
- `{total}` - current player count
- `{lvl}` - admin level of a logged-in admin
- `{cmd}` - command string executed

#### Examples

Click on each example to expand.

<details>
<summary><strong>Example 1: Chat event</strong></summary>

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

</details>

<details>
<summary><strong>Example 2: Death event with subtypes</strong></summary>

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

</details>

<details>
<summary><strong>Example 3: Score event using fields</strong></summary>

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

</details>

#### Linking an event to a channel key

The `channel` value (e.g., `"general"`) must match one of the keys inside a server's `channels` mapping in
`HALO_SERVERS`. The bot looks at the server that sent the event, finds that key in its own `channels`, and sends the
embed to that Discord channel ID.

If a server does not define that channel key, the event is dropped. This allows different servers to send the same event
type to different Discord channels.

---

## Slash Commands

The bot provides two slash commands for server management and monitoring. All commands support Discord's permission
system, configurable via `COMMAND_PERMISSIONS` in `config.yml`.

### `/game_status`

Shows an ephemeral summary (only visible to you) for each configured game server:

- Server name and port
- TCP client connected – whether a game server is currently connected
- Events processed – total events received from that server since bot start
- Last event – timestamp of the most recent event from that server
- Uptime – how long that server's connection processor has been running

Requires `ADMINISTRATOR` permission by default (configurable).

---

### `/halo`

Executes any server command directly on a connected Halo server and returns the output as a Discord embed.

#### Parameters

| Parameter | Type   | Required | Description                                                                       |
|-----------|--------|----------|-----------------------------------------------------------------------------------|
| `command` | String | Yes      | The server command to run (e.g., `pl`, `map bloodgulch ctf`, `sv_kill`).          |
| `server`  | String | No*      | Which Halo server to target. *Only shown if you have multiple servers configured. |

\* When only one server is defined in `HALO_SERVERS`, the `server` option is omitted and the command is sent to that
single server automatically.

> **Note:** The command uses a timeout of 5 seconds. If the server produces a lot of output, the bot will wait up to
> 300ms between lines to capture everything.

#### Examples

<details>
<summary><strong>Click to expand command examples</strong></summary>

**List all players on the server**

```
/halo pl
```

**Change map and gametype**

```
/halo map bloodgulch ctf
```

**Targeting a specific server (multi-server setups)**  
When multiple servers are defined in `config.yml`, the `/halo` command includes an extra `server` dropdown. For example:

```
/halo server: "Main Server" command: sv_map_next
```

This runs `sv_map_next` only on the server named "Main Server".

</details>

---

## Protocol Specification

The bot uses a plain‑text TCP protocol. Events are sent as lines with the format:

```
event_type|key1=value1|key2=value2|...|timestamp=unix_seconds
```

Special characters are escaped: `|` → `\|`, newline → `\n`, carriage return → `\r`.

**Authentication handshake** (required if `secret_key` is set in `config.yml`):  
Immediately after connecting, the Lua script sends:

```
AUTH|<secret_key>\n
```

If the secret matches, the bot replies `AUTH_OK\n` and then accepts normal events. If authentication fails, the
connection is closed.

Example of a normal event line after authentication:  
`event_chat|name=Player1|msg=Hello world|timestamp=1700000000`

The bot parses these lines and builds Discord embeds according to `config.yml`.

> **Note:** `event_echo` is sent by SAPP and carries command output, used by the `/halo` command.

---

## License

Copyright (c) 2026 Jericho Crosby (Chalwk). See the [LICENSE](LICENSE) file for details.
