--[[
=====================================================================================
SCRIPT NAME:      discord.lua
DESCRIPTION:      Logs Halo server events using LuaJitSocket TCP.

                  PREREQUISITES:
                  1. HaloDiscordBot needs to be installed and running.
                     https://github.com/Chalwk/HaloDiscordBot

                  2. LuaJIT Socket supporting Lua 5.1
                     https://github.com/CapsAdmin/luajitsocket/blob/master/ljsocket.lua

Copyright (c) 2026 Jericho Crosby (Chalwk)
=====================================================================================
]]

-- CONFIG START --
api_version = '1.12.0.0'

local host = "127.0.0.1"     -- bot address (usually loopback)
local port = 47652           -- bot port, must match a port in config.yml
local auto_connect = true    -- automatically connect on script load
local reconnect_interval = 5 -- seconds between reconnection attempts
local max_queue_size = 200   -- maximum message queue size
-- CONFIG END --

local char = string.char
local concat = table.concat
local pairs, ipairs = pairs, ipairs
local os_time = os.time
local pcall = pcall
local table_insert, table_remove = table.insert, table.remove
local tonumber, tostring = tonumber, tostring
local string_format = string.format
local type = type

local get_dynamic_player = get_dynamic_player
local get_var = get_var
local lookup_tag = lookup_tag
local player_present, player_alive = player_present, player_alive
local read_byte, read_dword = read_byte, read_dword

local distance_tag
local falling_tag
local ffa
local first_blood
local gametype
local gametype_base
local map
local mode
local players
local score_limit
local server_name

local COMMAND_TYPE = { [0] = "RCON", [1] = "CONSOLE", [2] = "CHAT", [3] = "UNKNOWN" }
local CHAT_TYPE = { [0] = "GLOBAL", [1] = "TEAM", [2] = "VEHICLE", [3] = "UNKNOWN" }
local GAMETYPE_MAP = { ctf = 1, race = 2, slayer = 4 }
local PIRATED_HASHES = {
    ['388e89e69b4cc08b3441f25959f74103'] = true,
    ['81f9c914b3402c2702a12dc1405247ee'] = true,
    ['c939c09426f69c4843ff75ae704bf426'] = true,
    ['13dbf72b3c21c5235c47e405dd6e092d'] = true,
    ['29a29f3659a221351ed3d6f8355b2200'] = true,
    ['d72b3f33bfb7266a8d0f13b37c62fddb'] = true,
    ['76b9b8db9ae6b6cacdd59770a18fc1d5'] = true,
    ['55d368354b5021e7dd5d3d1525a4ab82'] = true,
    ['d41d8cd98f00b204e9800998ecf8427e'] = true,
    ['c702226e783ea7e091c0bb44c2d0ec64'] = true,
    ['f443106bd82fd6f3c22ba2df7c5e4094'] = true,
    ['10440b462f6cbc3160c6280c2734f184'] = true,
    ['3d5cd27b3fa487b040043273fa00f51b'] = true,
    ['b661a51d4ccf44f5da2869b0055563cb'] = true,
    ['740da6bafb23c2fbdc5140b5d320edb1'] = true,
    ['7503dad2a08026fc4b6cfb32a940cfe0'] = true,
    ['4486253cba68da6786359e7ff2c7b467'] = true,
    ['f1d7c0018e1648d7d48f257dc35e9660'] = true,
    ['40da66d41e9c79172a84eef745739521'] = true,
    ['2863ab7e0e7371f9a6b3f0440c06c560'] = true,
    ['34146dc35d583f2b34693a83469fac2a'] = true,
    ['b315d022891afedf2e6bc7e5aaf2d357'] = true,
    ['63bf3d5a51b292cd0702135f6f566bd1'] = true,
    ['6891d0a75336a75f9d03bb5e51a53095'] = true,
    ['325a53c37324e4adb484d7a9c6741314'] = true,
    ['0e3c41078d06f7f502e4bb5bd886772a'] = true,
    ['fc65cda372eeb75fc1a2e7d19e91a86f'] = true,
    ['f35309a653ae6243dab90c203fa50000'] = true,
    ['50bbef5ebf4e0393016d129a545bd09d'] = true,
    ['a77ee0be91bd38a0635b65991bc4b686'] = true,
    ['3126fab3615a94119d5fe9eead1e88c1'] = true
}

