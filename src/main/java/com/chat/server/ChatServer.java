package com.chat.server;

import com.chat.common.Message;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer extends Thread {
    private int port;
    private ServerUI ui;
    private ServerSocket serverSocket;
    private boolean running = false;
    
    // Map to hold connected clients
    private Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    public ChatServer(int port, ServerUI ui) {
        this.port = port;
        this.ui = ui;
    }

    @Override
    public void run() {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            ui.log("Server started on port " + port);

            while (running) {
                Socket socket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(socket, this);
                handler.start();
            }
        } catch (IOException e) {
            if (running) {
                ui.log("Server Error: " + e.getMessage());
            }
        }
    }

    public void stopServer() {
        running = false;
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
            for (ClientHandler handler : clients.values()) {
                handler.closeConnection();
            }
            clients.clear();
            ui.log("Server stopped.");
        } catch (IOException e) {
            ui.log("Error stopping server: " + e.getMessage());
        }
    }

    public synchronized void addClient(String username, ClientHandler handler) {
        clients.put(username, handler);
        ui.log(username + " connected.");
        broadcastUserList();
    }

    public synchronized void removeClient(String username) {
        if (username != null) {
            clients.remove(username);
            ui.log(username + " disconnected.");
            broadcastUserList();
        }
    }

    public void broadcast(Message msg) {
        for (ClientHandler handler : clients.values()) {
            handler.sendMessage(msg);
        }
    }

    public void sendToUser(String targetUser, Message msg) {
        ClientHandler handler = clients.get(targetUser);
        if (handler != null) {
            handler.sendMessage(msg);
        }
    }

    private void broadcastUserList() {
        String[] userArray = clients.keySet().toArray(new String[0]);
        Message msg = new Message(Message.MessageType.USER_LIST, "Server", userArray);
        broadcast(msg);
    }
    
    public ServerUI getUi() {
        return ui;
    }
}
