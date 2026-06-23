package com.chat.server;

import com.chat.common.Message;
import com.chat.crypto.RSAUtil;
import com.chat.db.ConversationItemDAO;
import com.chat.db.ConversationItemDAO.ConversationItemRecord;
import com.chat.db.MessageDAO;
import com.chat.db.MessageDAO.EncryptedMessageRecord;
import com.chat.db.UserDAO;
import com.chat.db.UserDAO.UserRecord;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
    private final ConversationItemDAO conversationItemDAO = new ConversationItemDAO();
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

            // Vòng lặp xử lý trước khi CONNECT — cho phép LOGIN / REGISTER trước
            while (true) {
                Message msg = (Message) in.readObject();

                if (msg.getType() == Message.MessageType.REGISTER ||
                    msg.getType() == Message.MessageType.LOGIN) {
                    handleAuthentication(msg);
                    // Tiếp tục chờ CONNECT

                } else if (msg.getType() == Message.MessageType.CONNECT) {
                    username = msg.getSender();
                    server.addClient(username, this);
                    sendConversationList();
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
        msg.setSender(username); // không cho client giả mạo sender

        switch (msg.getType()) {
            case ENCRYPTED_TEXT  -> persistEncryptedMessage(msg);
            case KEY_REQUEST     -> {
                handleKeyRequest(msg);
                return;
            }
            case HISTORY_REQUEST -> {
                handleHistoryRequest(msg);
                return;
            }
            case TEXT, ICON, FILE, STEGANOGRAPHY,
                 VOICE_CALL_REQ, VOICE_CALL_RES, VOICE_CALL_END,
                 VIDEO_CALL_REQ, VIDEO_CALL_RES, VIDEO_CALL_END -> persistConversationItem(msg);
            default              -> {} // các loại khác (voice/video/file...) chỉ forward
        }

        // Forward tới receiver
        server.sendToUser(receiver, msg);
    }

    /** Xử lý đăng nhập hoặc đăng ký tách biệt. Content: "username:passwordHash". */
    private void handleAuthentication(Message msg) {
        boolean registering = msg.getType() == Message.MessageType.REGISTER;
        try {
            String content = (String) msg.getContent();
            String[] parts = content.split(":", 2);
            if (parts.length != 2) {
                sendAuthFailure(registering, "Invalid authentication format");
                return;
            }
            String uname        = parts[0];
            String passwordHash = parts[1];

            UserRecord existing = userDAO.findByUsername(uname);

            if (registering) {
                if (existing != null) {
                    sendAuthFailure(true, "Username already exists");
                    return;
                }

                KeyPair keyPair  = RSAUtil.generateKeyPair();
                String pubB64   = RSAUtil.publicKeyToBase64(keyPair.getPublic());
                String privB64  = RSAUtil.privateKeyToBase64(keyPair.getPrivate());

                int userId = userDAO.createUser(uname, passwordHash, pubB64, privB64);
                if (userId == -1) {
                    sendAuthFailure(true, "Failed to create user");
                    return;
                }
                server.getUi().log("New user registered: " + uname + " (id=" + userId + ")");

                String okContent = userId + ":" + pubB64 + ":" + privB64;
                sendToSelf(new Message(Message.MessageType.REGISTER_OK, "Server", okContent));

            } else {
                if (existing == null) {
                    sendAuthFailure(false, "Account not found");
                    return;
                }
                if (!existing.passwordHash().equals(passwordHash)) {
                    sendAuthFailure(false, "Wrong password");
                    return;
                }
                server.getUi().log("User logged in: " + uname + " (id=" + existing.id() + ")");

                String okContent = existing.id() + ":" + existing.publicKeyB64() + ":" + existing.privateKeyB64();
                sendToSelf(new Message(Message.MessageType.LOGIN_OK, "Server", okContent));
            }
        } catch (Exception e) {
            server.getUi().log("[ERROR] Authentication error: " + e.getMessage());
            sendAuthFailure(registering, e.getMessage());
        }
    }

    private void sendAuthFailure(boolean registering, String message) {
        Message.MessageType type = registering
            ? Message.MessageType.REGISTER_FAIL
            : Message.MessageType.LOGIN_FAIL;
        sendToSelf(new Message(type, "Server", message));
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

    private void sendConversationList() {
        UserRecord current = userDAO.findByUsername(username);
        if (current == null) return;
        Message response = new Message(
            Message.MessageType.CONVERSATION_LIST,
            "Server",
            userDAO.getConversationUsernames(current.id())
        );
        sendToSelf(response);
    }

    private void handleHistoryRequest(Message msg) {
        UserRecord current = userDAO.findByUsername(username);
        UserRecord other = userDAO.findByUsername(msg.getReceiver());
        if (current == null || other == null) {
            server.getUi().log("[WARN] HISTORY_REQUEST user not found: "
                + username + " <-> " + msg.getReceiver());
            return;
        }

        List<Message> history = new ArrayList<>();
        for (EncryptedMessageRecord record : messageDAO.getMessages(current.id(), other.id())) {
            Message item = new Message(Message.MessageType.ENCRYPTED_TEXT, record.senderUsername(), null);
            item.setReceiver(record.receiverUsername());
            item.setSenderId(record.senderId());
            item.setReceiverId(record.receiverId());
            item.setEncryptedContent(record.encryptedContent());
            item.setIv(record.ivB64());
            item.setCreatedAt(record.createdAt());
            if (record.senderId() == current.id()) {
                item.setEncryptedAesKeyForSender(record.encryptedAesKey());
            } else {
                item.setEncryptedAesKeyForReceiver(record.encryptedAesKey());
            }
            history.add(item);
        }

        for (ConversationItemRecord record : conversationItemDAO.getItems(current.id(), other.id())) {
            Message item = new Message(record.type(), record.senderUsername(), record.contentText());
            item.setReceiver(record.receiverUsername());
            item.setSenderId(record.senderId());
            item.setReceiverId(record.receiverId());
            item.setFileName(record.fileName());
            item.setFileData(record.fileData());
            item.setCreatedAt(record.createdAt());
            history.add(item);
        }

        history.sort(Comparator.comparing(
            item -> item.getCreatedAt() == null ? "" : item.getCreatedAt()
        ));

        Message response = new Message(
            Message.MessageType.HISTORY_RESPONSE,
            "Server",
            new ArrayList<>(history)
        );
        response.setReceiver(other.username()); // peer mà client đang mở
        sendToSelf(response);
    }

    private void persistConversationItem(Message msg) {
        UserRecord senderRecord = userDAO.findByUsername(username);
        UserRecord receiverRecord = userDAO.findByUsername(msg.getReceiver());
        if (senderRecord == null || receiverRecord == null) {
            server.getUi().log("[WARN] Could not resolve conversation item users: "
                + username + " -> " + msg.getReceiver());
            return;
        }

        msg.setSenderId(senderRecord.id());
        msg.setReceiverId(receiverRecord.id());
        int itemId = conversationItemDAO.saveItem(senderRecord.id(), receiverRecord.id(), msg);
        if (itemId == -1) {
            server.getUi().log("[WARN] Could not persist " + msg.getType()
                + " for " + username + " -> " + msg.getReceiver());
        }
    }

    /**
     * Lưu tin nhắn đã mã hóa vào SQLite.
     * Server không thấy plain text — chỉ lưu ciphertext + IV + encrypted AES keys.
     */
    private void persistEncryptedMessage(Message msg) {
        try {
            UserRecord senderRecord = userDAO.findByUsername(username);
            UserRecord receiverRecord = userDAO.findByUsername(msg.getReceiver());
            if (senderRecord == null || receiverRecord == null) {
                server.getUi().log("[WARN] Could not resolve users for encrypted message: "
                    + username + " -> " + msg.getReceiver());
                return;
            }

            // Không tin senderId/receiverId do client gửi lên. Server tự ánh xạ
            // username của connection và receiver sang ID thật trong database.
            msg.setSender(username);
            msg.setSenderId(senderRecord.id());
            msg.setReceiverId(receiverRecord.id());

            int messageId = messageDAO.saveMessage(
                senderRecord.id(), receiverRecord.id(),
                msg.getEncryptedContent(), msg.getIv()
            );
            if (messageId == -1) {
                server.getUi().log("[WARN] Could not persist encrypted message");
                return;
            }
            if (msg.getEncryptedAesKeyForReceiver() != null)
                messageDAO.saveMessageKey(messageId, receiverRecord.id(), msg.getEncryptedAesKeyForReceiver());
            if (msg.getEncryptedAesKeyForSender() != null)
                messageDAO.saveMessageKey(messageId, senderRecord.id(), msg.getEncryptedAesKeyForSender());

            server.getUi().log("[DB] Encrypted message saved: id=" + messageId
                + ", " + username + " -> " + msg.getReceiver());

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
