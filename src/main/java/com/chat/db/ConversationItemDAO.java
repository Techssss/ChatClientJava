package com.chat.db;

import com.chat.common.Message;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Lưu attachment, sticker, plain-text fallback và sự kiện cuộc gọi. */
public class ConversationItemDAO {
    public static final int MAX_BLOB_BYTES = 16 * 1024 * 1024;

    private final Connection conn;

    public ConversationItemDAO() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    public int saveItem(int senderId, int receiverId, Message message) {
        byte[] fileData = message.getFileData();
        if (fileData != null && fileData.length > MAX_BLOB_BYTES) {
            System.err.println("[ConversationItemDAO] Skip oversized item: " + fileData.length + " bytes");
            return -1;
        }

        String sql = """
            INSERT INTO conversation_items
                (sender_id, receiver_id, item_type, content_text, file_name, file_data)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, senderId);
                ps.setInt(2, receiverId);
                ps.setString(3, message.getType().name());
                ps.setString(4, message.getContent() == null ? null : String.valueOf(message.getContent()));
                ps.setString(5, message.getFileName());
                ps.setBytes(6, fileData);
                ps.executeUpdate();
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) return rs.getInt(1);
                }
            } catch (SQLException e) {
                System.err.println("[ConversationItemDAO] saveItem error: " + e.getMessage());
            }
        }
        return -1;
    }

    public List<ConversationItemRecord> getItems(int currentUserId, int otherUserId) {
        List<ConversationItemRecord> result = new ArrayList<>();
        String sql = """
            SELECT ci.id, ci.sender_id, ci.receiver_id, ci.item_type,
                   ci.content_text, ci.file_name, ci.file_data, ci.created_at,
                   sender.username AS sender_username,
                   receiver.username AS receiver_username
            FROM conversation_items ci
            JOIN users sender ON sender.id = ci.sender_id
            JOIN users receiver ON receiver.id = ci.receiver_id
            WHERE (ci.sender_id = ? AND ci.receiver_id = ?)
               OR (ci.sender_id = ? AND ci.receiver_id = ?)
            ORDER BY ci.created_at ASC, ci.id ASC
            """;
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setInt(1, currentUserId);
                ps.setInt(2, otherUserId);
                ps.setInt(3, otherUserId);
                ps.setInt(4, currentUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        result.add(new ConversationItemRecord(
                            rs.getInt("id"),
                            rs.getInt("sender_id"),
                            rs.getInt("receiver_id"),
                            rs.getString("sender_username"),
                            rs.getString("receiver_username"),
                            Message.MessageType.valueOf(rs.getString("item_type")),
                            rs.getString("content_text"),
                            rs.getString("file_name"),
                            rs.getBytes("file_data"),
                            rs.getString("created_at")
                        ));
                    }
                }
            } catch (SQLException | IllegalArgumentException e) {
                System.err.println("[ConversationItemDAO] getItems error: " + e.getMessage());
            }
        }
        return result;
    }

    public record ConversationItemRecord(
        int id,
        int senderId,
        int receiverId,
        String senderUsername,
        String receiverUsername,
        Message.MessageType type,
        String contentText,
        String fileName,
        byte[] fileData,
        String createdAt
    ) {}
}
