package com.chat.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO xử lý CRUD cho bảng users.
 * Lưu ý: private_key lưu dạng Base64, không bao giờ log ra console.
 */
public class UserDAO {

    private final Connection conn;

    public UserDAO() {
        this.conn = DatabaseManager.getInstance().getConnection();
    }

    /**
     * Tạo user mới với RSA key pair đã được generate sẵn.
     *
     * @param username      tên đăng nhập (unique)
     * @param passwordHash  hash của password (SHA-256 hoặc BCrypt)
     * @param publicKeyB64  public key dạng Base64
     * @param privateKeyB64 private key dạng Base64 (KHÔNG log ra console)
     * @return user_id của user vừa tạo, hoặc -1 nếu lỗi
     */
    public int createUser(String username, String passwordHash,
                          String publicKeyB64, String privateKeyB64) {
        String sql = "INSERT INTO users (username, password_hash, public_key, private_key) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, publicKeyB64);
            ps.setString(4, privateKeyB64);
            ps.executeUpdate();
            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            System.err.println("[UserDAO] createUser error: " + e.getMessage());
        }
        return -1;
    }

    /**
     * Tìm user theo username, trả về ResultSet.
     * Caller phải đóng ResultSet sau khi dùng.
     */
    public UserRecord findByUsername(String username) {
        String sql = "SELECT id, username, password_hash, public_key, private_key FROM users WHERE username = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new UserRecord(
                    rs.getInt("id"),
                    rs.getString("username"),
                    rs.getString("password_hash"),
                    rs.getString("public_key"),
                    rs.getString("private_key")
                );
            }
        } catch (SQLException e) {
            System.err.println("[UserDAO] findByUsername error: " + e.getMessage());
        }
        return null;
    }

    /** Lấy public_key (Base64) của user theo ID. */
    public String getPublicKey(int userId) {
        String sql = "SELECT public_key FROM users WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("public_key");
        } catch (SQLException e) {
            System.err.println("[UserDAO] getPublicKey error: " + e.getMessage());
        }
        return null;
    }

    /** Lấy private_key (Base64) của user theo ID — KHÔNG log ra console. */
    public String getPrivateKey(int userId) {
        String sql = "SELECT private_key FROM users WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getString("private_key");
        } catch (SQLException e) {
            System.err.println("[UserDAO] getPrivateKey error: " + e.getMessage());
        }
        return null;
    }

    /** Danh sách những user đã từng có nội dung hội thoại với current user. */
    public String[] getConversationUsernames(int currentUserId) {
        List<String> users = new ArrayList<>();
        String sql = """
            SELECT username
            FROM users
            WHERE id IN (
                SELECT CASE WHEN sender_id = ? THEN receiver_id ELSE sender_id END
                FROM messages
                WHERE sender_id = ? OR receiver_id = ?
                UNION
                SELECT CASE WHEN sender_id = ? THEN receiver_id ELSE sender_id END
                FROM conversation_items
                WHERE sender_id = ? OR receiver_id = ?
            )
            ORDER BY username COLLATE NOCASE
            """;
        synchronized (conn) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 1; i <= 6; i++) ps.setInt(i, currentUserId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) users.add(rs.getString("username"));
                }
            } catch (SQLException e) {
                System.err.println("[UserDAO] getConversationUsernames error: " + e.getMessage());
            }
        }
        return users.toArray(new String[0]);
    }

    /**
     * Cập nhật RSA key pair cho user (gọi sau khi tạo user).
     * Private key KHÔNG được log ra console.
     */
    public void updateKeys(int userId, String publicKeyB64, String privateKeyB64) {
        String sql = "UPDATE users SET public_key = ?, private_key = ? WHERE id = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, publicKeyB64);
            ps.setString(2, privateKeyB64);
            ps.setInt(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("[UserDAO] updateKeys error: " + e.getMessage());
        }
    }

    /** Simple DTO để truyền thông tin user. */
    public record UserRecord(int id, String username, String passwordHash,
                             String publicKeyB64, String privateKeyB64) {}
}
