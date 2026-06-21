package com.chat.client;

import com.chat.common.Message;
import com.chat.common.SteganoUtils;
import com.chat.common.UITheme;
import com.chat.crypto.AESUtil;
import com.chat.crypto.RSAUtil;

import javax.crypto.SecretKey;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.file.Files;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;


public class ClientUI extends JFrame {
    private ChatClient client;
    private JPanel chatPanel;
    private JTextField txtInput;
    private JList<String> userList;
    private DefaultListModel<String> listModel;
    private JTextField txtSearch;
    private final List<String> onlineUsers = new ArrayList<>();
    private String selectedUser = null;
    private JLabel chatHeaderLabel;
    private JLabel chatStatusLabel;
    private JLabel chatAvatarLabel;
    private JLabel onlineCountLabel;
    private JScrollPane chatScroll;
    private Map<String, JPanel> chatPanels = new HashMap<>();

    private AudioHandler audioHandler;
    private VideoHandler videoHandler;

    // ── Crypto context ────────────────────────────────────────────────────────
    /** Numeric userId của current user. */
    private int currentUserId;
    /** Private key của current user — được truyền từ LoginUI sau khi xác thực. */
    private PrivateKey currentUserPrivateKey;
    /** Public key của current user — để mã hóa AES key cho sender. */
    private PublicKey currentUserPublicKey;
    /** Cache public key theo username (nhận qua KEY_RESPONSE từ server). */
    private final Map<String, PublicKey> publicKeyCache = new HashMap<>();
    /** CompletableFuture để chờ KEY_RESPONSE bất đồng bộ. */
    private final Map<String, CompletableFuture<PublicKey>> keyFutures = new HashMap<>();
    // ─────────────────────────────────────────────────────────────────────────

    private final Color colorMe = UITheme.PRIMARY;
    private final Color colorOther = UITheme.SURFACE_ALT;
    private final Color colorBg = UITheme.BACKGROUND;

    public ClientUI(String ip, int port, String username, int userId) {
        this(ip, port, username, userId, null, null);
    }

