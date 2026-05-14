--[[
========================================================================================
SCRIPT NAME:      phasor_discord.lua
DESCRIPTION:      Logs Halo server events using LuaJitSocket TCP.

                  PREREQUISITES:
                  1. HaloDiscordBot needs to be installed and running.
                     https://github.com/Chalwk/HaloDiscordBot

                  2. LuaJIT Socket supporting Lua 5.1
                     https://github.com/CapsAdmin/luajitsocket/blob/master/ljsocket.lua

Copyright (c) 2026 Jericho Crosby (Chalwk)
========================================================================================
]]

-- CONFIG START --
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
local type = type

local readbyte = readbyte
local readdword = readdword
local readword = readword
local getplayer = getplayer
local getplayerobjectid = getplayerobjectid
local getobject = getobject

local current_request_id
local ffa
local first_blood
local gametype
local gametype_base
local map
local mode
local players
local total_players_address

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
local incoming_buffer = ""

local function load_socket()
    local ok, mod = pcall(require, "ljsocket")
    if not ok then
        respond("[HaloDiscordBot] LuaJitSocket failed to load: " .. tostring(mod))
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

-- Timer callback that attempts to reconnect if the bot is disconnected.
function OnReconnect()
    if is_connected or not auto_connect then
        reconnect_timer_active = false
        return false
    end

    respond("[HaloDiscordBot] Attempting reconnection...")
    connect_to_bot(host, port)

    if not is_connected then return true end

    reconnect_timer_active = false
    return false
end

-- Establish a TCP connection to the Discord bot.
function connect_to_bot(target_host, target_port)
    if not socket_lib then
        respond("[HaloDiscordBot] LuaJitSocket not loaded")
        return false
    end
    if sock then
        pcall(sock.close, sock)
        sock = nil
    end

    local s = socket_lib.create("inet", "stream", "tcp")
    if not s then
        respond("[HaloDiscordBot] socket.create failed")
        return false
    end

    s:set_blocking(false)

    local res, err, num = s:connect(target_host, tostring(target_port))
    if not res then
        s:close()
        respond("[HaloDiscordBot] Connection to " .. target_host .. ":" .. target_port .. " failed: " .. tostring(err))
        return false
    end

    if res == true then
        local poll_result, poll_err = s:poll(5000, "out")
        if not poll_result or not poll_result.out then
            s:close()
            respond("[HaloDiscordBot] Connection to " .. target_host .. ":" .. target_port .. " timed out")
            return false
        end
    end

    s:set_blocking(true)
    s:set_option("sndtimeo", 1000)

    sock = s
    is_connected = true
    respond("[HaloDiscordBot] Connected to " .. target_host .. ":" .. target_port)

    timer(1000, "OnPollIncoming")

    if #message_queue > 0 then
        local count = 0
        for _, msg in ipairs(message_queue) do
            local bytes, _err = s:send(msg)
            if not bytes then
                respond("[HaloDiscordBot] Queue send error: " .. tostring(_err))
                break
            end
            count = count + 1
        end

        if count > 0 then
            local q = message_queue
            local len = #q
            for i = 1, len - count do
                q[i] = q[i + count]
            end
            for i = len - count + 1, len do
                q[i] = nil
            end
        end
    end
    return true
end

-- Close the current connection and reset state.
local function disconnect()
    reconnect_timer_active = false
    if sock then
        pcall(sock.close, sock)
        sock = nil
    end
    is_connected = false
    respond("[HaloDiscordBot] Disconnected")
end

-- Send raw data over the socket. If disconnected, queue the message.
-- data: string to send.
-- Returns true if sent immediately, false if queued or error.
local function send_data(data)
    if not is_connected then
        table_insert(message_queue, data)
        if #message_queue > max_queue_size then
            table_remove(message_queue, 1)
        end
        if auto_connect then schedule_reconnect() end
        return false
    end

    if not sock then
        -- Should not happen if is_connected is true, but guard anyway!
        is_connected = false
        table_insert(message_queue, data)
        if #message_queue > max_queue_size then
            table_remove(message_queue, 1)
        end
        if auto_connect then schedule_reconnect() end
        return false
    end

    local bytes, err = sock:send(data)
    if not bytes then
        respond("[HaloDiscordBot] Send error: " .. tostring(err))
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

