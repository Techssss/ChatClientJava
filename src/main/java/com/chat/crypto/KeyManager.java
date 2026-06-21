package com.chat.crypto;

import com.chat.db.UserDAO;

import java.security.*;

/**
 * Quản lý RSA key pair cho từng user.
 * - Khi user đăng ký: generateAndSaveUserKeys() tạo key pair và lưu vào DB.
 * - Khi gửi message: getUserPublicKey() lấy public key của receiver.
 * - Khi nhận/đọc message: getUserPrivateKey() lấy private key của current user.
 */
public class KeyManager {

    private static final UserDAO userDAO = new UserDAO();

    /**
     * Tạo RSA key pair và lưu vào DB cho user.
     * Gọi ngay sau khi tạo user thành công.
     *
     * @param userId ID của user vừa được tạo
     */
    public static void generateAndSaveUserKeys(int userId) throws Exception {
        KeyPair keyPair = RSAUtil.generateKeyPair();
        String publicKeyB64  = RSAUtil.publicKeyToBase64(keyPair.getPublic());
        String privateKeyB64 = RSAUtil.privateKeyToBase64(keyPair.getPrivate());
        // Lưu key vào DB — private key KHÔNG được log ra console
        userDAO.updateKeys(userId, publicKeyB64, privateKeyB64);
    }

    /**
     * Lấy PublicKey của một user từ DB.
     *
     * @param userId ID của user cần lấy public key
     * @return PublicKey object
     */
    public static PublicKey getUserPublicKey(int userId) throws Exception {
        String b64 = userDAO.getPublicKey(userId);
        if (b64 == null) throw new IllegalStateException("Public key not found for userId=" + userId);
        return RSAUtil.publicKeyFromBase64(b64);
    }

    /**
     * Lấy PrivateKey của current user từ DB.
     * Private key chỉ được load vào memory, không bao giờ log ra console.
     *
     * @param userId ID của current user
     * @return PrivateKey object
     */
    public static PrivateKey getUserPrivateKey(int userId) throws Exception {
        String b64 = userDAO.getPrivateKey(userId);
        if (b64 == null) throw new IllegalStateException("Private key not found for userId=" + userId);
        return RSAUtil.privateKeyFromBase64(b64);
    }
}
