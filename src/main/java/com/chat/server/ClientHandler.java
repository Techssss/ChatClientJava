package com.chat.server;

import com.chat.common.Message;
import com.chat.crypto.RSAUtil;
import com.chat.db.MessageDAO;
import com.chat.db.UserDAO;
import com.chat.db.UserDAO.UserRecord;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.KeyPair;
import java.security.MessageDigest;

/**
 * Xử lý kết nối của một client.
 *
 * Giao thức mở rộng:
 * - REGISTER      : client đăng ký / đăng nhập, server trả REGISTER_OK/FAIL
 * - KEY_REQUEST   : client hỏi public key của user khác, server trả KEY_RESPONSE
 * - ENCRYPTED_TEXT: lưu ciphertext vào DB, forward cho receiver
 * - Server KHÔNG BAO GIỜ thấy plain text.
 */
public class ClientHandler extends Thread {

    private final Socket socket;
    private final ChatServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;

    private final MessageDAO messageDAO = new MessageDAO();
    private final UserDAO    userDAO    = new UserDAO();

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());

            // Vòng lặp xử lý trước khi CONNECT — cho phép REGISTER trước
            while (true) {
                Message msg = (Message) in.readObject();

                if (msg.getType() == Message.MessageType.REGISTER) {
                    // Xử lý đăng ký / đăng nhập, trả về userId
                    handleRegister(msg);
                    // Tiếp tục chờ CONNECT

                } else if (msg.getType() == Message.MessageType.CONNECT) {
                    username = msg.getSender();
                    server.addClient(username, this);
                    break; // Vào vòng lặp chính

                } else {
                    // Tin nhắn không hợp lệ trước khi CONNECT
                    closeConnection();
                    return;
                }
            }

            // Vòng lặp chính
            while (true) {
                Message msg = (Message) in.readObject();
                handleMessage(msg);
            }

        } catch (Exception e) {
            server.getUi().log("Connection lost with " + username);
        } finally {
            server.removeClient(username);
            closeConnection();
        }
    }

    // ─────────────────────────────── Handlers ───────────────────────────────

    private void handleMessage(Message msg) {
        String receiver = msg.getReceiver();
        if (receiver == null || receiver.equals("All")) return; // chỉ hỗ trợ private

        switch (msg.getType()) {
            case ENCRYPTED_TEXT  -> persistEncryptedMessage(msg);
            case KEY_REQUEST     -> handleKeyRequest(msg);
            default              -> {} // các loại khác (voice/video/file...) chỉ forward
        }

        // Forward tới receiver
        if (msg.getType() != Message.MessageType.KEY_REQUEST) {
            server.sendToUser(receiver, msg);
        }
    }

    /**
     * Client đăng ký tài khoản mới hoặc đăng nhập.
     * Content format: "username:passwordHash"
     * Server tự generate RSA key pair khi đăng ký mới.
     */
    private void handleRegister(Message msg) {
        try {
            String content = (String) msg.getContent();
            String[] parts = content.split(":", 2);
            if (parts.length != 2) {
                sendToSelf(new Message(Message.MessageType.REGISTER_FAIL, "Server",
                    "Invalid registration format"));
                return;
            }
            String uname        = parts[0];
            String passwordHash = parts[1];

            UserRecord existing = userDAO.findByUsername(uname);

            if (existing == null) {
                // Đăng ký mới: generate RSA key pair
                KeyPair keyPair  = RSAUtil.generateKeyPair();
                String pubB64   = RSAUtil.publicKeyToBase64(keyPair.getPublic());
                String privB64  = RSAUtil.privateKeyToBase64(keyPair.getPrivate());

                int userId = userDAO.createUser(uname, passwordHash, pubB64, privB64);
                if (userId == -1) {
                    sendToSelf(new Message(Message.MessageType.REGISTER_FAIL, "Server",
                        "Failed to create user"));
                    return;
                }
                server.getUi().log("New user registered: " + uname + " (id=" + userId + ")");

                // REGISTER_OK content = "userId:publicKeyB64:privateKeyB64"
                String okContent = userId + ":" + pubB64 + ":" + privB64;
                Message ok = new Message(Message.MessageType.REGISTER_OK, "Server", okContent);
                sendToSelf(ok);

            } else {
                // Đăng nhập: kiểm tra password
                if (!existing.passwordHash().equals(passwordHash)) {
                    sendToSelf(new Message(Message.MessageType.REGISTER_FAIL, "Server",
                        "Wrong password"));
                    return;
                }
                server.getUi().log("User logged in: " + uname + " (id=" + existing.id() + ")");

                // Gửi cả key về cho client
                String okContent = existing.id() + ":" + existing.publicKeyB64() + ":" + existing.privateKeyB64();
                Message ok = new Message(Message.MessageType.REGISTER_OK, "Server", okContent);
                sendToSelf(ok);
            }
        } catch (Exception e) {
            server.getUi().log("[ERROR] Register error: " + e.getMessage());
            sendToSelf(new Message(Message.MessageType.REGISTER_FAIL, "Server", e.getMessage()));
        }
    }

    /**
     * Client yêu cầu public key của một user khác.
     * content = username cần lấy key.
     * Server trả KEY_RESPONSE với publicKeyBase64 trong content.
     */
    private void handleKeyRequest(Message msg) {
        String targetUsername = (String) msg.getContent();
        UserRecord record = userDAO.findByUsername(targetUsername);
        if (record == null) {
            server.getUi().log("[WARN] KEY_REQUEST: user not found: " + targetUsername);
            return;
        }
        // Gửi public key về cho client yêu cầu
        Message resp = new Message(Message.MessageType.KEY_RESPONSE, targetUsername, record.publicKeyB64());
        sendToSelf(resp);
    }

    /**
     * Lưu tin nhắn đã mã hóa vào SQLite.
     * Server không thấy plain text — chỉ lưu ciphertext + IV + encrypted AES keys.
     */
    private void persistEncryptedMessage(Message msg) {
        try {
            int messageId = messageDAO.saveMessage(
                msg.getSenderId(), msg.getReceiverId(),
                msg.getEncryptedContent(), msg.getIv()
            );
            if (messageId == -1) {
                server.getUi().log("[WARN] Could not persist encrypted message");
                return;
            }
            if (msg.getEncryptedAesKeyForReceiver() != null)
                messageDAO.saveMessageKey(messageId, msg.getReceiverId(), msg.getEncryptedAesKeyForReceiver());
            if (msg.getEncryptedAesKeyForSender() != null)
                messageDAO.saveMessageKey(messageId, msg.getSenderId(), msg.getEncryptedAesKeyForSender());

        } catch (Exception e) {
            server.getUi().log("[ERROR] Failed to persist message: " + e.getMessage());
        }
    }

    // ─────────────────────────────── I/O ────────────────────────────────────

    /** Gửi message về chính client đang xử lý (trước khi addClient). */
    private void sendToSelf(Message msg) {
        sendMessage(msg);
    }

    public void sendMessage(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
            out.reset();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void closeConnection() {
        try {
            if (in  != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
