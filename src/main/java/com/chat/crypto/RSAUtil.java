package com.chat.crypto;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.security.*;
import java.security.spec.*;
import java.util.Base64;

/**
 * Utility class cho RSA-OAEP encryption/decryption.
 *
 * Lựa chọn kỹ thuật:
 * - RSA/ECB/OAEPWithSHA-256AndMGF1Padding: chống chosen-ciphertext attack.
 * - Key 2048-bit: cân bằng giữa security và performance.
 * - Chỉ dùng để encrypt/decrypt AES key (KHÔNG dùng để encrypt message trực tiếp).
 * - PRIVATE KEY KHÔNG ĐƯỢC LOG RA CONSOLE bao giờ.
 */
public class RSAUtil {

    private static final String ALGORITHM = "RSA";
    // RSA-OAEP với SHA-256 — algorithm được NIST và PKCS#1 v2.2 khuyến nghị
    private static final String TRANSFORMATION = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    private static final int KEY_SIZE_BITS = 2048;

    // ─────────────────────────────── Key Generation ───────────────────────────────

    /**
     * Tạo RSA KeyPair 2048-bit mới.
     * Gọi một lần khi user đăng ký — kết quả lưu vào DB.
     */
    public static KeyPair generateKeyPair() throws Exception {
        KeyPairGenerator gen = KeyPairGenerator.getInstance(ALGORITHM);
        gen.initialize(KEY_SIZE_BITS, new SecureRandom());
        return gen.generateKeyPair();
    }

    // ─────────────────────────────── Encrypt AES Key ───────────────────────────────

    /**
     * Mã hóa AES SecretKey bằng RSA public key của receiver.
     *
     * @param aesKey    AES key cần bảo vệ
     * @param publicKey RSA public key của receiver
     * @return AES key đã mã hóa, dạng Base64
     */
    public static String encryptAESKey(SecretKey aesKey, PublicKey publicKey) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        // Dùng OAEPParameterSpec tường minh để đảm bảo SHA-256 cả ở hash và MGF
        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
            "SHA-256", "MGF1",
            new MGF1ParameterSpec("SHA-256"),
            PSource.PSpecified.DEFAULT
        );
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, oaepParams);
        byte[] encryptedKey = cipher.doFinal(aesKey.getEncoded());
        return Base64.getEncoder().encodeToString(encryptedKey);
    }

    // ─────────────────────────────── Decrypt AES Key ───────────────────────────────

    /**
     * Giải mã AES SecretKey bằng RSA private key của current user.
     *
     * @param encryptedAESKeyBase64 AES key đã mã hóa dạng Base64
     * @param privateKey            RSA private key của current user
     * @return AES SecretKey đã giải mã
     */
    public static SecretKey decryptAESKey(String encryptedAESKeyBase64,
                                          PrivateKey privateKey) throws Exception {
        byte[] encryptedBytes = Base64.getDecoder().decode(encryptedAESKeyBase64);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        OAEPParameterSpec oaepParams = new OAEPParameterSpec(
            "SHA-256", "MGF1",
            new MGF1ParameterSpec("SHA-256"),
            PSource.PSpecified.DEFAULT
        );
        cipher.init(Cipher.DECRYPT_MODE, privateKey, oaepParams);
        byte[] aesKeyBytes = cipher.doFinal(encryptedBytes);
        return AESUtil.secretKeyFromBase64(Base64.getEncoder().encodeToString(aesKeyBytes));
    }

    // ─────────────────────────────── Key Serialization ───────────────────────────────

    /** Encode PublicKey sang Base64 (X.509 DER format). */
    public static String publicKeyToBase64(PublicKey publicKey) {
        return Base64.getEncoder().encodeToString(publicKey.getEncoded());
    }

    /**
     * Encode PrivateKey sang Base64 (PKCS#8 DER format).
     * CẢNH BÁO: Không log, không in ra console.
     */
    public static String privateKeyToBase64(PrivateKey privateKey) {
        return Base64.getEncoder().encodeToString(privateKey.getEncoded());
    }

    /** Khôi phục PublicKey từ Base64. */
    public static PublicKey publicKeyFromBase64(String base64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
        return factory.generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    /** Khôi phục PrivateKey từ Base64. */
    public static PrivateKey privateKeyFromBase64(String base64) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        KeyFactory factory = KeyFactory.getInstance(ALGORITHM);
        return factory.generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }
}