local socket_lib
local sock = nil
local is_connected = false
local reconnect_timer_active = false
local message_queue = {}
local incoming_buffer = "" -- buffer for partial data

local function load_socket()
    local ok, mod = pcall(require, "ljsocket")
    if not ok then
        cprint("[HaloDiscordBot] LuaJitSocket failed to load: " .. tostring(mod))
        return false
    end
    socket_lib = mod
    return true
end

local function schedule_reconnect()
    if not auto_connect or reconnect_timer_active then return end
    reconnect_timer_active = true
    timer(reconnect_interval * 1000, "OnReconnect")
end

function OnReconnect()
    if is_connected or not auto_connect then
        reconnect_timer_active = false
        return false
    end

    cprint("[HaloDiscordBot] Attempting reconnection...")
    connect_to_bot(host, port)

    if not is_connected then return true end

    reconnect_timer_active = false
    return false
end

function connect_to_bot(target_host, target_port)
    if not socket_lib then
        cprint("[HaloDiscordBot] LuaJitSocket not loaded")
        return false
    end
    if sock then
        pcall(sock.close, sock)
        sock = nil
    end

    local s = socket_lib.create("inet", "stream", "tcp")
    if not s then
        cprint("[HaloDiscordBot] socket.create failed")
        return false
    end

    s:set_blocking(false)

    local res, err, num = s:connect(target_host, tostring(target_port))
    if not res then
        s:close()
        cprint("[HaloDiscordBot] Connection to " .. target_host .. ":" .. target_port .. " failed: " .. tostring(err))
        return false
    end

    if res == true then
        local poll_result, poll_err = s:poll(5000, "out")
        if not poll_result or not poll_result.out then
            s:close()
            cprint("[HaloDiscordBot] Connection to " .. target_host .. ":" .. target_port .. " timed out")
            return false
        end
    end

    s:set_blocking(true)
    s:set_option("sndtimeo", 1000)

    sock = s
    is_connected = true
    cprint("[HaloDiscordBot] Connected to " .. target_host .. ":" .. target_port)

    -- Start polling for incoming data (every second)
    timer(1000, "OnPollIncoming")

    if #message_queue > 0 then
        local count = 0
        for _, msg in ipairs(message_queue) do
            local bytes, _err = s:send(msg)
            if not bytes then
                cprint("[HaloDiscordBot] Queue send error: " .. tostring(_err))
                break
            end
            count = count + 1
        end

        -- Efficient in-place removal of sent messages
        if count > 0 then
            local q = message_queue
            local len = #q
            for i = 1, len - count do q[i] = q[i + count] end
            for i = len - count + 1, len do q[i] = nil end
        end
    end
    return true
end

local function disconnect()
    reconnect_timer_active = false
    if sock then
        pcall(sock.close, sock)
        sock = nil
    end
    is_connected = false
    cprint("[HaloDiscordBot] Disconnected")
end

local function send_data(data)
    if not is_connected then
        table_insert(message_queue, data)
        if #message_queue > max_queue_size then
            table_remove(message_queue, 1)
        end
        if auto_connect then schedule_reconnect() end
        return false
    end

    local bytes, err = sock:send(data)
    if not bytes then
        cprint("[HaloDiscordBot] Send error: " .. tostring(err))
        is_connected = false
        pcall(sock.close, sock)
        sock = nil
        table_insert(message_queue, data)
        if #message_queue > max_queue_size then
            table_remove(message_queue, 1)
        end
        if auto_connect then schedule_reconnect() end
        return false
    end
    return true
end

