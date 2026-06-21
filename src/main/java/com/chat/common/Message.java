package com.chat.common;

import java.io.Serializable;

public class Message implements Serializable {
    private static final long serialVersionUID = 2L; // bump version sau khi thêm field

    public enum MessageType {
        CONNECT, DISCONNECT, USER_LIST,
        TEXT, ICON, FILE,
        VOICE_CALL_REQ, VOICE_CALL_RES, VOICE_CALL_END, AUDIO_DATA,
        VIDEO_CALL_REQ, VIDEO_CALL_RES, VIDEO_CALL_END, VIDEO_DATA,
        STEGANOGRAPHY,
        // Hybrid Encryption
        ENCRYPTED_TEXT,
        // Key Exchange — client request public key của user khác từ server
        KEY_REQUEST,   // client → server: content = username cần lấy key
        KEY_RESPONSE,  // server → client: content = publicKeyBase64, sender = username
        // Registration — client đăng ký qua server (không trực tiếp vào DB)
        REGISTER,      // client → server: content = "username:passwordHash"
        REGISTER_OK,   // server → client: content = userId (Integer)
        REGISTER_FAIL  // server → client: content = thông báo lỗi
    }

    private MessageType type;
    private String sender;
    private String receiver;   // Riêng tư: luôn phải có receiver cụ thể
    private Object content;
    private String fileName;
    private byte[] fileData;

    // ── Trường Hybrid Encryption ────────────────────────────────────────────
    /** Nội dung đã mã hóa AES-GCM, Base64. */
    private String encryptedContent;
    /** AES key đã mã hóa RSA bằng public key của RECEIVER, Base64. */
    private String encryptedAesKeyForReceiver;
    /** AES key đã mã hóa RSA bằng public key của SENDER, Base64.
     *  Cho phép sender đọc lại tin nhắn của mình. */
    private String encryptedAesKeyForSender;
    /** IV (12 bytes) dùng cho AES-GCM, Base64. */
    private String iv;
    /** Numeric ID của sender (để server lưu DB). */
    private int senderId;
    /** Numeric ID của receiver. */
    private int receiverId;
    // ───────────────────────────────────────────────────────────────────────

    public Message(MessageType type, String sender, Object content) {
        this.type = type;
        this.sender = sender;
        this.content = content;
    }

    // ── Getters/Setters gốc ─────────────────────────────────────────────────
    public MessageType getType() { return type; }
    public void setType(MessageType type) { this.type = type; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }

    public String getReceiver() { return receiver; }
    public void setReceiver(String receiver) { this.receiver = receiver; }

    public Object getContent() { return content; }
    public void setContent(Object content) { this.content = content; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public byte[] getFileData() { return fileData; }
    public void setFileData(byte[] fileData) { this.fileData = fileData; }

    // ── Getters/Setters mã hóa ──────────────────────────────────────────────
    public String getEncryptedContent() { return encryptedContent; }
    public void setEncryptedContent(String encryptedContent) { this.encryptedContent = encryptedContent; }

    public String getEncryptedAesKeyForReceiver() { return encryptedAesKeyForReceiver; }
    public void setEncryptedAesKeyForReceiver(String key) { this.encryptedAesKeyForReceiver = key; }

    public String getEncryptedAesKeyForSender() { return encryptedAesKeyForSender; }
    public void setEncryptedAesKeyForSender(String key) { this.encryptedAesKeyForSender = key; }

    public String getIv() { return iv; }
    public void setIv(String iv) { this.iv = iv; }

    public int getSenderId() { return senderId; }
    public void setSenderId(int senderId) { this.senderId = senderId; }

    public int getReceiverId() { return receiverId; }
    public void setReceiverId(int receiverId) { this.receiverId = receiverId; }
}
