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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Explicit sign-in and account-registration screen. */
public class LoginUI extends JFrame {
    private enum AuthMode { LOGIN, REGISTER }

    private JTextField txtIp;
    private JTextField txtPort;
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JPasswordField txtConfirmPassword;
    private JPanel confirmGroup;
    private JButton btnSubmit;
    private JButton btnSignInMode;
    private JButton btnRegisterMode;
    private JLabel lblHeader;
    private JLabel lblSub;
    private JLabel lblHint;
    private AuthMode authMode = AuthMode.LOGIN;

    public LoginUI() {
        setTitle("Chatly — Sign in");
        setSize(920, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        setLayout(new BorderLayout());

        add(createBrandPanel(), BorderLayout.WEST);

        JPanel formShell = new JPanel(new GridBagLayout());
        formShell.setBackground(UITheme.BACKGROUND);
        formShell.setBorder(new EmptyBorder(28, 52, 28, 52));

        JPanel formPanel = new JPanel();
        formPanel.setOpaque(false);
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setPreferredSize(new Dimension(445, 555));

        lblHeader = new JLabel("Welcome back");
        lblHeader.setFont(new Font("Segoe UI", Font.BOLD, 31));
        lblHeader.setForeground(UITheme.TEXT);
        lblHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblSub = new JLabel("Sign in to continue your conversations.");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblSub.setForeground(UITheme.TEXT_MUTED);
        lblSub.setAlignmentX(Component.LEFT_ALIGNMENT);

        formPanel.add(lblHeader);
        formPanel.add(Box.createRigidArea(new Dimension(0, 4)));
        formPanel.add(lblSub);
        formPanel.add(Box.createRigidArea(new Dimension(0, 18)));
        formPanel.add(createModeSwitch());
        formPanel.add(Box.createRigidArea(new Dimension(0, 18)));

        JLabel lblIp = createFieldLabel("SERVER ADDRESS");
        txtIp = new JTextField("localhost");
        styleField(txtIp, "localhost or 192.168.x.x");
        JLabel lblPort = createFieldLabel("PORT");
        txtPort = new JTextField("5000");
        styleField(txtPort, "5000");

        JPanel serverRow = new JPanel(new GridLayout(1, 2, 12, 0));
        serverRow.setOpaque(false);
        serverRow.add(fieldGroup(lblIp, txtIp));
        serverRow.add(fieldGroup(lblPort, txtPort));
        serverRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 66));
        serverRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(serverRow);
        formPanel.add(Box.createRigidArea(new Dimension(0, 12)));

        JLabel lblUser = createFieldLabel("USERNAME");
        txtUsername = new JTextField();
        styleField(txtUsername, "Enter your username");
        formPanel.add(fieldGroup(lblUser, txtUsername));
        formPanel.add(Box.createRigidArea(new Dimension(0, 12)));

        JLabel lblPassword = createFieldLabel("PASSWORD");
        txtPassword = new JPasswordField();
        styleField(txtPassword, "Enter your password");
        formPanel.add(fieldGroup(lblPassword, txtPassword));
        formPanel.add(Box.createRigidArea(new Dimension(0, 12)));

        JLabel lblConfirm = createFieldLabel("CONFIRM PASSWORD");
        txtConfirmPassword = new JPasswordField();
        styleField(txtConfirmPassword, "Repeat your password");
        confirmGroup = fieldGroup(lblConfirm, txtConfirmPassword);
        formPanel.add(confirmGroup);
        formPanel.add(Box.createRigidArea(new Dimension(0, 18)));

        btnSubmit = new UITheme.RoundedButton("Sign in  →", UITheme.PRIMARY, UITheme.PRIMARY_DARK, 18);
        btnSubmit.setAlignmentX(Component.LEFT_ALIGNMENT);
        btnSubmit.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        btnSubmit.setPreferredSize(new Dimension(445, 48));
        btnSubmit.addActionListener(e -> authenticate());
        formPanel.add(btnSubmit);
        formPanel.add(Box.createRigidArea(new Dimension(0, 10)));

        lblHint = new JLabel("No account yet? Select Create account above.");
        lblHint.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblHint.setForeground(UITheme.TEXT_MUTED);
        lblHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        formPanel.add(lblHint);

