// Copyright (c) 2026. Jericho Crosby (Chalwk)

package com.chalwk.tcp;

import com.chalwk.Config;
import com.chalwk.utils.LoggerUtil;
import net.dv8tion.jda.api.JDA;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class GameEventTcpServer {

    private final GameEventProcessor processor;
    private final int port;
    private final String serverName;

    public GameEventTcpServer(JDA jda, Config config, String serverName, int port) {
        this.serverName = serverName;
        this.port = port;
        this.processor = new GameEventProcessor(jda, config, serverName, port);
    }

    public GameEventProcessor getProcessor() {
        return processor;
    }

    public void start() {
        new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(port, 0, InetAddress.getLoopbackAddress())) {
                LoggerUtil.info("TCP Server started for '{}' on port {}.", serverName, port);
                while (!Thread.currentThread().isInterrupted()) {
                    Socket clientSocket = serverSocket.accept();
                    LoggerUtil.info("[{}] New connection from {}", serverName, clientSocket.getRemoteSocketAddress());
                    processor.setHasConnectedClient(true);
                    new Thread(() -> handleClient(clientSocket)).start();
                }
            } catch (Exception e) {
                LoggerUtil.error("TCP server error for {}: {}", serverName, e.getMessage(), e);
            }
        }).start();
    }

    private void handleClient(Socket socket) {
        try (socket; BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
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
        } finally {
            LoggerUtil.info("[{}] Client disconnected", serverName);
            processor.setHasConnectedClient(false);
        }
    }
}