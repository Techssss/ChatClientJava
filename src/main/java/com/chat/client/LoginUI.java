package com.chat.client;

import com.chat.common.Message;
import com.chat.common.UITheme;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.security.MessageDigest;

/**
 * Màn hình đăng nhập / đăng ký.
 *
 * Client KHÔNG trực tiếp truy cập DB. Thay vào đó:
 * 1. Mở kết nối socket tạm tới server.
 * 2. Gửi REGISTER message với "username:passwordHash".
 * 3. Server xử lý (tạo user + RSA key nếu mới, hoặc xác thực nếu đã có).
 * 4. Server trả REGISTER_OK (kèm userId) hoặc REGISTER_FAIL.
 * 5. Sau khi thành công, đóng socket tạm và mở ClientUI với userId đã biết.
 */
public class LoginUI extends JFrame {

    private JTextField    txtIp, txtPort, txtUsername;
    private JPasswordField txtPassword;
    private JButton       btnLogin;

    public LoginUI() {
        setTitle("Chatly — Sign in");
        setSize(880, 560);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        setLayout(new BorderLayout());

        UITheme.GradientPanel brandPanel = new UITheme.GradientPanel(
            new Color(74, 85, 224), new Color(42, 198, 218));
        brandPanel.setPreferredSize(new Dimension(340, 0));
        brandPanel.setLayout(new BoxLayout(brandPanel, BoxLayout.Y_AXIS));
        brandPanel.setBorder(new EmptyBorder(64, 42, 50, 42));

        JLabel logo = new JLabel("✦");
        logo.setFont(new Font("Segoe UI Symbol", Font.BOLD, 42));
        logo.setForeground(Color.WHITE);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel brand = new JLabel("Chatly");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 38));
        brand.setForeground(Color.WHITE);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel tagline = new JLabel("<html>Conversations that feel<br>simple, fast and personal.</html>");
        tagline.setFont(new Font("Segoe UI", Font.PLAIN, 17));
        tagline.setForeground(new Color(255, 255, 255, 220));
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);

        brandPanel.add(logo);
        brandPanel.add(Box.createRigidArea(new Dimension(0, 18)));
        brandPanel.add(brand);
        brandPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        brandPanel.add(tagline);
        brandPanel.add(Box.createVerticalGlue());

        JLabel secureNote = new JLabel("●  Private messaging  •  Secure connection");
        secureNote.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        secureNote.setForeground(new Color(255, 255, 255, 205));
        secureNote.setAlignmentX(Component.LEFT_ALIGNMENT);
        brandPanel.add(secureNote);
        add(brandPanel, BorderLayout.WEST);

        JPanel formShell = new JPanel(new GridBagLayout());
        formShell.setBackground(UITheme.BACKGROUND);
        formShell.setBorder(new EmptyBorder(35, 56, 35, 56));

        JPanel formPanel = new JPanel();
        formPanel.setOpaque(false);
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setPreferredSize(new Dimension(410, 440));

        JLabel lblHeader = new JLabel("Welcome back");
        lblHeader.setFont(new Font("Segoe UI", Font.BOLD, 31));
        lblHeader.setForeground(UITheme.TEXT);
        lblHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel lblSub = new JLabel("Sign in or create a new account automatically.");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSub.setForeground(UITheme.TEXT_MUTED);
        lblSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblIp = createFieldLabel("SERVER ADDRESS");
        txtIp = new JTextField("localhost");
        styleField(txtIp, "localhost");
        JLabel lblPort = createFieldLabel("PORT");
        txtPort = new JTextField("5000");
        styleField(txtPort, "5000");
        JLabel lblUser = createFieldLabel("USERNAME");
        txtUsername = new JTextField("");
        styleField(txtUsername, "Enter your username");
        JLabel lblPwd = createFieldLabel("PASSWORD");
        txtPassword = new JPasswordField("");
        styleField(txtPassword, "Enter your password");

        btnLogin = new UITheme.RoundedButton("Connect to Chat  →", UITheme.PRIMARY, UITheme.PRIMARY_DARK, 18);
        btnLogin.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        btnLogin.setPreferredSize(new Dimension(410, 48));
        btnLogin.addActionListener(e -> connect());

        formPanel.add(lblHeader);
        formPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        formPanel.add(lblSub);
        formPanel.add(Box.createRigidArea(new Dimension(0, 24)));
        formPanel.add(lblIp);
        formPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        formPanel.add(txtIp);
        formPanel.add(Box.createRigidArea(new Dimension(0, 12)));

        JPanel row = new JPanel(new GridLayout(1, 2, 12, 0));
        row.setOpaque(false);
        JPanel portBox = fieldGroup(lblPort, txtPort);
        JPanel userBox = fieldGroup(lblUser, txtUsername);
        row.add(portBox);
        row.add(userBox);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        row.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(row);
        formPanel.add(Box.createRigidArea(new Dimension(0, 12)));
        formPanel.add(lblPwd);
        formPanel.add(Box.createRigidArea(new Dimension(0, 5)));
        formPanel.add(txtPassword);
        formPanel.add(Box.createRigidArea(new Dimension(0, 20)));
        formPanel.add(btnLogin);
        formPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        JLabel lblHint = new JLabel("New here? Your account will be created on first sign in.");
        lblHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblHint.setForeground(UITheme.TEXT_MUTED);
        lblHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(lblHint);

        formShell.add(formPanel);
        add(formShell, BorderLayout.CENTER);

        getRootPane().setDefaultButton(btnLogin);
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 11));
        label.setForeground(UITheme.TEXT_MUTED);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private void styleField(JTextField field, String placeholder) {
        field.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        field.setForeground(UITheme.TEXT);
        field.setBackground(Color.WHITE);
        field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        field.setPreferredSize(new Dimension(180, 42));
        field.putClientProperty("JTextField.placeholderText", placeholder);
        field.putClientProperty("JTextField.roundRect", true);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private JPanel fieldGroup(JLabel label, JTextField field) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createRigidArea(new Dimension(0, 5)));
        panel.add(field);
        return panel;
    }

    private void connect() {
        String ip = txtIp.getText().trim();
        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Invalid port", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword()).trim();

        if (username.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Username cannot be empty", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Password cannot be empty", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        btnLogin.setText("Connecting...");
        btnLogin.setEnabled(false);

        SwingWorker<ClientUI, Void> worker = new SwingWorker<>() {
            @Override
            protected ClientUI doInBackground() throws Exception {
                // Bước 1: Đăng nhập/đăng ký qua server, nhận về userId + keys
                AuthResult auth = registerViaServer(ip, port, username, password);

                // Bước 2: Mở ClientUI với đầy đủ crypto context
                ClientUI ui = new ClientUI(ip, port, username,
                    auth.userId(), auth.privateKeyB64(), auth.publicKeyB64());
                boolean connected = ui.connect();
                if (!connected) throw new RuntimeException("Could not connect to server");
                return ui;
            }

            @Override
            protected void done() {
                try {
                    ClientUI ui = get();
                    LoginUI.this.dispose();
                    ui.setVisible(true);
                } catch (Exception ex) {
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(LoginUI.this, msg, "Connection Failed", JOptionPane.ERROR_MESSAGE);
                    btnLogin.setText("Connect to Chat");
                    btnLogin.setEnabled(true);
                }
            }
        };
        worker.execute();
    }

    /**
     * Kết quả đăng ký / đăng nhập từ server.
     */
    private record AuthResult(int userId, String publicKeyB64, String privateKeyB64) {}

    /**
     * Mở socket tạm, gửi REGISTER, đọc response, đóng socket.
     * Server tự tạo RSA key pair nếu user mới, trả về userId + key.
     */
    private AuthResult registerViaServer(String ip, int port, String username, String password) throws Exception {
        String passwordHash = hashPassword(password);

        try (java.net.Socket tempSocket = new java.net.Socket(ip, port)) {
            java.io.ObjectOutputStream tempOut = new java.io.ObjectOutputStream(tempSocket.getOutputStream());
            tempOut.flush();
            java.io.ObjectInputStream tempIn = new java.io.ObjectInputStream(tempSocket.getInputStream());

            // Gửi REGISTER: content = "username:passwordHash"
            Message regMsg = new Message(Message.MessageType.REGISTER, username,
                username + ":" + passwordHash);
            tempOut.writeObject(regMsg);
            tempOut.flush();

            // Đọc response
            Message response = (Message) tempIn.readObject();

            if (response.getType() == Message.MessageType.REGISTER_OK) {
                // content = "userId:publicKeyB64:privateKeyB64"
                String[] parts = ((String) response.getContent()).split(":", 3);
                int userId        = Integer.parseInt(parts[0]);
                String pubKeyB64  = parts[1];
                String privKeyB64 = parts[2];
                return new AuthResult(userId, pubKeyB64, privKeyB64);
            } else {
                String errMsg = (String) response.getContent();
                if (errMsg != null && errMsg.contains("Wrong password"))
                    throw new RuntimeException("Sai mật khẩu cho tài khoản: " + username);
                throw new RuntimeException("Đăng ký thất bại: " + errMsg);
            }
        }
    }

    /** SHA-256 hash của password. Production nên dùng BCrypt. */
    private String hashPassword(String password) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(password.getBytes("UTF-8"));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static void main(String[] args) {
        FlatLightLaf.setup();
        UITheme.applyDefaults();
        SwingUtilities.invokeLater(() -> new LoginUI().setVisible(true));
    }
}
