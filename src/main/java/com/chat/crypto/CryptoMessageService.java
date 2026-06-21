package com.chat.crypto;

import com.chat.db.MessageDAO;
import com.chat.db.MessageDAO.EncryptedMessageRecord;

import javax.crypto.SecretKey;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Service điều phối toàn bộ luồng mã hóa/giải mã tin nhắn.
 *
 * Luồng GỬI (sendEncryptedMessage):
 *   1. Lấy public key của receiver và sender từ DB
 *   2. Generate AES key + IV ngẫu nhiên
 *   3. Mã hóa plainText bằng AES-GCM
 *   4. Mã hóa AES key bằng public key của RECEIVER
 *   5. Mã hóa AES key bằng public key của SENDER (để sender đọc lại được)
 *   6. Lưu message + 2 message_keys vào DB
 *
 * Luồng ĐỌC (getDecryptedMessages):
 *   1. Query messages + encrypted_aes_key phù hợp với currentUser
 *   2. Giải mã AES key bằng private key của currentUser
 *   3. Giải mã nội dung bằng AES key
 */
public class CryptoMessageService {

    private final MessageDAO messageDAO = new MessageDAO();

    // ─────────────────────────────── Gửi tin nhắn ───────────────────────────────

    /**
     * Mã hóa và lưu tin nhắn vào DB.
     * KHÔNG có plain text nào được lưu xuống DB.
     *
     * @param senderId   ID của người gửi
     * @param receiverId ID của người nhận
     * @param plainText  nội dung gốc (chỉ tồn tại trong memory, không được log)
     * @throws Exception nếu có lỗi mã hóa hoặc DB
     */
    public void sendEncryptedMessage(int senderId, int receiverId, String plainText) throws Exception {
        // Bước 1: Lấy public key của cả sender và receiver
        PublicKey receiverPublicKey = KeyManager.getUserPublicKey(receiverId);
        PublicKey senderPublicKey   = KeyManager.getUserPublicKey(senderId);

        // Bước 2: Generate AES key và IV mới cho mỗi message
        SecretKey aesKey = AESUtil.generateAESKey();
        byte[] iv        = AESUtil.generateIV();

        // Bước 3: Mã hóa nội dung bằng AES-GCM
        String encryptedContent = AESUtil.encrypt(plainText, aesKey, iv);
        String ivB64            = AESUtil.toBase64(iv);

        // Bước 4: Lưu message vào DB (KHÔNG có plain text)
        int messageId = messageDAO.saveMessage(senderId, receiverId, encryptedContent, ivB64);
        if (messageId == -1) throw new RuntimeException("Failed to save message to DB");

        // Bước 5: Mã hóa AES key cho receiver (receiver dùng private key để đọc)
        String encryptedAesKeyForReceiver = RSAUtil.encryptAESKey(aesKey, receiverPublicKey);
        messageDAO.saveMessageKey(messageId, receiverId, encryptedAesKeyForReceiver);

        // Bước 6: Mã hóa AES key cho sender (sender cũng đọc lại được tin nhắn của mình)
        String encryptedAesKeyForSender = RSAUtil.encryptAESKey(aesKey, senderPublicKey);
        messageDAO.saveMessageKey(messageId, senderId, encryptedAesKeyForSender);

        // Plain text được GC thu hồi sau khi ra khỏi scope — không còn trong memory
    }

    // ─────────────────────────────── Đọc tin nhắn ───────────────────────────────

    /**
     * Lấy và giải mã toàn bộ tin nhắn giữa currentUser và otherUser.
     * currentUser có thể là sender hoặc receiver — đều đọc được nhờ message_keys.
     *
     * @param currentUserId  user đang đăng nhập và đọc tin nhắn
     * @param otherUserId    user còn lại trong cuộc hội thoại
     * @return danh sách MessageDTO với nội dung đã giải mã
     */
    public List<MessageDTO> getDecryptedMessages(int currentUserId, int otherUserId) throws Exception {
        // Lấy private key của currentUser — chỉ load vào memory, không log
        PrivateKey currentUserPrivateKey = KeyManager.getUserPrivateKey(currentUserId);

        List<EncryptedMessageRecord> records = messageDAO.getMessages(currentUserId, otherUserId);
        List<MessageDTO> result = new ArrayList<>();

        for (EncryptedMessageRecord record : records) {
            try {
                // Giải mã AES key bằng private key của currentUser
                SecretKey aesKey = RSAUtil.decryptAESKey(record.encryptedAesKey(), currentUserPrivateKey);

                // Giải mã nội dung bằng AES key
                byte[] iv = AESUtil.fromBase64(record.ivB64());
                String plainText = AESUtil.decrypt(record.encryptedContent(), aesKey, iv);

                result.add(new MessageDTO(
                    record.messageId(),
                    record.senderId(),
                    record.receiverId(),
                    plainText,          // chỉ tồn tại trong memory của client
                    record.createdAt()
                ));
            } catch (Exception e) {
                // Không ném exception để không block toàn bộ danh sách
                System.err.println("[CryptoMessageService] Failed to decrypt messageId="
                    + record.messageId() + ": " + e.getMessage());
                result.add(new MessageDTO(
                    record.messageId(),
                    record.senderId(),
                    record.receiverId(),
                    "[Không thể giải mã tin nhắn]",
                    record.createdAt()
                ));
            }
        }
        return result;
    }

    // ─────────────────────────────── DTO ───────────────────────────────

    /**
     * Data Transfer Object chứa thông tin tin nhắn đã giải mã.
     * Plain text chỉ tồn tại ở client-side, không được lưu DB.
     */
    public record MessageDTO(
        int messageId,
        int senderId,
        int receiverId,
        String plainText,   // nội dung đã giải mã — chỉ dùng để hiển thị UI
        String createdAt
    ) {}
}