-- Process complete lines from the incoming buffer ("say_all" and "exec" commands).
local function process_buffer()
    while true do
        local nl_pos = incoming_buffer:find("\n")
        if not nl_pos then break end
        local line = incoming_buffer:sub(1, nl_pos - 1)
        incoming_buffer = incoming_buffer:sub(nl_pos + 1)

        line = line:gsub("\r$", "")

        local say_pos = line:find("say_all ")
        if say_pos then
            local msg = line:sub(say_pos + 8)
            say_all(msg)
            respond("[HaloDiscordBot] Received from Discord: " .. msg)
        elseif line:find("^exec ") then
            local cmd = line:sub(6)
            local parts = {}
            for part in cmd:gmatch("%S+") do
                table_insert(parts, part)
            end
            if #parts >= 4 then
                local reqId = parts[1]
                local playerIndex = tonumber(parts[2]) or 0
                local echo = parts[3] == "1"
                local command = concat(parts, " ", 4)
                respond("[HaloDiscordBot] Executing command: " .. command)
                current_request_id = reqId
                execute_command(command, playerIndex, echo)
                current_request_id = nil
            else
                respond("[HaloDiscordBot] Invalid exec format: " .. line)
            end
        elseif line ~= "" then
            respond("[HaloDiscordBot] Unknown command: " .. line)
        end
    end
end

-- Poll for incoming data (non-blocking, chunked read).
-- Timer callback that reads from the socket and processes lines.
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
        respond("[HaloDiscordBot] Connection closed by remote host")
        disconnect()
        if auto_connect then schedule_reconnect() end
    elseif err ~= "timeout" and err ~= "tryagain" then
        respond("[HaloDiscordBot] Read error: " .. tostring(err))
        disconnect()
        if auto_connect then schedule_reconnect() end
    end

    return true
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

-- Format an event as a pipe-separated string.
-- subtype: numeric subtype for event_death and event_score
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

local function get_gametype_string()
    local count = 0
    local bytes = {}
    for i = 1, 0x2C do
        local b = readbyte(gametype_base + count)
        if b == 0 then break end
        bytes[i] = char(b)
        count = count + 2
    end
    return concat(bytes):lower()
end

local function get_scorelimit()
    return readbyte(gametype_base + 0x58)
end

local function get_ffa()
    return readbyte(gametype_base + 0x34) == 0
end

local function get_team(id)
    local p = players[id]
    return p and p.team or "unknown"
end

local function in_vehicle(id)
    local obj_id = getplayerobjectid(id)
    if not obj_id then return false end
    local vehicle_id = readdword(getobject(obj_id) + 0x11C)
    return vehicle_id ~= 0xFFFFFFFF
end

local function new_player(id)
    return { id = id, switched = false, ip = getip(id), name = getname(id), team = get_team(id), hash = gethash(id) }
end

local function get_ping(id)
    local p = getplayer(id)
    if not p then return nil end
    return readword(p + 0xDC)
end

local function is_alive(id)
    if not id then return false end
    return getplayerobjectid(id) ~= nil
end

local function get_player(player)
    if not player then return nil end
    local id = resolveplayer(player)
    return id and players[id] or nil
end

local function get_player_data(player)
    return {
        total = readword(total_players_address),
        name = player.name,
        ip = player.ip,
        hash = player.hash,
        id = player.id,
        ping = get_ping(player.id),
        pirated = PIRATED_HASHES[player.hash] and 'YES' or 'NO'
    }
end

function OnNewGame(map_name)
    gametype = get_gametype_string()
    if gametype == 'n/a' then return end

    players = {}
    first_blood = true
    ffa = get_ffa()

    mode = gametype
    map = map_name

    log_event("event_start", { map = map, mode = mode, gt = gametype, ffa = ffa and "true" or "false" })

    for i = 0, 15 do
        if getplayer(i) then OnPlayerJoin(i) end
    end
end

function OnGameEnd()
    log_event("event_end", { map = map, mode = mode, gt = gametype, ffa = ffa and "true" or "false" })
end

function OnPlayerJoin(player)
    local id = resolveplayer(player) or player
    players[id] = new_player(id)
    local p = players[id]
    if p then
        log_event("event_join", get_player_data(p))
    end
end

function OnPlayerLeave(id)
    local player = players[id]
    if player then
        log_event("event_leave", get_player_data(player))
        players[id] = nil
    end
end

