package com.chat.server;

import com.chat.common.UITheme;
import com.chat.db.DatabaseManager;
import com.formdev.flatlaf.FlatLightLaf;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ServerUI extends JFrame {
    private JTextArea logArea;
    private JButton startStopButton;
    private JLabel statusLabel;
    private ChatServer server;
    private boolean isRunning = false;

    public ServerUI() {
        setTitle("Chatly Server Console");
        setSize(760, 520);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setMinimumSize(new Dimension(680, 460));

        JPanel root = new JPanel(new BorderLayout(0, 18));
        root.setBackground(UITheme.BACKGROUND);
        root.setBorder(new EmptyBorder(24, 28, 24, 28));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        JPanel titleGroup = new JPanel();
        titleGroup.setOpaque(false);
        titleGroup.setLayout(new BoxLayout(titleGroup, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Chatly Server");
        title.setFont(new Font("Segoe UI", Font.BOLD, 27));
        title.setForeground(UITheme.TEXT);
        JLabel subtitle = new JLabel("Connection and activity console");
        subtitle.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        subtitle.setForeground(UITheme.TEXT_MUTED);
        titleGroup.add(title);
        titleGroup.add(Box.createRigidArea(new Dimension(0, 3)));
        titleGroup.add(subtitle);
        header.add(titleGroup, BorderLayout.WEST);

        statusLabel = new JLabel("●  OFFLINE");
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        statusLabel.setForeground(UITheme.TEXT_MUTED);
        statusLabel.setBorder(new EmptyBorder(8, 12, 8, 12));
        header.add(statusLabel, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JPanel content = new JPanel(new BorderLayout(0, 14));
        content.setOpaque(false);

        JPanel cards = new JPanel(new GridLayout(1, 3, 12, 0));
        cards.setOpaque(false);
        cards.add(createInfoCard("PORT", "5000", "TCP socket"));
        cards.add(createInfoCard("DATABASE", "SQLite", "Encrypted messages"));
        cards.add(createInfoCard("PROTOCOL", "Java Object", "Client / server"));
        content.add(cards, BorderLayout.NORTH);

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Cascadia Mono", Font.PLAIN, 13));
        logArea.setBackground(new Color(26, 32, 46));
        logArea.setForeground(new Color(205, 216, 235));
        logArea.setCaretColor(Color.WHITE);
        logArea.setBorder(new EmptyBorder(14, 16, 14, 16));
        logArea.setText("[ready] Server console initialized\n");
        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        UITheme.RoundedPanel consoleCard = new UITheme.RoundedPanel(22);
        consoleCard.setBackground(new Color(26, 32, 46));
        consoleCard.setLayout(new BorderLayout());
        consoleCard.setBorder(new EmptyBorder(5, 5, 5, 5));
        consoleCard.add(scrollPane, BorderLayout.CENTER);
        content.add(consoleCard, BorderLayout.CENTER);
        root.add(content, BorderLayout.CENTER);

        startStopButton = new UITheme.RoundedButton("Start Server", UITheme.PRIMARY, UITheme.PRIMARY_DARK, 18);
        startStopButton.setPreferredSize(new Dimension(160, 44));
        startStopButton.addActionListener(e -> toggleServer());

        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setOpaque(false);
        JLabel hint = new JLabel("Start the server before opening clients");
        hint.setForeground(UITheme.TEXT_MUTED);
        hint.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        bottomPanel.add(hint, BorderLayout.WEST);
        bottomPanel.add(startStopButton, BorderLayout.EAST);
        root.add(bottomPanel, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel createInfoCard(String caption, String value, String detail) {
        UITheme.RoundedPanel card = new UITheme.RoundedPanel(20);
        card.setBackground(UITheme.SURFACE);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(12, 16, 12, 16));
        JLabel captionLabel = new JLabel(caption);
        captionLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
        captionLabel.setForeground(UITheme.TEXT_MUTED);
        JLabel valueLabel = new JLabel(value);
        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
        valueLabel.setForeground(UITheme.TEXT);
        JLabel detailLabel = new JLabel(detail);
        detailLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        detailLabel.setForeground(UITheme.TEXT_MUTED);
        card.add(captionLabel);
        card.add(Box.createRigidArea(new Dimension(0, 2)));
        card.add(valueLabel);
        card.add(detailLabel);
        return card;
    }

    private void toggleServer() {
        if (!isRunning) {
            // Khởi động DB trước khi accept client
            DatabaseManager.getInstance();
            server = new ChatServer(5000, this);
            server.start();
            startStopButton.setText("Stop Server");
            statusLabel.setText("●  ONLINE");
            statusLabel.setForeground(UITheme.SUCCESS);
            isRunning = true;
        } else {
            if (server != null) {
                server.stopServer();
            }
            startStopButton.setText("Start Server");
            statusLabel.setText("●  OFFLINE");
            statusLabel.setForeground(UITheme.TEXT_MUTED);
            isRunning = false;
        }
    }

    public void log(String message) {
        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        SwingUtilities.invokeLater(() -> {
            logArea.append("[" + time + "] " + message + "\n");
            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public static void main(String[] args) {
        FlatLightLaf.setup();
        UITheme.applyDefaults();
        SwingUtilities.invokeLater(() -> {
            ServerUI ui = new ServerUI();
            ui.setVisible(true);
            if (java.util.Arrays.asList(args).contains("--start")) {
                ui.toggleServer();
            }
        });
    }
}
