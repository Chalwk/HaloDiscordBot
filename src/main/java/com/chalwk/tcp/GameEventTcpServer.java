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
    private final AtomicReference<PrintWriter> activeWriter = new AtomicReference<>();

    public GameEventTcpServer(JDA jda, Config config, String serverName, int port) {
        this.serverName = serverName;
        this.port = port;
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

    // starts a background thread that listens for incoming TCP connections
    public void start() {
        new Thread(() -> {
            // bind only to loopback so external connections can't sneak in. Fuck you script kiddies!
            try (ServerSocket serverSocket = new ServerSocket(port, 0, InetAddress.getLoopbackAddress())) {
                LoggerUtil.info("TCP Server started for '{}' on port {}.", serverName, port);
                while (!Thread.currentThread().isInterrupted()) {
                    Socket clientSocket = serverSocket.accept();
                    LoggerUtil.info("[{}] New connection from {}", serverName, clientSocket.getRemoteSocketAddress());
                    processor.setHasConnectedClient(true);
                    // each client gets its own thread to read lines and write back
                    new Thread(() -> handleClient(clientSocket)).start();
                }
            } catch (Exception e) {
                LoggerUtil.error("TCP server error for {}: {}", serverName, e.getMessage(), e);
            }
        }).start();
    }

    // reads lines from a connected client and feeds them into the processor
    private void handleClient(Socket socket) {
        try (socket;
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            activeWriter.set(writer);   // store writer for sending commands

            try {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    processor.processEvent(line);
                }
            } catch (Exception e) {
                LoggerUtil.error("[{}] Client connection error: {}", serverName, e.getMessage(), e);
            }
        } catch (Exception ignored) {
            // closing the socket might throw, but we don't really care here
        } finally {
            activeWriter.set(null);
            LoggerUtil.info("[{}] Client disconnected", serverName);
            processor.setHasConnectedClient(false);
        }
    }
}