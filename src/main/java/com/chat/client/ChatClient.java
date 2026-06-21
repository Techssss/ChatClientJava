package com.chat.client;

import com.chat.common.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ChatClient extends Thread {
    private String serverAddress;
    private int serverPort;
    private String username;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private ClientUI ui;
    private boolean running;

    public ChatClient(String serverAddress, int serverPort, String username, ClientUI ui) {
        this.serverAddress = serverAddress;
        this.serverPort = serverPort;
        this.username = username;
        this.ui = ui;
    }

    public boolean connect() {
        try {
            socket = new Socket(serverAddress, serverPort);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Send connect message
            Message connectMsg = new Message(Message.MessageType.CONNECT, username, null);
            sendMessage(connectMsg);
            running = true;
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public void run() {
        try {
            while (running) {
                Message msg = (Message) in.readObject();
                ui.handleMessage(msg);
            }
        } catch (Exception e) {
            if (running) {
                ui.showError("Connection lost!");
            }
        } finally {
            disconnect();
        }
    }

    public void sendMessage(Message msg) {
        try {
            msg.setSender(username);
            out.writeObject(msg);
            out.flush();
            out.reset(); // Crucial for preventing memory leaks when sending audio/video
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void disconnect() {
        running = false;
        try {
            if (out != null) {
                out.writeObject(new Message(Message.MessageType.DISCONNECT, username, null));
            }
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getUsername() {
        return username;
    }
}
