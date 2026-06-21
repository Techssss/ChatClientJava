package com.chat.db;

import java.sql.*;

/**
 * Singleton DatabaseManager — chỉ dùng ở SERVER side.
 * Client KHÔNG trực tiếp truy cập DB này.
 *
 * File DB lưu tại thư mục làm việc khi server chạy.
 * Path tuyệt đối để tránh lỗi khi client và server chạy trên cùng máy.
 */
public class DatabaseManager {

    // Đường dẫn tuyệt đối cố định để server và client không bị nhầm lẫn
    private static final String DB_PATH = System.getProperty("user.home") + "/chat_server.db";
    private static final String DB_URL  = "jdbc:sqlite:" + DB_PATH;

    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {
        try {
            Class.forName("org.sqlite.JDBC");

            // Cấu hình connection properties
            java.util.Properties props = new java.util.Properties();
            props.setProperty("busy_timeout", "10000"); // chờ tối đa 10s khi DB bị lock

            connection = DriverManager.getConnection(DB_URL, props);
            connection.setAutoCommit(true);

            // PRAGMA foreign_keys — dùng executeUpdate, không dùng execute() cho PRAGMA
            try (Statement st = connection.createStatement()) {
                st.executeUpdate("PRAGMA foreign_keys = ON");
                // Busy timeout cũng set qua PRAGMA để chắc chắn
                st.executeUpdate("PRAGMA busy_timeout = 10000");
            }

            initSchema();
            System.out.println("[DB] Connected to: " + DB_PATH);

        } catch (Exception e) {
            throw new RuntimeException("Cannot connect to SQLite database: " + DB_PATH, e);
        }
    }

    public static synchronized DatabaseManager getInstance() {
        if (instance == null) {
            instance = new DatabaseManager();
        }
        return instance;
    }

    public Connection getConnection() {
        return connection;
    }

    /**
     * Khởi tạo schema. Migration an toàn — CREATE TABLE IF NOT EXISTS.
     * Không phá dữ liệu cũ.
     */
    private void initSchema() throws SQLException {
        try (Statement stmt = connection.createStatement()) {

            // --- Bảng users ---
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS users (" +
                "    id            INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    username      TEXT NOT NULL UNIQUE," +
                "    password_hash TEXT NOT NULL," +
                "    public_key    TEXT NOT NULL," +
                "    private_key   TEXT NOT NULL," +
                "    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP" +
                ")"
            );

            // --- Bảng messages (chỉ lưu ciphertext) ---
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS messages (" +
                "    id                INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    sender_id         INTEGER NOT NULL," +
                "    receiver_id       INTEGER NOT NULL," +
                "    encrypted_content TEXT NOT NULL," +
                "    iv                TEXT NOT NULL," +
                "    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP," +
                "    FOREIGN KEY (sender_id)   REFERENCES users(id)," +
                "    FOREIGN KEY (receiver_id) REFERENCES users(id)" +
                ")"
            );

            // --- Bảng message_keys (2 dòng/message: sender + receiver) ---
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS message_keys (" +
                "    id                INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    message_id        INTEGER NOT NULL," +
                "    user_id           INTEGER NOT NULL," +
                "    encrypted_aes_key TEXT NOT NULL," +
                "    FOREIGN KEY (message_id) REFERENCES messages(id)," +
                "    FOREIGN KEY (user_id)    REFERENCES users(id)" +
                ")"
            );
        }
    }
}
