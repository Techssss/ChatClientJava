package com.chat.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO xử lý lưu/đọc tin nhắn đã mã hóa.
 * KHÔNG có bất kỳ cột nào chứa plain text.
 */
public class MessageDAO {

    private final Connection conn;

    public MessageDAO() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    /**
     * Lưu một tin nhắn đã mã hóa vào DB.
     *
     * @param senderId         ID của người gửi
     * @param receiverId       ID của người nhận
     * @param encryptedContent nội dung đã mã hóa AES-GCM, Base64
     * @param ivB64            IV (12 bytes) dạng Base64
     * @return message_id của tin nhắn vừa lưu
     */
    public int saveMessage(int senderId, int receiverId,
                           String encryptedContent, String ivB64) {
        String sql = "INSERT INTO messages (sender_id, receiver_id, encrypted_content, iv) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, senderId);
            ps.setInt(2, receiverId);
            ps.setString(3, encryptedContent);
            ps.setString(4, ivB64);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("[MessageDAO] saveMessage error: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Lưu encrypted AES key cho một user (sender hoặc receiver).
     * Mỗi message có 2 bản ghi: 1 cho sender, 1 cho receiver.
     *
     * @param messageId       ID của message
     * @param userId          ID của user (sender hoặc receiver)
     * @param encryptedAesKey AES key đã mã hóa bằng RSA public key của user, Base64
     */
    public void saveMessageKey(int messageId, int userId, String encryptedAesKey) {
        String sql = "INSERT INTO message_keys (message_id, user_id, encrypted_aes_key) VALUES (?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, messageId);
            ps.setInt(2, userId);
            ps.setString(3, encryptedAesKey);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[MessageDAO] saveMessageKey error: " + e.getMessage());
        }
    }

    /**
     * Lấy tất cả tin nhắn giữa 2 user, kèm encrypted_aes_key của currentUser.
     *
     * @param currentUserId ID của user đang đọc
     * @param otherUserId   ID của user còn lại
     * @return danh sách EncryptedMessageRecord, mỗi record có đủ data để decrypt
     */
    public List<EncryptedMessageRecord> getMessages(int currentUserId, int otherUserId) {
        List<EncryptedMessageRecord> result = new ArrayList<>();
        // JOIN với message_keys để lấy AES key phù hợp với currentUser
        String sql = """
            SELECT m.id, m.sender_id, m.receiver_id, m.encrypted_content, m.iv,
                   mk.encrypted_aes_key, m.created_at
            FROM messages m
            JOIN message_keys mk ON mk.message_id = m.id AND mk.user_id = ?
            WHERE (m.sender_id = ? AND m.receiver_id = ?)
               OR (m.sender_id = ? AND m.receiver_id = ?)
            ORDER BY m.created_at ASC
            """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, currentUserId);
            ps.setInt(2, currentUserId);
            ps.setInt(3, otherUserId);
            ps.setInt(4, otherUserId);
            ps.setInt(5, currentUserId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                result.add(new EncryptedMessageRecord(
                    rs.getInt("id"),
                    rs.getInt("sender_id"),
                    rs.getInt("receiver_id"),
                    rs.getString("encrypted_content"),
                    rs.getString("iv"),
                    rs.getString("encrypted_aes_key"),
                    rs.getString("created_at")
                ));
            }
        } catch (SQLException e) {
            System.err.println("[MessageDAO] getMessages error: " + e.getMessage());
        }
        return result;
    }

    /**
     * Record chứa toàn bộ thông tin cần thiết để giải mã 1 tin nhắn.
     * Tất cả binary data đều ở dạng Base64.
     */
    public record EncryptedMessageRecord(
        int messageId,
        int senderId,
        int receiverId,
        String encryptedContent,  // Base64 — AES-GCM ciphertext
        String ivB64,             // Base64 — 12-byte IV
        String encryptedAesKey,   // Base64 — RSA-encrypted AES key (của currentUser)
        String createdAt
    ) {}
}
