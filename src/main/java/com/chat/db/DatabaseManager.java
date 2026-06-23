package com.chat.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.*;

/**
 * Singleton DatabaseManager — chỉ dùng ở SERVER side.
 * Client KHÔNG trực tiếp truy cập DB này.
 *
 * File DB lưu trong thư mục data của project/thư mục chạy server.
 */
public class DatabaseManager {

    private static final Path DATA_DIR = Path.of(System.getProperty("user.dir"), "data")
        .toAbsolutePath().normalize();
    private static final Path DB_FILE = DATA_DIR.resolve("chat_server.db");
    private static final Path LEGACY_DB_FILE = Path.of(System.getProperty("user.home"), "chat_server.db")
        .toAbsolutePath().normalize();
    private static final String DB_PATH = DB_FILE.toString();
    private static final String DB_URL  = "jdbc:sqlite:" + DB_PATH;

    private static DatabaseManager instance;
    private Connection connection;

    private DatabaseManager() {
        try {
            Class.forName("org.sqlite.JDBC");
            prepareDatabaseFile();

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

    /**
     * Tạo thư mục data và tự sao chép DB cũ từ user.home trong lần chạy đầu tiên.
     * File cũ được giữ lại như một bản dự phòng.
     */
    private void prepareDatabaseFile() throws Exception {
        Files.createDirectories(DATA_DIR);
        if (!Files.exists(DB_FILE) && Files.isRegularFile(LEGACY_DB_FILE)) {
            Files.copy(LEGACY_DB_FILE, DB_FILE, StandardCopyOption.COPY_ATTRIBUTES);
            System.out.println("[DB] Migrated legacy database from: " + LEGACY_DB_FILE);
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

            // --- Nội dung không phải encrypted text và lịch sử cuộc gọi ---
            stmt.execute(
                "CREATE TABLE IF NOT EXISTS conversation_items (" +
                "    id           INTEGER PRIMARY KEY AUTOINCREMENT," +
                "    sender_id    INTEGER NOT NULL," +
                "    receiver_id  INTEGER NOT NULL," +
                "    item_type    TEXT NOT NULL," +
                "    content_text TEXT," +
                "    file_name    TEXT," +
                "    file_data    BLOB," +
                "    created_at   TEXT DEFAULT (STRFTIME('%Y-%m-%d %H:%M:%f', 'NOW'))," +
                "    FOREIGN KEY (sender_id)   REFERENCES users(id)," +
                "    FOREIGN KEY (receiver_id) REFERENCES users(id)" +
                ")"
            );
            stmt.execute(
                "CREATE INDEX IF NOT EXISTS idx_conversation_items_pair_time " +
                "ON conversation_items(sender_id, receiver_id, created_at)"
            );
        }
    }
}