function OnPlayerSpawn(id)
    local player = players[id]
    if player then
        player.last_damage = 0
        player.switched = nil
        log_event("event_spawn", { name = player.name, team = player.team })
    end
end

function OnTeamChange(id, _, new_team)
    local player = players[id]
    if player then
        local team_name = (new_team == 0 and "blue") or (new_team == 1 and "red") or "FFA"
        player.team = team_name
        player.switched = true
        log_event("event_team_switch", { name = player.name, team = team_name })
    end
end

function OnMapReset()
    log_event("event_map_reset", { map = map, mode = mode, gt = gametype, ffa = ffa and "FFA" or "Team Play" })
end

function OnAdminLogin(id)
    local player = players[id]
    if player then
        log_event("event_login", { name = player.name })
    end
end

function OnAdminSnap(id)
    local player = players[id]
    if player then
        log_event("event_snap", { name = player.name })
    end
end

function OnEcho(playerIndex, message)
    if current_request_id then
        log_event("event_echo", { reqId = current_request_id, playerIndex = tostring(playerIndex), message = message })
    else
        log_event("event_echo", { playerIndex = tostring(playerIndex), message = message })
    end
end

function OnServerCommand(id, command)
    local player = players[id]
    if not player then return true end
    log_event("event_command", {
        name = player.name,
        id = tostring(id),
        type = "RCON",
        cmd = command
    })
    return true
end

function OnServerChat(id, type, msg)
    local player = players[id]
    if not player then return end
    if not is_chat_command(msg) and msg:sub(1, 1) ~= "@" then
        log_event("event_chat", { type = CHAT_TYPE[type], name = player.name, id = id, msg = msg })
    elseif is_chat_command(msg) then
        log_event("event_command", {
            name = player.name,
            id = tostring(id),
            type = "CHAT",
            cmd = msg
        })
    end
end

function OnPlayerScore(id)
    local player = players[id]
    if not player then return end

    local event_type = GAMETYPE_MAP[gametype] or (gametype == "race" and (ffa and 3 or 2))
    if not event_type then return end

    --
    -- todo: implement score-getting
    --
    local red_score = 0
    local blue_score = 0
    local total_team_laps = player.team == "red" and red_score or blue_score

    log_event("event_score", {
        total_team_laps = total_team_laps,
        score = "N/A",
        name = player.name,
        team = player.team or "FFA",
        red_score = red_score,
        blue_score = blue_score,
        scorelimit = get_scorelimit() -- in case scorelimit changes mid-game
    }, event_type
    )
end

function OnPlayerKill(killer, victim, kill_mode)
    -- Kill modes:
    -- 0 = server           1 = falling/team-change
    -- 2 = guardians        3 = vehicle
    -- 4 = player           5 = teammate
    -- 6 = suicide

    local victim_data = get_player(victim)
    if not victim_data then return end

    local event_type = 10 -- generic
    local killer_data = get_player(killer)

    if kill_mode == 0 then
        event_type = 9
    elseif kill_mode == 1 then
        if victim_data.switched then return end
        event_type = 8
    elseif kill_mode == 2 then
        event_type = 10
    elseif kill_mode == 3 then
        event_type = 7
    elseif kill_mode == 4 then
        if first_blood then
            first_blood = false
            event_type = 1
        elseif not killer or not is_alive(killer) then
            event_type = 2
        else
            event_type = 4
        end
    elseif kill_mode == 5 then
        event_type = 6
    elseif kill_mode == 6 then
        event_type = 5
    end

    log_event("event_death", {
        killer_name = killer_data and killer_data.name or "",
        victim_name = victim_data.name
    }, event_type
    )
end

function OnScriptLoad(_, game, _)
    if not load_socket() then
        respond("[HaloDiscordBot] FATAL: LuaJitSocket missing, cannot send events")
        return
    end

    local network_struct, ce
    if game == "CE" then
        ce = 0x40
        network_struct, gametype_base = 0x6C7980, 0x5F5498
    else
        ce = 0
        network_struct, gametype_base = 0x745BA0, 0x671340
    end
    total_players_address = network_struct + (0x1A8 + ce)

    if auto_connect then
        connect_to_bot(host, port)
        if not is_connected then schedule_reconnect() end
    end
end

function OnScriptUnload()
    if is_connected then send_data("SHUTDOWN\n") end
    disconnect()
end

function GetRequiredVersion()
    return 200
end