        formShell.add(formPanel);
        add(formShell, BorderLayout.CENTER);
        getRootPane().setDefaultButton(btnSubmit);
        updateModeUi();
    }

    private JPanel createBrandPanel() {
        UITheme.GradientPanel panel = new UITheme.GradientPanel(
            new Color(74, 85, 224), new Color(42, 198, 218));
        panel.setPreferredSize(new Dimension(350, 0));
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(new EmptyBorder(68, 44, 52, 44));

        JLabel logo = new JLabel("✦");
        logo.setFont(new Font("Segoe UI Symbol", Font.BOLD, 42));
        logo.setForeground(Color.WHITE);
        logo.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel brand = new JLabel("Chatly");
        brand.setFont(new Font("Segoe UI", Font.BOLD, 38));
        brand.setForeground(Color.WHITE);
        brand.setAlignmentX(Component.LEFT_ALIGNMENT);
        JLabel tagline = new JLabel("<html>One account.<br>All your conversations.</html>");
        tagline.setFont(new Font("Segoe UI", Font.PLAIN, 18));
        tagline.setForeground(new Color(255, 255, 255, 225));
        tagline.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(logo);
        panel.add(Box.createRigidArea(new Dimension(0, 18)));
        panel.add(brand);
        panel.add(Box.createRigidArea(new Dimension(0, 12)));
        panel.add(tagline);
        panel.add(Box.createVerticalGlue());

        JLabel note = new JLabel("●  Private messaging  •  Secure account");
        note.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        note.setForeground(new Color(255, 255, 255, 205));
        note.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(note);
        return panel;
    }

    private JPanel createModeSwitch() {
        UITheme.RoundedPanel switchPanel = new UITheme.RoundedPanel(18);
        switchPanel.setBackground(UITheme.SURFACE_ALT);
        switchPanel.setLayout(new GridLayout(1, 2, 6, 0));
        switchPanel.setBorder(new EmptyBorder(4, 4, 4, 4));
        switchPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 46));
        switchPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        btnSignInMode = modeButton("Sign in");
        btnRegisterMode = modeButton("Create account");
        btnSignInMode.addActionListener(e -> setAuthMode(AuthMode.LOGIN));
        btnRegisterMode.addActionListener(e -> setAuthMode(AuthMode.REGISTER));
        switchPanel.add(btnSignInMode);
        switchPanel.add(btnRegisterMode);
        return switchPanel;
    }

    private JButton modeButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty("JButton.buttonType", "roundRect");
        return button;
    }

    private void setAuthMode(AuthMode mode) {
        if (authMode == mode) return;
        authMode = mode;
        txtPassword.setText("");
        txtConfirmPassword.setText("");
        updateModeUi();
    }

    private void updateModeUi() {
        boolean registering = authMode == AuthMode.REGISTER;
        setModeButtonState(btnSignInMode, !registering);
        setModeButtonState(btnRegisterMode, registering);
        confirmGroup.setVisible(registering);
        lblHeader.setText(registering ? "Create your account" : "Welcome back");
        lblSub.setText(registering
            ? "Choose a username and secure password to get started."
            : "Sign in to continue your conversations.");
        lblHint.setText(registering
            ? "Already registered? Switch back to Sign in."
            : "No account yet? Select Create account above.");
        btnSubmit.setText(registering ? "Create account  →" : "Sign in  →");
        setTitle(registering ? "Chatly — Create account" : "Chatly — Sign in");
        revalidate();
        repaint();
    }

    private void setModeButtonState(JButton button, boolean active) {
        button.setBackground(active ? UITheme.PRIMARY : UITheme.SURFACE_ALT);
        button.setForeground(active ? Color.WHITE : UITheme.TEXT_MUTED);
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
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        field.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(label);
        panel.add(Box.createRigidArea(new Dimension(0, 5)));
        panel.add(field);
        return panel;
    }

    private void authenticate() {
        String ip = txtIp.getText().trim();
        int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
        } catch (NumberFormatException e) {
            showValidationError("Port must be a valid number.");
            return;
        }

        String username = txtUsername.getText().trim();
        String password = new String(txtPassword.getPassword());
        if (username.isEmpty()) {
            showValidationError("Username cannot be empty.");
            return;
        }
        if (password.isBlank()) {
            showValidationError("Password cannot be empty.");
            return;
        }

        AuthMode requestedMode = authMode;
        if (requestedMode == AuthMode.REGISTER) {
            String confirm = new String(txtConfirmPassword.getPassword());
            if (username.length() < 3) {
                showValidationError("Username must contain at least 3 characters.");
                return;
            }
            if (password.length() < 6) {
                showValidationError("Password must contain at least 6 characters.");
                return;
            }
            if (!password.equals(confirm)) {
                showValidationError("Password confirmation does not match.");
                return;
            }
        }

        setBusy(true, requestedMode);
        SwingWorker<ClientUI, Void> worker = new SwingWorker<>() {
            @Override
            protected ClientUI doInBackground() throws Exception {
                AuthResult auth = authenticateViaServer(ip, port, username, password, requestedMode);
                ClientUI ui = new ClientUI(ip, port, username,
                    auth.userId(), auth.privateKeyB64(), auth.publicKeyB64());
                if (!ui.connect()) throw new RuntimeException("Could not connect to server");
                return ui;
            }

            @Override
            protected void done() {
                try {
                    ClientUI ui = get();
                    LoginUI.this.dispose();
                    ui.setVisible(true);
                } catch (Exception ex) {
                    String message = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(LoginUI.this, message,
                        requestedMode == AuthMode.LOGIN ? "Sign in failed" : "Registration failed",
                        JOptionPane.ERROR_MESSAGE);
                    setBusy(false, requestedMode);
                }
            }
        };
        worker.execute();
    }

    private void setBusy(boolean busy, AuthMode mode) {
        btnSubmit.setEnabled(!busy);
        btnSignInMode.setEnabled(!busy);
        btnRegisterMode.setEnabled(!busy);
        btnSubmit.setText(busy
            ? (mode == AuthMode.LOGIN ? "Signing in…" : "Creating account…")
            : (mode == AuthMode.LOGIN ? "Sign in  →" : "Create account  →"));
    }

    private AuthResult authenticateViaServer(String ip, int port, String username,
                                               String password, AuthMode mode) throws Exception {
        String passwordHash = hashPassword(password);
        Message.MessageType requestType = mode == AuthMode.LOGIN
            ? Message.MessageType.LOGIN
            : Message.MessageType.REGISTER;
        Message.MessageType successType = mode == AuthMode.LOGIN
            ? Message.MessageType.LOGIN_OK
            : Message.MessageType.REGISTER_OK;

        try (Socket tempSocket = new Socket(ip, port);
             ObjectOutputStream tempOut = new ObjectOutputStream(tempSocket.getOutputStream())) {
            tempOut.flush();
            try (ObjectInputStream tempIn = new ObjectInputStream(tempSocket.getInputStream())) {
                Message request = new Message(requestType, username, username + ":" + passwordHash);
                tempOut.writeObject(request);
                tempOut.flush();
                Message response = (Message) tempIn.readObject();

                if (response.getType() != successType) {
                    throw new RuntimeException(humanizeAuthError((String) response.getContent(), mode));
                }
                String[] parts = ((String) response.getContent()).split(":", 3);
                if (parts.length != 3) throw new RuntimeException("Invalid response from server");
                return new AuthResult(Integer.parseInt(parts[0]), parts[1], parts[2]);
            }
        }
    }

    private String humanizeAuthError(String error, AuthMode mode) {
        if (error == null || error.isBlank()) return "Authentication failed.";
        return switch (error) {
            case "Wrong password" -> "Incorrect password. Please try again.";
            case "Account not found" -> "Account does not exist. Create one first.";
            case "Username already exists" -> "This username is already taken.";
            default -> (mode == AuthMode.LOGIN ? "Sign in failed: " : "Registration failed: ") + error;
        };
    }

    private void showValidationError(String message) {
        JOptionPane.showMessageDialog(this, message, "Check your information", JOptionPane.WARNING_MESSAGE);
    }

    private String hashPassword(String password) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(password.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder();
        for (byte b : hash) result.append(String.format("%02x", b));
        return result.toString();
    }

    private record AuthResult(int userId, String publicKeyB64, String privateKeyB64) {}

    public static void main(String[] args) {
        FlatLightLaf.setup();
        UITheme.applyDefaults();
        SwingUtilities.invokeLater(() -> new LoginUI().setVisible(true));
    }
}