-- Process complete lines from the incoming buffer
local function process_buffer()
    while true do
        local nl_pos = incoming_buffer:find("\n")
        if not nl_pos then break end
        local line = incoming_buffer:sub(1, nl_pos - 1)
        incoming_buffer = incoming_buffer:sub(nl_pos + 1)

        line = line:gsub("\r$", "")

        -- Look for "say_all " anywhere in the line
        local say_pos = line:find("say_all ")
        if say_pos then
            local msg = line:sub(say_pos + 8) -- skip "say_all "
            say_all(msg)
            cprint("[HaloDiscordBot] Received from Discord: " .. msg)
        elseif line ~= "" then
            cprint("[HaloDiscordBot] Unknown command: " .. line)
        end
    end
end

-- Poll for incoming data (non-blocking, chunked read)
function OnPollIncoming()
    if not is_connected or not sock then return true end

    local was_blocking = sock:set_blocking(false)
    local data, err, partial = sock:receive(4096)
    sock:set_blocking(was_blocking)

    if data then
        if type(data) == "string" then
            incoming_buffer = incoming_buffer .. data
        end
        process_buffer()
    elseif partial then
        if type(partial) == "string" then
            incoming_buffer = incoming_buffer .. partial
            process_buffer()
        end
    elseif err == "closed" then
        cprint("[HaloDiscordBot] Connection closed by remote host")
        disconnect()
        if auto_connect then schedule_reconnect() end
    elseif err ~= "timeout" and err ~= "tryagain" then
        cprint("[HaloDiscordBot] Read error: " .. tostring(err))
        disconnect()
        if auto_connect then schedule_reconnect() end
    end

    return true
end

local function respond(id)
    return id == 0 and cprint or function(msg) rprint(id, msg) end
end

local function is_chat_command(s)
    local first_char = s:sub(1, 1)
    return first_char == "/" or first_char == "\\"
end

local function escape_value(value)
    if value == nil then return "" end
    local str = tostring(value)
    str = str:gsub("|", "\\|")
    str = str:gsub("\n", "\\n")
    str = str:gsub("\r", "\\r")
    return str
end

local function format_event(event_type, data_table, subtype)
    local parts = { event_type }
    if subtype then table_insert(parts, "subtype=" .. escape_value(subtype)) end
    for key, value in pairs(data_table) do
        table_insert(parts, key .. "=" .. escape_value(value))
    end
    table_insert(parts, "timestamp=" .. os_time())
    return concat(parts, "|")
end

local function log_event(event_type, data, subtype)
    local event_string = format_event(event_type, data, subtype)
    send_data(event_string .. "\n")
end

