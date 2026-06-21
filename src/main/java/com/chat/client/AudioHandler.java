package com.chat.client;

import com.chat.common.Message;
import com.chat.common.UITheme;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

public class AudioHandler {
    private static final Color CALL_BG = new Color(14, 20, 38);
    private static final Color CONTROL_BG = new Color(255, 255, 255, 42);
    private static final Color CONTROL_HOVER = new Color(255, 255, 255, 68);

    private final ChatClient client;
    private String targetUser;
    private volatile boolean isRecording;
    private volatile boolean microphoneMuted;
    private volatile boolean speakerMuted;
    private TargetDataLine microphone;
    private SourceDataLine speakers;

    private JFrame audioFrame;
    private JLabel lblStatus;
    private JLabel lblTimer;
    private JLabel lblMicAction;
    private JLabel lblSpeakerAction;
    private UITheme.CircleButton btnMic;
    private UITheme.CircleButton btnSpeaker;

    private Timer callTimer;
    private int callDuration;

    public AudioHandler(ChatClient client) {
        this.client = client;
    }

    public void startCall(String targetUser) {
        this.targetUser = targetUser;
        isRecording = true;
        microphoneMuted = false;
        speakerMuted = false;
        SwingUtilities.invokeLater(this::createCallWindow);

        Thread recordThread = new Thread(() -> {
            try {
                AudioFormat format = getAudioFormat();
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                microphone = (TargetDataLine) AudioSystem.getLine(info);
                microphone.open(format);
                microphone.start();

                byte[] buffer = new byte[1024];
                while (isRecording && microphone.isOpen()) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0 && !microphoneMuted) {
                        byte[] data = new byte[bytesRead];
                        System.arraycopy(buffer, 0, data, 0, bytesRead);
                        Message msg = new Message(Message.MessageType.AUDIO_DATA, client.getUsername(), null);
                        msg.setReceiver(this.targetUser);
                        msg.setFileData(data);
                        client.sendMessage(msg);
                    }
                }
            } catch (LineUnavailableException e) {
                SwingUtilities.invokeLater(() -> {
                    if (lblStatus != null) lblStatus.setText("Microphone unavailable");
                    JOptionPane.showMessageDialog(audioFrame,
                        "Cannot access the microphone: " + e.getMessage(),
                        "Microphone error", JOptionPane.ERROR_MESSAGE);
                });
            } finally {
                if (microphone != null && microphone.isOpen()) microphone.close();
            }
        }, "chatly-voice-capture");
        recordThread.setDaemon(true);
        recordThread.start();
    }

    private void createCallWindow() {
        if (audioFrame != null) return;
        audioFrame = new JFrame("Voice call • " + targetUser);
        audioFrame.setSize(470, 640);
        audioFrame.setMinimumSize(new Dimension(430, 590));
        audioFrame.setLocationRelativeTo(null);
        audioFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        UITheme.GradientPanel root = new UITheme.GradientPanel(CALL_BG, new Color(54, 42, 96));
        root.setLayout(new BorderLayout());
        root.setBorder(new EmptyBorder(28, 28, 26, 28));

        JPanel header = new JPanel();
        header.setOpaque(false);
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        JLabel callType = new JLabel("ENCRYPTED VOICE CALL");
        callType.setFont(new Font("Segoe UI", Font.BOLD, 11));
        callType.setForeground(new Color(130, 224, 190));
        callType.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel lblName = new JLabel(targetUser);
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 29));
        lblName.setForeground(Color.WHITE);
        lblName.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblStatus = new JLabel("Calling…");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        lblStatus.setForeground(new Color(193, 201, 220));
        lblStatus.setAlignmentX(Component.CENTER_ALIGNMENT);
        lblTimer = new JLabel("00:00");
        lblTimer.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTimer.setForeground(Color.WHITE);
        lblTimer.setAlignmentX(Component.CENTER_ALIGNMENT);
        header.add(callType);
        header.add(Box.createRigidArea(new Dimension(0, 11)));
        header.add(lblName);
        header.add(Box.createRigidArea(new Dimension(0, 5)));
        header.add(lblStatus);
        header.add(Box.createRigidArea(new Dimension(0, 9)));
        header.add(lblTimer);
        root.add(header, BorderLayout.NORTH);

        JPanel avatarStage = new JPanel(new GridBagLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int cx = getWidth() / 2;
                int cy = getHeight() / 2;
                g2.setColor(new Color(91, 108, 255, 24));
                g2.fillOval(cx - 112, cy - 112, 224, 224);
                g2.setColor(new Color(91, 108, 255, 40));
                g2.fillOval(cx - 92, cy - 92, 184, 184);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        avatarStage.setOpaque(false);
        JLabel avatar = UITheme.avatar(targetUser, 148);
        avatar.setBackground(new Color(91, 108, 255));
        avatarStage.add(avatar);
        root.add(avatarStage, BorderLayout.CENTER);

        UITheme.RoundedPanel dock = new UITheme.RoundedPanel(32);
        dock.setBackground(new Color(255, 255, 255, 24));
        dock.setLayout(new GridLayout(1, 3, 14, 0));
        dock.setBorder(new EmptyBorder(16, 20, 14, 20));

        btnMic = new UITheme.CircleButton(UITheme.icon("call-mic", 23), CONTROL_BG, 58);
        lblMicAction = controlLabel("Mute");
        btnMic.addActionListener(e -> toggleMicrophone());
        dock.add(controlGroup(btnMic, lblMicAction));

        btnSpeaker = new UITheme.CircleButton(UITheme.icon("call-volume", 23), CONTROL_BG, 58);
        lblSpeakerAction = controlLabel("Speaker");
        btnSpeaker.addActionListener(e -> toggleSpeaker());
        dock.add(controlGroup(btnSpeaker, lblSpeakerAction));

        UITheme.CircleButton btnEnd = new UITheme.CircleButton(
            UITheme.icon("call-end", 25), UITheme.DANGER, 58);
        btnEnd.setButtonColors(UITheme.DANGER, new Color(255, 96, 96));
        btnEnd.addActionListener(e -> endCall());
        dock.add(controlGroup(btnEnd, controlLabel("End")));
        root.add(dock, BorderLayout.SOUTH);

        audioFrame.setContentPane(root);
        audioFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { endCall(); }
        });
        audioFrame.setVisible(true);
        startTimer();
    }

    private JPanel controlGroup(JButton button, JLabel label) {
        JPanel group = new JPanel();
        group.setOpaque(false);
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        group.add(button);
        group.add(Box.createRigidArea(new Dimension(0, 7)));
        group.add(label);
        return group;
    }

    private JLabel controlLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        label.setForeground(new Color(225, 230, 242));
        return label;
    }

    private void toggleMicrophone() {
        microphoneMuted = !microphoneMuted;
        btnMic.setIcon(UITheme.icon(microphoneMuted ? "call-mic-off" : "call-mic", 23));
        btnMic.setButtonColors(microphoneMuted ? UITheme.DANGER : CONTROL_BG,
            microphoneMuted ? new Color(255, 96, 96) : CONTROL_HOVER);
        lblMicAction.setText(microphoneMuted ? "Unmute" : "Mute");
    }

    private void toggleSpeaker() {
        speakerMuted = !speakerMuted;
        btnSpeaker.setIcon(UITheme.icon(speakerMuted ? "call-volume-off" : "call-volume", 23));
        btnSpeaker.setButtonColors(speakerMuted ? UITheme.DANGER : CONTROL_BG,
            speakerMuted ? new Color(255, 96, 96) : CONTROL_HOVER);
        lblSpeakerAction.setText(speakerMuted ? "Unmute" : "Speaker");
        if (speakerMuted && speakers != null) speakers.flush();
    }

    private void endCall() {
        Message msg = new Message(Message.MessageType.VOICE_CALL_END, client.getUsername(), null);
        msg.setReceiver(targetUser);
        client.sendMessage(msg);
        stopCall();
    }

    private void startTimer() {
        callDuration = 0;
        if (callTimer != null) callTimer.stop();
        callTimer = new Timer(1000, e -> {
            callDuration++;
            lblTimer.setText(String.format("%02d:%02d", callDuration / 60, callDuration % 60));
        });
        callTimer.start();
    }

    public void stopCall() {
        isRecording = false;
        if (microphone != null && microphone.isOpen()) microphone.close();
        if (speakers != null && speakers.isOpen()) speakers.close();
        if (callTimer != null) callTimer.stop();
        SwingUtilities.invokeLater(() -> {
            if (audioFrame != null) {
                audioFrame.dispose();
                audioFrame = null;
            }
        });
    }

    public void setSpeakerMuted(boolean muted) {
        this.speakerMuted = muted;
        if (muted && speakers != null) speakers.flush();
    }

    public void playAudio(byte[] audioData) {
        if (speakerMuted) return;
        try {
            if (speakers == null || !speakers.isOpen()) {
                AudioFormat format = getAudioFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                speakers = (SourceDataLine) AudioSystem.getLine(info);
                speakers.open(format);
                speakers.start();
            }
            speakers.write(audioData, 0, audioData.length);
            SwingUtilities.invokeLater(() -> {
                if (lblStatus != null && lblStatus.getText().startsWith("Calling")) {
                    lblStatus.setText("Connected • Encrypted");
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private AudioFormat getAudioFormat() {
        return new AudioFormat(8000.0f, 16, 1, true, true);
    }
}
