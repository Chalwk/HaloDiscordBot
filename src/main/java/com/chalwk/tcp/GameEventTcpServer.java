// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk.tcp;

import com.chalwk.Config;
import com.chalwk.utils.LoggerUtil;
import net.dv8tion.jda.api.JDA;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicReference;

public class GameEventTcpServer {

    private final GameEventProcessor processor;
    private final int port;
    private final String serverName;
    private final String bindAddress;
    private final String secretKey;
    private final java.util.List<String> allowedIps;
    private final AtomicReference<PrintWriter> activeWriter = new AtomicReference<>();

    public GameEventTcpServer(JDA jda, Config config, String serverName, int port,
                              String bindAddress, String secretKey, java.util.List<String> allowedIps) {
        this.serverName = serverName;
        this.port = port;
        this.bindAddress = bindAddress == null ? "127.0.0.1" : bindAddress;
        this.secretKey = secretKey; // may be null for local-only (no auth)
        this.allowedIps = allowedIps == null ? new java.util.ArrayList<>() : allowedIps;
        this.processor = new GameEventProcessor(jda, config, serverName, port, this);
    }

    public GameEventProcessor getProcessor() {
        return processor;
    }

    public void sendCommand(String command) {
        PrintWriter writer = activeWriter.get();
        if (writer != null) {
            writer.println(command);
            writer.flush();
            LoggerUtil.debug("[{}] Sent command: {}", serverName, command);
        } else {
            LoggerUtil.warn("[{}] Cannot send command - no connected client.", serverName);
        }
    }

    private boolean isIpAllowed(String ip) {
        if (allowedIps.isEmpty()) return true;
        for (String allowed : allowedIps) {
            if (allowed.contains("/")) {
                String[] parts = allowed.split("/");
                String cidrIp = parts[0];
                int prefix = Integer.parseInt(parts[1]);
                if (ipMatchesCidr(ip, cidrIp, prefix)) return true;
            } else if (allowed.equals(ip)) {
                return true;
            }
        }
        return false;
    }

    private boolean ipMatchesCidr(String ip, String cidrIp, int prefix) {
        try {
            java.net.InetAddress inet = java.net.InetAddress.getByName(ip);
            java.net.InetAddress cidr = java.net.InetAddress.getByName(cidrIp);
            byte[] inetBytes = inet.getAddress();
            byte[] cidrBytes = cidr.getAddress();
            if (inetBytes.length != cidrBytes.length) return false;
            int fullBytes = prefix / 8;
            int remainingBits = prefix % 8;
            for (int i = 0; i < fullBytes; i++) {
                if (inetBytes[i] != cidrBytes[i]) return false;
            }
            if (remainingBits > 0) {
                int mask = 0xFF << (8 - remainingBits);
                return (inetBytes[fullBytes] & mask) == (cidrBytes[fullBytes] & mask);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void start() {
        new Thread(() -> {
            try {
                InetAddress addr = InetAddress.getByName(bindAddress);
                try (ServerSocket serverSocket = new ServerSocket(port, 0, addr)) {
                    LoggerUtil.info("TCP Server started for '{}' on {}:{}", serverName, bindAddress, port);
                    while (!Thread.currentThread().isInterrupted()) {
                        Socket clientSocket = serverSocket.accept();
                        String clientIp = clientSocket.getInetAddress().getHostAddress();
                        if (!isIpAllowed(clientIp)) {
                            LoggerUtil.warn("[{}] Rejected connection from {} (not in allowed_ips)", serverName, clientIp);
                            clientSocket.close();
                            continue;
                        }
                        LoggerUtil.info("[{}] New connection from {}", serverName, clientSocket.getRemoteSocketAddress());
                        processor.setHasConnectedClient(true);
                        new Thread(() -> handleClient(clientSocket)).start();
                    }
                }
            } catch (Exception e) {
                LoggerUtil.error("TCP server error for {}: {}", serverName, e.getMessage(), e);
            }
        }).start();
    }

    private void handleClient(Socket socket) {
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            // Authentication step (only if secretKey is configured)
            if (secretKey != null && !secretKey.isBlank()) {
                String firstLine = reader.readLine();
                if (firstLine != null && firstLine.startsWith("AUTH|")) {
                    String providedKey = firstLine.substring(5);
                    if (secretKey.equals(providedKey)) {
                        writer.println("AUTH_OK");
                        LoggerUtil.info("[{}] Client authenticated", serverName);
                    } else {
                        LoggerUtil.warn("[{}] Authentication failed from {}", serverName, socket.getRemoteSocketAddress());
                        writer.println("AUTH_FAIL");
                        return;
                    }
                } else {
                    LoggerUtil.warn("[{}] No auth message from {}, closing", serverName, socket.getRemoteSocketAddress());
                    return;
                }
            }

            activeWriter.set(writer);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                processor.processEvent(line);
            }
            LoggerUtil.info("[{}] Client disconnected", serverName);
        } catch (java.net.SocketException e) {
            LoggerUtil.info("[{}] Client disconnected ({}).", serverName, e.getMessage());
        } catch (Exception e) {
            LoggerUtil.error("[{}] Client connection error: {}", serverName, e.getMessage(), e);
        } finally {
            activeWriter.set(null);
            processor.setHasConnectedClient(false);
        }
    }
}