local function read_wide_string(address, length)
    local bytes = {}
    for i = 1, length do
        local byte = read_byte(address + (i - 1) * 2)
        if byte == 0 then break end
        bytes[#bytes + 1] = char(byte)
    end
    return concat(bytes)
end

local function get_server_name()
    local network_struct = read_dword(sig_scan("F3ABA1????????BA????????C740??????????E8????????668B0D") + 3)
    return read_wide_string(network_struct + 0x8, 0x42)
end

local function get_tag(class, name)
    local tag = lookup_tag(class, name)
    return tag ~= 0 and read_dword(tag + 0xC) or nil
end

local function new_player(id)
    return {
        id = id,
        last_damage = 0,
        switched = false,
        ip = get_var(id, '$ip'),
        name = get_var(id, '$name'),
        team = get_var(id, '$team'),
        hash = get_var(id, '$hash'),
        level = function() return tonumber(get_var(id, '$lvl')) end
    }
end

local function get_player_data(player, isQuit)
    local total = tonumber(get_var(0, '$pn'))
    return {
        total = isQuit and total - 1 or total,
        name = player.name,
        ip = player.ip,
        hash = player.hash,
        id = player.id,
        lvl = player.level(),
        ping = get_var(player.id, "$ping"),
        pirated = PIRATED_HASHES[player.hash] and 'YES' or 'NO'
    }
end

local function in_vehicle(id)
    local dyn_player = get_dynamic_player(id)
    return dyn_player ~= 0 and read_dword(dyn_player + 0x11C) ~= 0xFFFFFFFF
end

function OnStart(notifyFlag)
    gametype = get_var(0, "$gt")
    if gametype == 'n/a' then return end
    if not server_name then server_name = get_server_name() end

    players, first_blood = {}, true
    ffa = get_var(0, '$ffa') == '1'
    mode, map = get_var(0, "$mode"), get_var(0, "$map")
    falling_tag, distance_tag = get_tag('jpt!', 'globals\\falling'), get_tag('jpt!', 'globals\\distance')
    score_limit = read_byte(gametype_base + 0x58)

    if not notifyFlag or notifyFlag == 0 then
        log_event("event_start", { map = map, mode = mode, gt = gametype, ffa = ffa and "true" or "false" })
    end

    for i = 1, 16 do
        if player_present(i) then OnJoin(i, notifyFlag) end
    end
end

function OnEnd()
    log_event("event_end", { map = map, mode = mode, gt = gametype, ffa = ffa and "true" or "false" })
end

function OnJoin(id, notifyFlag)
    players[id] = new_player(id)
    if not notifyFlag then log_event("event_join", get_player_data(players[id])) end
end

function OnQuit(id)
    local player = players[id]
    if player then
        log_event("event_leave", get_player_data(player, true))
        players[id] = nil
    end
end

function OnSpawn(id)
    local player = players[id]
    if player then
        player.last_damage, player.switched = 0, nil
        log_event("event_spawn", { name = player.name, team = player.team })
    end
end

function OnSwitch(id)
    local player = players[id]
    if player then
        player.team, player.switched = get_var(id, '$team'), true
        log_event("event_team_switch", { name = player.name, team = player.team })
    end
end

function OnReset()
    log_event("event_map_reset", { map = map, mode = mode, gt = gametype, ffa = ffa and "FFA" or "Team Play" })
end

function OnLogin(id)
    local player = players[id]
    if player then log_event("event_login", { name = player.name, lvl = player.level() }) end
end

function OnSnap(id)
    local player = players[id]
    if player then log_event("event_snap", { name = player.name }) end
end

local function handle_discord_command(id, command)
    local player = players[id]
    if not player then return false end
    if player.level() < 2 then
        respond(id)("[HaloDiscordBot] Insufficient permissions")
        return false
    end

    local args = {}
    for word in command:gmatch("([^%s]+)") do args[#args + 1] = word end
    local sub_cmd = args[2] and args[2]:lower()
    if not sub_cmd then
        respond(id)("Usage: /discord <connect|disconnect|status|reconnect> [host] [port]")
        return false
    end

    local tell = respond(id)
    if sub_cmd == "connect" then
        local new_host = args[3] or host
        local new_port = tonumber(args[4] or tostring(port))
        if not new_port or new_port < 1 or new_port > 65535 then
            tell("Invalid port number.")
            return false
        end
        host, port = new_host, new_port
        auto_connect = true
        disconnect()
        connect_to_bot(host, port)
        if not is_connected then schedule_reconnect() end
        return false
    elseif sub_cmd == "disconnect" then
        auto_connect = false
        disconnect()
        tell("Auto-connect disabled. Connection closed.")
        return false
    elseif sub_cmd == "status" then
        local status = is_connected and "connected" or "disconnected"
        local auto_str = auto_connect and "ON" or "OFF"
        tell(string_format("[HaloDiscordBot] Status: %s | Auto-connect: %s | %s:%d", status, auto_str, host, port))
        return false
    elseif sub_cmd == "reconnect" then
        disconnect()
        auto_connect = true
        connect_to_bot(host, port)
        if not is_connected then schedule_reconnect() end
        return false
    else
        tell("Unknown sub-command. Usage: /discord <connect|disconnect|status|reconnect>")
        return false
    end
end

function OnCommand(id, command, env)
    local player = players[id]
    if not player then return true end
    if command:lower():match("^discord%s") then return handle_discord_command(id, command) end
    log_event("event_command", {
        lvl = player.level(),
        name = player.name,
        id = tostring(id),
        type = COMMAND_TYPE[env],
        cmd = command
    })
    return true
end

function OnChat(id, msg, env)
    local player = players[id]
    if not player then return end
    if not is_chat_command(msg) and msg:sub(1, 1) ~= "@" then
        log_event("event_chat", { type = CHAT_TYPE[env], name = player.name, id = id, msg = msg })
    end
end

function OnScore(id)
    local player = players[id]
    if not player then return end
    local event_type = GAMETYPE_MAP[gametype] or (gametype == "race" and (ffa and 3 or 2))
    if not event_type then return end
    log_event("event_score", {
        total_team_laps = player.team == "red" and get_var(0, "$redscore") or get_var(0, "$bluescore"),
        score = get_var(id, "$score"),
        name = player.name,
        team = player.team or "FFA",
        red_score = get_var(0, "$redscore"),
        blue_score = get_var(0, "$bluescore"),
        scorelimit = score_limit
    }, event_type)
end

function OnDamage(victim, _, metaId)
    local player = players[tonumber(victim)]
    if player then player.last_damage = metaId end
end

function OnDeath(victim, killer)
    victim, killer = tonumber(victim), tonumber(killer)
    local victim_data, killer_data = players[victim], players[killer]
    if not victim_data then return end

    local event_type = 10
    if killer == -1 and not victim_data.switched then
        event_type = (victim_data.last_damage == falling_tag or victim_data.last_damage == distance_tag) and 8 or 9
    elseif killer == 0 then
        event_type = 7
    elseif killer > 0 then
        if killer == victim then
            event_type = 5
        elseif not ffa and killer_data and victim_data.team == killer_data.team then
            event_type = 6
        elseif first_blood then
            first_blood = false; event_type = 1
        elseif not player_alive(killer) then
            event_type = 2
        elseif in_vehicle(victim) then
            event_type = 3
        else
            event_type = 4
        end
    end

    log_event("event_death", {
        killer_name = killer_data and killer_data.name or "",
        victim_name = victim_data.name
    }, event_type)
end

function OnScriptLoad()
    -- Load socket library
    if not load_socket() then
        cprint("[HaloDiscordBot] FATAL: LuaJitSocket missing, cannot send events")
        return
    end

    gametype_base = read_dword(sig_scan("B9360000008BF3BF78545F00") + 0x8)

    register_callback(cb['EVENT_CHAT'], 'OnChat')
    register_callback(cb['EVENT_COMMAND'], 'OnCommand')
    register_callback(cb['EVENT_DAMAGE_APPLICATION'], 'OnDamage')
    register_callback(cb['EVENT_DIE'], 'OnDeath')
    register_callback(cb['EVENT_GAME_END'], 'OnEnd')
    register_callback(cb['EVENT_GAME_START'], 'OnStart')
    register_callback(cb['EVENT_JOIN'], 'OnJoin')
    register_callback(cb['EVENT_LEAVE'], 'OnQuit')
    register_callback(cb['EVENT_LOGIN'], 'OnLogin')
    register_callback(cb['EVENT_MAP_RESET'], "OnReset")
    register_callback(cb['EVENT_SCORE'], 'OnScore')
    register_callback(cb['EVENT_SNAP'], 'OnSnap')
    register_callback(cb['EVENT_SPAWN'], 'OnSpawn')
    register_callback(cb['EVENT_TEAM_SWITCH'], 'OnSwitch')

    -- Connect to Discord bot
    if auto_connect then
        connect_to_bot(host, port)
        if not is_connected then schedule_reconnect() end
    end

    OnStart(1) -- in case script is loaded mid-game
end

function OnScriptUnload()
    if is_connected then send_data("SHUTDOWN\n") end
    disconnect()
end
