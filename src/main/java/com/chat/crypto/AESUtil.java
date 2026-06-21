package com.chat.crypto;

import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Utility class cho AES-256-GCM encryption/decryption.
 *
 * Lựa chọn kỹ thuật:
 * - AES/GCM/NoPadding: vừa mã hóa vừa xác thực tính toàn vẹn (AEAD).
 * - Key 256-bit: an toàn tới năm 2030+ theo NIST.
 * - IV 12 bytes (96-bit): kích thước khuyến nghị cho GCM.
 * - GCM Tag 128-bit: mức bảo vệ tối đa.
 */
public class AESUtil {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_SIZE_BITS = 256;
    private static final int IV_SIZE_BYTES = 12;     // GCM khuyến nghị 12 bytes
    private static final int GCM_TAG_LENGTH = 128;   // bits

    // ─────────────────────────────── Key / IV Generation ───────────────────────────────

    /**
     * Tạo AES SecretKey 256-bit ngẫu nhiên.
     */
    public static SecretKey generateAESKey() throws Exception {
        KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
        keyGen.init(KEY_SIZE_BITS, new SecureRandom());
        return keyGen.generateKey();
    }

    /**
     * Tạo IV ngẫu nhiên 12 bytes cho AES-GCM.
     * IV phải unique cho mỗi (key, message) pair.
     */
    public static byte[] generateIV() {
        byte[] iv = new byte[IV_SIZE_BYTES];
        new SecureRandom().nextBytes(iv);
        return iv;
    }

    // ─────────────────────────────── Encrypt ───────────────────────────────

    /**
     * Mã hóa plain text bằng AES/GCM/NoPadding.
     *
     * @param plainText nội dung gốc (không được null hoặc empty)
     * @param key       AES SecretKey 256-bit
     * @param iv        IV 12 bytes (duy nhất cho mỗi message)
     * @return ciphertext đã encode Base64 (bao gồm GCM authentication tag)
     */
    public static String encrypt(String plainText, SecretKey key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec paramSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, paramSpec);
        byte[] cipherBytes = cipher.doFinal(plainText.getBytes("UTF-8"));
        return Base64.getEncoder().encodeToString(cipherBytes);
    }

    // ─────────────────────────────── Decrypt ───────────────────────────────

    /**
     * Giải mã ciphertext Base64 bằng AES/GCM/NoPadding.
     * Nếu ciphertext bị giả mạo, GCM tag verification sẽ ném AEADBadTagException.
     *
     * @param encryptedTextBase64 ciphertext dạng Base64
     * @param key                 AES SecretKey (phải khớp với key dùng lúc encrypt)
     * @param iv                  IV (phải khớp với IV dùng lúc encrypt)
     * @return plain text gốc
     */
    public static String decrypt(String encryptedTextBase64, SecretKey key, byte[] iv) throws Exception {
        byte[] cipherBytes = Base64.getDecoder().decode(encryptedTextBase64);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec paramSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, paramSpec);
        byte[] plainBytes = cipher.doFinal(cipherBytes);
        return new String(plainBytes, "UTF-8");
    }

    // ─────────────────────────────── Base64 Helpers ───────────────────────────────

    /** Encode byte array sang Base64 string. */
    public static String toBase64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    /** Decode Base64 string về byte array. */
    public static byte[] fromBase64(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    /** Chuyển SecretKey về Base64 để lưu (nếu cần). */
    public static String secretKeyToBase64(SecretKey key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    /** Khôi phục SecretKey từ Base64. */
    public static SecretKey secretKeyFromBase64(String base64) {
        byte[] keyBytes = Base64.getDecoder().decode(base64);
        return new SecretKeySpec(keyBytes, ALGORITHM);
    }
}
