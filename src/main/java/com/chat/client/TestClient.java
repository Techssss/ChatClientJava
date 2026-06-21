package com.chat.client;

import com.chat.common.UITheme;
import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import java.awt.*;

/**
 * Entry point dùng để test — mở LoginUI thay vì khởi tạo ClientUI trực tiếp.
 * ClientUI giờ yêu cầu userId (lấy từ DB sau đăng nhập/đăng ký),
 * nên phải đi qua LoginUI.
 */
public class TestClient {
    public static void main(String[] args) {
        FlatLightLaf.setup();
        UITheme.applyDefaults();
        SwingUtilities.invokeLater(() -> new LoginUI().setVisible(true));
    }
}