    /**
     * Constructor đầy đủ: client có private/public key sau khi đăng nhập.
     * LoginUI gọi constructor này sau khi nhận key từ server.
     */
    public ClientUI(String ip, int port, String username, int userId,
                    String privateKeyB64, String publicKeyB64) {
        this.currentUserId = userId;
        try {
            if (privateKeyB64 != null)
                this.currentUserPrivateKey = RSAUtil.privateKeyFromBase64(privateKeyB64);
            if (publicKeyB64 != null)
                this.currentUserPublicKey = RSAUtil.publicKeyFromBase64(publicKeyB64);
        } catch (Exception e) {
            System.err.println("[ClientUI] Failed to load keys: " + e.getMessage());
        }
        setTitle("Chatly — " + username);
        setSize(1120, 720);
        setMinimumSize(new Dimension(940, 620));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        getContentPane().setBackground(colorBg);

        client = new ChatClient(ip, port, username, this);
        audioHandler = new AudioHandler(client);
        videoHandler = new VideoHandler(client, audioHandler);

        setLayout(new BorderLayout());

        // --- LEFT PANEL (Users) ---
        JPanel leftPanel = new JPanel(new BorderLayout());
        leftPanel.setPreferredSize(new Dimension(292, 0));
        leftPanel.setBackground(UITheme.SURFACE);
        leftPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, UITheme.BORDER));

        JPanel sidebarTop = new JPanel();
        sidebarTop.setOpaque(false);
        sidebarTop.setLayout(new BoxLayout(sidebarTop, BoxLayout.Y_AXIS));
        sidebarTop.setBorder(new EmptyBorder(22, 20, 12, 20));

        JPanel brandRow = new JPanel(new BorderLayout());
        brandRow.setOpaque(false);
        brandRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        JLabel brand = new JLabel("✦  Chatly");
        brand.setFont(new Font("Segoe UI Symbol", Font.BOLD, 24));
        brand.setForeground(UITheme.TEXT);
        JLabel menu = new JLabel("•••");
        menu.setFont(new Font("Segoe UI", Font.BOLD, 16));
        menu.setForeground(UITheme.TEXT_MUTED);
        brandRow.add(brand, BorderLayout.WEST);
        brandRow.add(menu, BorderLayout.EAST);
        sidebarTop.add(brandRow);
        sidebarTop.add(Box.createRigidArea(new Dimension(0, 20)));

        UITheme.RoundedPanel profileCard = new UITheme.RoundedPanel(20);
        profileCard.setBackground(UITheme.SURFACE_ALT);
        profileCard.setLayout(new BorderLayout(12, 0));
        profileCard.setBorder(new EmptyBorder(12, 12, 12, 12));
        profileCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 70));
        profileCard.add(UITheme.avatar(username, 44), BorderLayout.WEST);
        JPanel profileText = new JPanel();
        profileText.setOpaque(false);
        profileText.setLayout(new BoxLayout(profileText, BoxLayout.Y_AXIS));
        JLabel profileName = new JLabel(username);
        profileName.setFont(new Font("Segoe UI", Font.BOLD, 14));
        profileName.setForeground(UITheme.TEXT);
        JLabel profileState = new JLabel("●  Online");
        profileState.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        profileState.setForeground(UITheme.SUCCESS);
        profileText.add(profileName);
        profileText.add(Box.createRigidArea(new Dimension(0, 2)));
        profileText.add(profileState);
        profileCard.add(profileText, BorderLayout.CENTER);
        sidebarTop.add(profileCard);
        sidebarTop.add(Box.createRigidArea(new Dimension(0, 16)));

        txtSearch = new JTextField();
        txtSearch.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        txtSearch.setBackground(UITheme.BACKGROUND);
        txtSearch.setForeground(UITheme.TEXT);
        txtSearch.setBorder(new EmptyBorder(10, 14, 10, 14));
        txtSearch.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        txtSearch.putClientProperty("JTextField.placeholderText", "Search conversations...");
        txtSearch.putClientProperty("JTextField.leadingIcon", null);
        txtSearch.putClientProperty("JTextField.roundRect", true);
        txtSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            private void refresh() { filterUserList(txtSearch.getText()); }
            @Override public void insertUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent e) { refresh(); }
        });
        sidebarTop.add(txtSearch);
        sidebarTop.add(Box.createRigidArea(new Dimension(0, 18)));

        JPanel listHeading = new JPanel(new BorderLayout());
        listHeading.setOpaque(false);
        listHeading.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        JLabel lblChats = new JLabel("MESSAGES");
        lblChats.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblChats.setForeground(UITheme.TEXT_MUTED);
        onlineCountLabel = new JLabel("0 online");
        onlineCountLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        onlineCountLabel.setForeground(UITheme.TEXT_MUTED);
        listHeading.add(lblChats, BorderLayout.WEST);
        listHeading.add(onlineCountLabel, BorderLayout.EAST);
        sidebarTop.add(listHeading);
        leftPanel.add(sidebarTop, BorderLayout.NORTH);

        listModel = new DefaultListModel<>();
        userList = new JList<>(listModel);
        userList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        userList.setCellRenderer(new UserListCellRenderer());
        userList.setBackground(UITheme.SURFACE);
        userList.setFixedCellHeight(68);
        userList.setBorder(new EmptyBorder(0, 10, 16, 10));
        userList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && userList.getSelectedValue() != null) {
                selectedUser = userList.getSelectedValue();
                if (chatHeaderLabel != null) {
                    chatHeaderLabel.setText(selectedUser);
                    chatStatusLabel.setText("Online now");
                    chatAvatarLabel.setText(selectedUser.substring(0, 1).toUpperCase());
                }
                switchChatPanel(selectedUser);
            }
        });

        JScrollPane userScroll = new JScrollPane(userList);
        userScroll.setBorder(null);
        userScroll.getViewport().setBackground(UITheme.SURFACE);
        leftPanel.add(userScroll, BorderLayout.CENTER);

        add(leftPanel, BorderLayout.WEST);

        // --- RIGHT PANEL (Chat Area) ---
        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(colorBg);

        // Header
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(UITheme.SURFACE);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, UITheme.BORDER),
            new EmptyBorder(12, 22, 12, 18)));

        JPanel headerLeft = new JPanel(new BorderLayout(12, 0));
        headerLeft.setOpaque(false);
        chatAvatarLabel = UITheme.avatar("?", 44);
        headerLeft.add(chatAvatarLabel, BorderLayout.WEST);
        JPanel headerText = new JPanel();
        headerText.setOpaque(false);
        headerText.setLayout(new BoxLayout(headerText, BoxLayout.Y_AXIS));
        chatHeaderLabel = new JLabel("Select a user to chat");
        chatHeaderLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        chatHeaderLabel.setForeground(UITheme.TEXT);
        chatStatusLabel = new JLabel("Choose a conversation from the sidebar");
        chatStatusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        chatStatusLabel.setForeground(UITheme.TEXT_MUTED);
        headerText.add(chatHeaderLabel);
        headerText.add(Box.createRigidArea(new Dimension(0, 2)));
        headerText.add(chatStatusLabel);
        headerLeft.add(headerText, BorderLayout.CENTER);

        headerPanel.add(headerLeft, BorderLayout.WEST);

        JPanel callButtonsPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        callButtonsPanel.setOpaque(false);
        JLabel encryptedBadge = new JLabel("  Encrypted  ", UITheme.icon("lock", 14), SwingConstants.LEFT);
        encryptedBadge.setOpaque(true);
        encryptedBadge.setBackground(new Color(229, 248, 240));
        encryptedBadge.setForeground(UITheme.SUCCESS);
        encryptedBadge.setFont(new Font("Segoe UI", Font.BOLD, 11));
        encryptedBadge.setBorder(new EmptyBorder(8, 8, 8, 8));
        JButton btnVoice = createIconButton("phone", "Voice Call");
        btnVoice.addActionListener(e -> startVoiceCall());
        JButton btnVideo = createIconButton("video", "Video Call");
        btnVideo.addActionListener(e -> startVideoCall());
        callButtonsPanel.add(encryptedBadge);
        callButtonsPanel.add(btnVoice);
        callButtonsPanel.add(btnVideo);
        headerPanel.add(callButtonsPanel, BorderLayout.EAST);

        rightPanel.add(headerPanel, BorderLayout.NORTH);

        // Chat History (Placeholder initially)
        JPanel placeholder = new JPanel(new GridBagLayout());
        placeholder.setBackground(colorBg);
        JPanel emptyState = new JPanel();
        emptyState.setOpaque(false);
        emptyState.setLayout(new BoxLayout(emptyState, BoxLayout.Y_AXIS));
        JLabel emptyIcon = new JLabel("✦");
        emptyIcon.setFont(new Font("Segoe UI Symbol", Font.BOLD, 42));
        emptyIcon.setForeground(UITheme.PRIMARY);
        emptyIcon.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel lblPlaceholder = new JLabel("Your conversations live here");
        lblPlaceholder.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblPlaceholder.setForeground(UITheme.TEXT);
        lblPlaceholder.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel lblPlaceholderHint = new JLabel("Choose someone from the sidebar and say hello.");
        lblPlaceholderHint.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblPlaceholderHint.setForeground(UITheme.TEXT_MUTED);
        lblPlaceholderHint.setAlignmentX(Component.CENTER_ALIGNMENT);
        emptyState.add(emptyIcon);
        emptyState.add(Box.createRigidArea(new Dimension(0, 12)));
        emptyState.add(lblPlaceholder);
        emptyState.add(Box.createRigidArea(new Dimension(0, 5)));
        emptyState.add(lblPlaceholderHint);
        placeholder.add(emptyState);

        chatScroll = new JScrollPane(placeholder);
        chatScroll.setBorder(null);
        chatScroll.getViewport().setBackground(colorBg);
        chatScroll.getVerticalScrollBar().setUnitIncrement(16);
        rightPanel.add(chatScroll, BorderLayout.CENTER);

        // Bottom Input
        JPanel composerShell = new JPanel(new BorderLayout());
        composerShell.setBackground(colorBg);
        composerShell.setBorder(new EmptyBorder(10, 18, 16, 18));
        UITheme.RoundedPanel bottomPanel = new UITheme.RoundedPanel(24);
        bottomPanel.setLayout(new BorderLayout(10, 10));
        bottomPanel.setBackground(UITheme.SURFACE);
        bottomPanel.setBorder(new EmptyBorder(9, 12, 9, 12));

        JPanel inputTools = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        inputTools.setOpaque(false);
        
        JButton btnIcon = createIconButton("smile", "Send Icon/Sticker");
        btnIcon.addActionListener(e -> showIconPicker(btnIcon));
        JButton btnImage = createIconButton("image", "Send Image");
        btnImage.addActionListener(e -> sendImage());
        JButton btnFile = createIconButton("paperclip", "Send File");
        btnFile.addActionListener(e -> sendFile());
        JButton btnStego = createIconButton("shield", "Send Steganography Msg");
        btnStego.addActionListener(e -> sendSteganography());
        JButton btnSTT = createIconButton("mic", "Speech to Text");
        btnSTT.addActionListener(e -> speechToText());

        inputTools.add(btnIcon);
        inputTools.add(btnImage);
        inputTools.add(btnFile);
        inputTools.add(btnStego);
        inputTools.add(btnSTT);

        txtInput = new JTextField();
        txtInput.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
        txtInput.setBackground(UITheme.SURFACE_ALT);
        txtInput.setForeground(UITheme.TEXT);
        txtInput.setBorder(new EmptyBorder(10, 14, 10, 14));
        txtInput.putClientProperty("JTextField.placeholderText", "Write a message...");
        txtInput.putClientProperty("JTextField.roundRect", true);
        txtInput.addActionListener(e -> sendText());

        JButton btnSend = new UITheme.RoundedButton("Send", UITheme.PRIMARY, UITheme.PRIMARY_DARK, 18);
        btnSend.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btnSend.setPreferredSize(new Dimension(66, 42));
        btnSend.setToolTipText("Send");
        btnSend.addActionListener(e -> sendText());

        bottomPanel.add(inputTools, BorderLayout.WEST);
        bottomPanel.add(txtInput, BorderLayout.CENTER);
        bottomPanel.add(btnSend, BorderLayout.EAST);

        composerShell.add(bottomPanel, BorderLayout.CENTER);
        rightPanel.add(composerShell, BorderLayout.SOUTH);

        add(rightPanel, BorderLayout.CENTER);
    }

    private void switchChatPanel(String user) {
        JPanel panel = getChatPanelForUser(user);
        chatScroll.setViewportView(panel);
        chatScroll.revalidate();
        chatScroll.repaint();
        scrollToBottom(panel);
    }

    private JPanel getChatPanelForUser(String user) {
        if (!chatPanels.containsKey(user)) {
            JPanel p = new JPanel() {
                @Override
                protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setColor(new Color(91, 108, 255, 12));
                    for (int y = 24; y < getHeight(); y += 38) {
                        for (int x = 24; x < getWidth(); x += 38) {
                            g2.fillOval(x, y, 3, 3);
                        }
                    }
                    g2.dispose();
                }
            };
            p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
            p.setBackground(colorBg);
            p.setBorder(new EmptyBorder(16, 10, 16, 10));

            JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
            dateRow.setOpaque(false);
            JLabel dateLabel = new JLabel("  Today  ");
            dateLabel.setOpaque(true);
            dateLabel.setBackground(new Color(232, 236, 245));
            dateLabel.setForeground(UITheme.TEXT_MUTED);
            dateLabel.setFont(new Font("Segoe UI", Font.BOLD, 10));
            dateLabel.setBorder(new EmptyBorder(5, 9, 5, 9));
            dateRow.add(dateLabel);
            p.add(dateRow);
            p.add(Box.createRigidArea(new Dimension(0, 8)));
            chatPanels.put(user, p);
        }
        return chatPanels.get(user);
    }

    private void scrollToBottom(JPanel panel) {
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = chatScroll.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    private JButton createIconButton(String iconName, String tooltip) {
        JButton btn = new JButton(UITheme.icon(iconName, 18));
        btn.setToolTipText(tooltip);
        btn.setBackground(UITheme.SURFACE_ALT);
        btn.setPreferredSize(new Dimension(40, 40));
        btn.setContentAreaFilled(true);
        btn.putClientProperty("JButton.buttonType", "roundRect");
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public boolean connect() {
        return client.connect();
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
        if (b) {
            client.start();
        }
    }

    public void handleMessage(Message msg) {
        if (msg.getSender() != null && msg.getSender().equals(client.getUsername()) && msg.getType() != Message.MessageType.USER_LIST) {
            return;
        }

        // Process continuous media data outside the EDT to prevent UI freezing
        if (msg.getType() == Message.MessageType.AUDIO_DATA) {
            audioHandler.playAudio(msg.getFileData());
            return;
        }
        if (msg.getType() == Message.MessageType.VIDEO_DATA) {
            videoHandler.displayVideo(msg.getFileData());
            return;
        }

        SwingUtilities.invokeLater(() -> {
            switch (msg.getType()) {
                case USER_LIST:
                    updateUserList((String[]) msg.getContent());
                    break;
                case TEXT:
                    // Plain-text fallback (không mã hóa)
                    addMessageBubble(msg.getSender(), (String) msg.getContent(), false, false, null, false, msg.getSender());
                    break;
                case ENCRYPTED_TEXT:
                    // Giải mã trước khi hiển thị
                    handleIncomingEncryptedMessage(msg);
                    break;
                case KEY_RESPONSE:
                    // Server trả lời KEY_REQUEST: lưu vào cache và resolve future
                    handleKeyResponse(msg);
                    break;
                case ICON:
                    if (msg.getFileData() != null) {
                        ImageIcon receivedIcon = null;
                        try {
                            BufferedImage img = ImageIO.read(new ByteArrayInputStream(msg.getFileData()));
                            if (img != null) {
                                int maxW = 200;
                                if (img.getWidth() > maxW) {
                                    int newH = (img.getHeight() * maxW) / img.getWidth();
                                    Image scaled = img.getScaledInstance(maxW, newH, Image.SCALE_SMOOTH);
                                    receivedIcon = new ImageIcon(scaled);
                                } else {
                                    receivedIcon = new ImageIcon(img);
                                }
                            }
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                        if (receivedIcon != null) {
                            addMessageBubble(msg.getSender(), "", false, false, receivedIcon, false, msg.getSender());
                        } else {
                            addMessageBubble(msg.getSender(), "[Failed to load image]", false, false, null, false, msg.getSender());
                        }
                    } else if (msg.getContent() != null) {
                        // It's a sticker/emoji
                        addMessageBubble(msg.getSender(), (String) msg.getContent(), false, false, null, true, msg.getSender());
                    }
                    break;
                case FILE:
                    addMessageBubble(msg.getSender(), "📎 " + msg.getFileName() + " (Click to download)", false, false, null, false, msg.getSender());
                    JPanel filePanel = getChatPanelForUser(msg.getSender());
                    Component[] fileComponents = filePanel.getComponents();
                    if(fileComponents.length > 0) {
                        JPanel lastPanel = (JPanel) fileComponents[fileComponents.length - 1];
                        lastPanel.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseClicked(MouseEvent e) {
                                saveFile(msg);
                            }
                        });
                        lastPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    }
                    break;
                case STEGANOGRAPHY:
                    ImageIcon stegoIcon = null;
                    try {
                        BufferedImage img = ImageIO.read(new ByteArrayInputStream(msg.getFileData()));
                        stegoIcon = new ImageIcon(img.getScaledInstance(150, 150, Image.SCALE_SMOOTH));
                    } catch (Exception ex) {}
                    addMessageBubble(msg.getSender(), "🔒 Hidden Stego Image (Click to reveal)", false, true, stegoIcon, false, msg.getSender());
                    
                    JPanel stegoPanel = getChatPanelForUser(msg.getSender());
                    Component[] components = stegoPanel.getComponents();
                    if(components.length > 0) {
                        JPanel lastPanel = (JPanel) components[components.length - 1];
                        lastPanel.addMouseListener(new MouseAdapter() {
                            @Override
                            public void mouseClicked(MouseEvent e) {
                                handleIncomingStego(msg);
                            }
                        });
                        lastPanel.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    }
                    break;
                case VOICE_CALL_REQ:
                    handleVoiceCallReq(msg);
                    break;
                case VOICE_CALL_RES:
                    if ("ACCEPT".equals(msg.getContent())) {
                        audioHandler.startCall(msg.getSender());
                        addMessageBubble("System", "📞 Voice call connected with " + msg.getSender(), false, false, null, false, msg.getSender());
                    } else {
                        addMessageBubble("System", msg.getSender() + " rejected your voice call.", false, false, null, false, msg.getSender());
                    }
                    break;
                case VOICE_CALL_END:
                    audioHandler.stopCall();
                    addMessageBubble("System", "📞 Voice call ended by " + msg.getSender(), false, false, null, false, msg.getSender());
                    break;
                case VIDEO_CALL_REQ:
                    handleVideoCallReq(msg);
                    break;
                case VIDEO_CALL_RES:
                    if ("ACCEPT".equals(msg.getContent())) {
                        videoHandler.startCall(msg.getSender());
                        addMessageBubble("System", "📹 Video call connected with " + msg.getSender(), false, false, null, false, msg.getSender());
                    } else {
                        addMessageBubble("System", msg.getSender() + " rejected your video call.", false, false, null, false, msg.getSender());
                    }
                    break;
                case VIDEO_CALL_END:
                    videoHandler.stopCall();
                    addMessageBubble("System", "📹 Video call ended by " + msg.getSender(), false, false, null, false, msg.getSender());
                    break;
            }
        });
    }

    private void updateUserList(String[] users) {
        String prevSelected = selectedUser;
        onlineUsers.clear();
        for (String u : users) {
            if (!u.equals(client.getUsername())) {
                onlineUsers.add(u);
            }
        }
        filterUserList(txtSearch == null ? "" : txtSearch.getText());
        if (onlineCountLabel != null) {
            onlineCountLabel.setText(onlineUsers.size() + " online");
        }
        if (prevSelected != null && listModel.contains(prevSelected)) {
            userList.setSelectedValue(prevSelected, true);
        }
    }

    private void filterUserList(String query) {
        if (listModel == null) return;
        String normalized = query == null ? "" : query.trim().toLowerCase();
        listModel.clear();
        for (String user : onlineUsers) {
            if (normalized.isEmpty() || user.toLowerCase().contains(normalized)) {
                listModel.addElement(user);
            }
        }
    }

    private void addMessageBubble(String sender, String text, boolean isMe, boolean isStego, ImageIcon icon, boolean isSticker, String conversationUser) {
        if (conversationUser == null) return;

        boolean isSystem = "System".equals(sender);
        JPanel targetPanel = getChatPanelForUser(conversationUser);
        int alignment = isSystem ? FlowLayout.CENTER : (isMe ? FlowLayout.RIGHT : FlowLayout.LEFT);
        JPanel wrapPanel = new JPanel(new FlowLayout(alignment, 9, 4));
        wrapPanel.setOpaque(false);

        JPanel bubble = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (!isSticker) {
                    g2.setColor(getBackground());
                    int arc = isSystem ? 18 : 24;
                    g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setOpaque(false);
        bubble.setBorder(isSystem
            ? new EmptyBorder(6, 12, 6, 12)
            : new EmptyBorder(10, 14, 8, 14));

        if (!isSticker) {
            if (isSystem) {
                bubble.setBackground(new Color(231, 235, 244));
            } else if (isMe) {
                bubble.setBackground(colorMe);
            } else {
                bubble.setBackground(isStego ? new Color(234, 241, 255) : colorOther);
            }
        }

        if (!isMe && !isSystem) {
            JLabel lblName = new JLabel(sender);
            lblName.setFont(new Font("Segoe UI", Font.BOLD, 12));
            lblName.setForeground(UITheme.PRIMARY_DARK);
            bubble.add(lblName);
            bubble.add(Box.createRigidArea(new Dimension(0, 3)));
        }

        if (icon != null) {
            JLabel imgLabel = new JLabel(icon);
            bubble.add(imgLabel);
        }

        if (text != null && !text.isEmpty()) {
            JTextArea textArea = new JTextArea(text);
            textArea.setEditable(false);
            textArea.setFocusable(false);
            textArea.setOpaque(false);
            textArea.setLineWrap(true);
            textArea.setWrapStyleWord(true);

            if (isSticker) {
                textArea.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 48));
                textArea.setSize(new Dimension(80, Short.MAX_VALUE));
                textArea.setPreferredSize(new Dimension(80, textArea.getPreferredSize().height));
            } else if (isSystem) {
                textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                int textWidth = Math.min(Math.max(100, text.length() * 6 + 12), 360);
                textArea.setSize(new Dimension(textWidth, Short.MAX_VALUE));
                textArea.setPreferredSize(new Dimension(textWidth, textArea.getPreferredSize().height));
            } else {
                textArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 14));
                int textWidth = Math.min(Math.max(58, text.length() * 8 + 12), 390);
                textArea.setSize(new Dimension(textWidth, Short.MAX_VALUE));
                textArea.setPreferredSize(new Dimension(textWidth, textArea.getPreferredSize().height));
            }

            if (isSticker) {
                textArea.setForeground(Color.BLACK); // Emoji colors are rendered by OS usually
            } else if (isSystem) {
                textArea.setForeground(UITheme.TEXT_MUTED);
            } else if (isMe) {
                textArea.setForeground(Color.WHITE);
            } else {
                textArea.setForeground(UITheme.TEXT);
            }
            bubble.add(textArea);
        }

        if (!isSystem && !isSticker) {
            bubble.add(Box.createRigidArea(new Dimension(0, 4)));
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            JLabel meta = new JLabel((isMe ? "Sent  " : "") + time);
            meta.setFont(new Font("Segoe UI", Font.PLAIN, 9));
            meta.setForeground(isMe ? new Color(255, 255, 255, 190) : UITheme.TEXT_MUTED);
            meta.setAlignmentX(Component.RIGHT_ALIGNMENT);
            bubble.add(meta);
        }

        if (!isMe && !isSystem) {
            wrapPanel.add(UITheme.avatar(sender, 32));
        }
        wrapPanel.add(bubble);
        targetPanel.add(wrapPanel);
        targetPanel.revalidate();
        targetPanel.repaint();

        scrollToBottom(targetPanel);
    }

    private void sendText() {
        if (selectedUser == null) {
            JOptionPane.showMessageDialog(this, "Select a user to chat.");
            return;
        }
        String text = txtInput.getText().trim();
        if (text.isEmpty()) return;

        txtInput.setText("");
        // Hiển thị ngay ở UI (optimistic update)
        addMessageBubble("Me", text, true, false, null, false, selectedUser);

        // Mã hóa và gửi trong background để không block UI
        String targetUser = selectedUser;
        SwingWorker<Void, Void> cryptoWorker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                buildAndSendEncryptedMsg(targetUser, text);
                return null;
            }
            @Override
            protected void done() {
                try { get(); }
                catch (Exception e) {
                    SwingUtilities.invokeLater(() ->
                        showError("Gửi tin nhắn thất bại: " + e.getMessage()));
                }
            }
        };
        cryptoWorker.execute();
    }

    /**
     * Xây dựng và gửi tin nhắn đã mã hóa (Hybrid Encryption).
     * Không cần truy cập DB — public key được lấy từ server qua socket.
     */
    private void buildAndSendEncryptedMsg(String targetUsername, String plainText) throws Exception {
        // Lấy public key của receiver qua socket (có cache)
        PublicKey receiverPublicKey = getPublicKeyFor(targetUsername);
        PublicKey senderPublicKey   = currentUserPublicKey;

        if (receiverPublicKey == null)
            throw new RuntimeException("Không lấy được public key của " + targetUsername);
        if (senderPublicKey == null)
            throw new RuntimeException("Public key của bạn chưa được khởi tạo");

        // Generate AES key và IV mới cho mỗi message
        SecretKey aesKey = AESUtil.generateAESKey();
        byte[]    iv     = AESUtil.generateIV();

        // Mã hóa nội dung
        String encryptedContent           = AESUtil.encrypt(plainText, aesKey, iv);
        String ivB64                      = AESUtil.toBase64(iv);
        String encryptedAesKeyForReceiver = RSAUtil.encryptAESKey(aesKey, receiverPublicKey);
        String encryptedAesKeyForSender   = RSAUtil.encryptAESKey(aesKey, senderPublicKey);

        // Đóng gói Message (không có receiverId vì client không biết — server sẽ lookup)
        Message msg = new Message(Message.MessageType.ENCRYPTED_TEXT, client.getUsername(), null);
        msg.setReceiver(targetUsername);
        msg.setSenderId(currentUserId);
        msg.setReceiverId(-1); // server sẽ resolve từ username
        msg.setEncryptedContent(encryptedContent);
        msg.setIv(ivB64);
        msg.setEncryptedAesKeyForReceiver(encryptedAesKeyForReceiver);
        msg.setEncryptedAesKeyForSender(encryptedAesKeyForSender);

        client.sendMessage(msg);
    }

    /**
     * Lấy public key của một user qua socket KEY_REQUEST.
     * Có cache: nếu đã biết thì trả ngay, không cần request lại.
     * Block tối đa 5 giây chờ response.
     */
    private PublicKey getPublicKeyFor(String username) throws Exception {
        // Kiểm tra cache trước
        if (publicKeyCache.containsKey(username)) return publicKeyCache.get(username);

        // Gửi KEY_REQUEST lên server
        CompletableFuture<PublicKey> future = new CompletableFuture<>();
        synchronized (keyFutures) {
            keyFutures.put(username, future);
        }
        Message req = new Message(Message.MessageType.KEY_REQUEST, client.getUsername(), username);
        req.setReceiver(username); // server dùng nội bộ
        client.sendMessage(req);

        // Chờ tối đa 5 giây
        PublicKey key = future.get(5, java.util.concurrent.TimeUnit.SECONDS);
        publicKeyCache.put(username, key);
        return key;
    }

    /**
     * Xử lý KEY_RESPONSE từ server: decode public key và resolve future.
     */
    private void handleKeyResponse(Message msg) {
        String username    = msg.getSender(); // server gửi sender = username của key
        String publicKeyB64 = (String) msg.getContent();
        try {
            PublicKey publicKey = RSAUtil.publicKeyFromBase64(publicKeyB64);
            publicKeyCache.put(username, publicKey);
            synchronized (keyFutures) {
                CompletableFuture<PublicKey> future = keyFutures.remove(username);
                if (future != null) future.complete(publicKey);
            }
        } catch (Exception e) {
            System.err.println("[ClientUI] Failed to parse KEY_RESPONSE for " + username + ": " + e.getMessage());
        }
    }

    /**
     * Giải mã tin nhắn ENCRYPTED_TEXT nhận được từ socket.
     * Dùng private key trong memory — không cần truy cập DB.
     */
    private void handleIncomingEncryptedMessage(Message msg) {
        try {
            if (currentUserPrivateKey == null)
                throw new IllegalStateException("Private key chưa được khởi tạo");

            // Là receiver hay sender? Chọn đúng encrypted AES key
            boolean isReceiver = !msg.getSender().equals(client.getUsername());
            String encryptedAesKey = isReceiver
                ? msg.getEncryptedAesKeyForReceiver()
                : msg.getEncryptedAesKeyForSender();

            // Giải mã AES key bằng private key của current user
            SecretKey aesKey = RSAUtil.decryptAESKey(encryptedAesKey, currentUserPrivateKey);

            // Giải mã nội dung
            byte[] iv = AESUtil.fromBase64(msg.getIv());
            String plainText = AESUtil.decrypt(msg.getEncryptedContent(), aesKey, iv);

            addMessageBubble(msg.getSender(), plainText, false, false, null, false, msg.getSender());
        } catch (Exception e) {
            System.err.println("[ClientUI] Cannot decrypt from " + msg.getSender() + ": " + e.getMessage());
            addMessageBubble(msg.getSender(), "[Tin nhắn mã hóa — không giải mã được]",
                false, false, null, false, msg.getSender());
        }
    }

    // New: Show Icon Picker
    private void showIconPicker(Component parent) {
        JPopupMenu popup = new JPopupMenu();
        popup.setLayout(new GridLayout(2, 3, 5, 5));
        
        String[] emojis = {"👍", "❤️", "😂", "😮", "😢", "😡"};

        for (String emoji : emojis) {
            JButton btn = new JButton(emoji);
            btn.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 28));
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
            btn.addActionListener(e -> {
                popup.setVisible(false);
                sendEmojiIcon(emoji);
            });
            popup.add(btn);
        }
        popup.show(parent, 0, parent.getHeight());
    }

    private void sendEmojiIcon(String emoji) {
        if (selectedUser == null) return;
        Message msg = new Message(Message.MessageType.ICON, client.getUsername(), emoji);
        msg.setReceiver(selectedUser);
        client.sendMessage(msg);
        
        addMessageBubble("Me", emoji, true, false, null, true, selectedUser);
    }

    private void sendImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Images", "jpg", "png", "gif"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            if (selectedUser == null) {
                JOptionPane.showMessageDialog(this, "Select a user first.");
                return;
            }
            File file = chooser.getSelectedFile();
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                Message msg = new Message(Message.MessageType.ICON, client.getUsername(), null); // Using ICON type for Image
                msg.setFileData(data); 
                msg.setReceiver(selectedUser);
                client.sendMessage(msg);
                
                BufferedImage img = ImageIO.read(file);
                ImageIcon displayIcon = null;
                int maxW = 200;
                if (img.getWidth() > maxW) {
                    int newH = (img.getHeight() * maxW) / img.getWidth();
                    displayIcon = new ImageIcon(img.getScaledInstance(maxW, newH, Image.SCALE_SMOOTH));
                } else {
                    displayIcon = new ImageIcon(img);
                }
                
                addMessageBubble("Me", "", true, false, displayIcon, false, selectedUser);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void sendFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            if (selectedUser == null) {
                JOptionPane.showMessageDialog(this, "Select a user first.");
                return;
            }
            File file = chooser.getSelectedFile();
            try {
                byte[] data = Files.readAllBytes(file.toPath());
                Message msg = new Message(Message.MessageType.FILE, client.getUsername(), null);
                msg.setFileName(file.getName());
                msg.setFileData(data);
                msg.setReceiver(selectedUser);
                client.sendMessage(msg);
                
                addMessageBubble("Me", "📎 " + file.getName(), true, false, null, false, selectedUser);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void saveFile(Message msg) {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new File(msg.getFileName()));
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                Files.write(chooser.getSelectedFile().toPath(), msg.getFileData());
                JOptionPane.showMessageDialog(this, "File saved successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(this, "Error saving file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void sendSteganography() {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "bmp"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            if (selectedUser == null) {
                JOptionPane.showMessageDialog(this, "Select a user first.");
                return;
            }
            File file = chooser.getSelectedFile();
            String text = JOptionPane.showInputDialog(this, "Enter text to hide:");
            String pwd = JOptionPane.showInputDialog(this, "Enter password:");
            
            if (text != null && pwd != null && !text.isEmpty() && !pwd.isEmpty()) {
                try {
                    byte[] original = Files.readAllBytes(file.toPath());
                    byte[] encoded = SteganoUtils.encodeTextToImage(original, text, pwd);
                    
                    Message msg = new Message(Message.MessageType.STEGANOGRAPHY, client.getUsername(), null);
                    msg.setFileName(file.getName() + "_stego.png");
                    msg.setFileData(encoded);
                    msg.setReceiver(selectedUser);
                    client.sendMessage(msg);
                    
                    ImageIcon stegoIcon = new ImageIcon(ImageIO.read(file).getScaledInstance(150, 150, Image.SCALE_SMOOTH));
                    addMessageBubble("Me", "🔒 Hidden Stego Image", true, true, stegoIcon, false, selectedUser);
                } catch (Exception e) {
                    JOptionPane.showMessageDialog(this, "Error hiding message: " + e.getMessage());
                }
            }
        }
    }

    private void handleIncomingStego(Message msg) {
        String pwd = JOptionPane.showInputDialog(this, "Enter password to decrypt:");
        if (pwd != null && !pwd.isEmpty()) {
            try {
                String decoded = SteganoUtils.decodeTextFromImage(msg.getFileData(), pwd);
                if (decoded != null) {
                    JOptionPane.showMessageDialog(this, "Hidden Message: " + decoded, "Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(this, "Invalid password or no hidden text found.", "Failed", JOptionPane.ERROR_MESSAGE);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void startVoiceCall() {
        if (selectedUser == null) {
            JOptionPane.showMessageDialog(this, "Select a specific user for Voice Call.");
            return;
        }
        Message msg = new Message(Message.MessageType.VOICE_CALL_REQ, client.getUsername(), null);
        msg.setReceiver(selectedUser);
        client.sendMessage(msg);
        addMessageBubble("System", "📞 Calling " + selectedUser + "...", true, false, null, false, selectedUser);
    }

    private void handleVoiceCallReq(Message msg) {
        int res = JOptionPane.showConfirmDialog(this, msg.getSender() + " is voice calling you. Accept?", "Incoming Call", JOptionPane.YES_NO_OPTION);
        Message resMsg = new Message(Message.MessageType.VOICE_CALL_RES, client.getUsername(), res == JOptionPane.YES_OPTION ? "ACCEPT" : "REJECT");
        resMsg.setReceiver(msg.getSender());
        client.sendMessage(resMsg);
        
        if (res == JOptionPane.YES_OPTION) {
            audioHandler.startCall(msg.getSender());
            addMessageBubble("System", "📞 Voice call connected with " + msg.getSender(), false, false, null, false, msg.getSender());
        }
    }

    private void startVideoCall() {
        if (selectedUser == null) {
            JOptionPane.showMessageDialog(this, "Select a specific user for Video Call.");
            return;
        }
        Message msg = new Message(Message.MessageType.VIDEO_CALL_REQ, client.getUsername(), null);
        msg.setReceiver(selectedUser);
        client.sendMessage(msg);
        addMessageBubble("System", "📹 Calling " + selectedUser + "...", true, false, null, false, selectedUser);
    }

    private void handleVideoCallReq(Message msg) {
        int res = JOptionPane.showConfirmDialog(this, msg.getSender() + " is video calling you. Accept?", "Incoming Call", JOptionPane.YES_NO_OPTION);
        Message resMsg = new Message(Message.MessageType.VIDEO_CALL_RES, client.getUsername(), res == JOptionPane.YES_OPTION ? "ACCEPT" : "REJECT");
        resMsg.setReceiver(msg.getSender());
        client.sendMessage(resMsg);
        
        if (res == JOptionPane.YES_OPTION) {
            videoHandler.startCall(msg.getSender());
            addMessageBubble("System", "📹 Video call connected with " + msg.getSender(), false, false, null, false, msg.getSender());
        }
    }

    private void speechToText() {
        if (selectedUser == null) {
            JOptionPane.showMessageDialog(this, "Select a user first.");
            return;
        }
        File modelDir = new File("model");
        if (!modelDir.exists() || !modelDir.isDirectory()) {
            JOptionPane.showMessageDialog(this, "To use Speech to Text, please ensure Vosk model is in 'model' directory.\nPlease download vosk-model-small-en-us-0.15 and extract to 'model' folder.", "Missing Model", JOptionPane.WARNING_MESSAGE);
        }
        SttHandler stt = new SttHandler(this, client, selectedUser);
        stt.startRecording();
    }

    public void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    class UserListCellRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            UITheme.RoundedPanel panel = new UITheme.RoundedPanel(18);
            panel.setLayout(new BorderLayout(11, 0));
            panel.setBorder(new EmptyBorder(8, 10, 8, 10));

            String name = (String) value;
            JLabel lblAvatar = UITheme.avatar(name, 42);
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JLabel lblName = new JLabel(name);
            lblName.setFont(new Font("Segoe UI", Font.BOLD, 14));
            lblName.setForeground(UITheme.TEXT);
            JLabel lblState = new JLabel("●  Available to chat");
            lblState.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            lblState.setForeground(UITheme.SUCCESS);
            text.add(lblName);
            text.add(Box.createRigidArea(new Dimension(0, 2)));
            text.add(lblState);

            panel.add(lblAvatar, BorderLayout.WEST);
            panel.add(text, BorderLayout.CENTER);

            if (isSelected) {
                panel.setBackground(new Color(235, 238, 255));
                lblAvatar.setBackground(colorMe);
            } else {
                panel.setBackground(UITheme.SURFACE);
            }

            return panel;
        }
    }
}
