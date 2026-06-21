package com.chat.client;

import com.chat.common.Message;
import com.chat.common.UITheme;
import com.github.sarxos.webcam.Webcam;

import javax.imageio.ImageIO;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class VideoHandler {
    private static final Color STAGE_BG = new Color(12, 17, 30);
    private static final Color CONTROL_BG = new Color(255, 255, 255, 42);
    private static final Color CONTROL_HOVER = new Color(255, 255, 255, 68);

    private final ChatClient client;
    private final AudioHandler audioHandler;
    private String targetUser;
    private volatile boolean isRecording;
    private volatile boolean cameraMuted;
    private volatile boolean microphoneMuted;
    private volatile boolean speakerMuted;
    private Webcam webcam;
    private TargetDataLine microphone;

    private JFrame videoFrame;
    private JLabel videoLabel;
    private JLabel localPreviewLabel;
    private JLabel lblStatus;
    private JLabel lblTimer;
    private JLabel lblMicAction;
    private JLabel lblCameraAction;
    private JLabel lblSpeakerAction;
    private UITheme.CircleButton btnMic;
    private UITheme.CircleButton btnCamera;
    private UITheme.CircleButton btnSpeaker;
    private Timer callTimer;
    private int callDuration;

    public VideoHandler(ChatClient client, AudioHandler audioHandler) {
        this.client = client;
        this.audioHandler = audioHandler;
    }

    public void startCall(String targetUser) {
        this.targetUser = targetUser;
        isRecording = true;
        cameraMuted = false;
        microphoneMuted = false;
        speakerMuted = false;
        audioHandler.setSpeakerMuted(false);
        SwingUtilities.invokeLater(this::createCallWindow);
        startVideoCapture();
        startAudioCapture();
    }

    private void startVideoCapture() {
        Thread recordThread = new Thread(() -> {
            try {
                webcam = Webcam.getDefault();
                if (webcam == null) {
                    showCallError("No webcam detected on this device.");
                    return;
                }
                webcam.open();
                while (isRecording && webcam.isOpen()) {
                    if (cameraMuted) {
                        Thread.sleep(100);
                        continue;
                    }
                    BufferedImage image = webcam.getImage();
                    if (image != null) {
                        updateLocalPreview(image);
                        BufferedImage scaledImage = scaleFrame(image, 480, 360);
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(scaledImage, "jpg", baos);
                        Message msg = new Message(Message.MessageType.VIDEO_DATA, client.getUsername(), null);
                        msg.setReceiver(this.targetUser);
                        msg.setFileData(baos.toByteArray());
                        client.sendMessage(msg);
                    }
                    Thread.sleep(100);
                }
            } catch (Exception e) {
                if (isRecording) showCallError("Camera error: " + e.getMessage());
            } finally {
                if (webcam != null && webcam.isOpen()) webcam.close();
            }
        }, "chatly-video-capture");
        recordThread.setDaemon(true);
        recordThread.start();
    }

    private void startAudioCapture() {
        Thread audioThread = new Thread(() -> {
            try {
                AudioFormat format = getAudioFormat();
                microphone = (TargetDataLine) AudioSystem.getLine(new DataLine.Info(TargetDataLine.class, format));
                microphone.open(format);
                microphone.start();
                byte[] buffer = new byte[1024];
                while (isRecording && microphone.isOpen()) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0 && !microphoneMuted) {
                        byte[] data = new byte[bytesRead];
                        System.arraycopy(buffer, 0, data, 0, bytesRead);
                        Message msg = new Message(Message.MessageType.AUDIO_DATA, client.getUsername(), null);
                        msg.setReceiver(targetUser);
                        msg.setFileData(data);
                        client.sendMessage(msg);
                    }
                }
            } catch (LineUnavailableException e) {
                SwingUtilities.invokeLater(() -> {
                    if (lblStatus != null) lblStatus.setText("Camera connected • Microphone unavailable");
                });
            } finally {
                if (microphone != null && microphone.isOpen()) microphone.close();
            }
        }, "chatly-video-audio");
        audioThread.setDaemon(true);
        audioThread.start();
    }

    private void createCallWindow() {
        if (videoFrame != null) return;
        videoFrame = new JFrame("Video call • " + targetUser);
        videoFrame.setSize(1040, 720);
        videoFrame.setMinimumSize(new Dimension(820, 600));
        videoFrame.setLocationRelativeTo(null);
        videoFrame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(STAGE_BG);
        root.setBorder(new EmptyBorder(18, 20, 20, 20));

        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setOpaque(false);
        JLabel avatar = UITheme.avatar(targetUser, 44);
        header.add(avatar, BorderLayout.WEST);
        JPanel identity = new JPanel();
        identity.setOpaque(false);
        identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
        JLabel lblName = new JLabel(targetUser);
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 18));
        lblName.setForeground(Color.WHITE);
        lblStatus = new JLabel("Connecting video…");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        lblStatus.setForeground(new Color(170, 181, 204));
        identity.add(lblName);
        identity.add(Box.createRigidArea(new Dimension(0, 2)));
        identity.add(lblStatus);
        header.add(identity, BorderLayout.CENTER);
        lblTimer = new JLabel("00:00");
        lblTimer.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTimer.setForeground(Color.WHITE);
        lblTimer.setBorder(new EmptyBorder(8, 14, 8, 14));
        header.add(lblTimer, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        JLayeredPane canvas = new JLayeredPane() {
            @Override public void doLayout() {
                videoLabel.setBounds(0, 0, getWidth(), getHeight());
                int previewW = Math.min(210, Math.max(150, getWidth() / 5));
                int previewH = previewW * 3 / 4;
                localPreviewLabel.setBounds(getWidth() - previewW - 18, getHeight() - previewH - 18,
                    previewW, previewH);
            }
        };
        canvas.setOpaque(true);
        canvas.setBackground(new Color(18, 25, 43));
        canvas.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 22), 1, true));

        videoLabel = new JLabel("<html><div style='text-align:center'>Waiting for video<br><small>Connecting securely…</small></div></html>", SwingConstants.CENTER);
        videoLabel.setOpaque(true);
        videoLabel.setBackground(new Color(18, 25, 43));
        videoLabel.setForeground(new Color(190, 200, 220));
        videoLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        canvas.add(videoLabel, JLayeredPane.DEFAULT_LAYER);

        localPreviewLabel = new JLabel("Starting camera…", SwingConstants.CENTER);
        localPreviewLabel.setOpaque(true);
        localPreviewLabel.setBackground(new Color(32, 41, 62));
        localPreviewLabel.setForeground(new Color(180, 190, 210));
        localPreviewLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        localPreviewLabel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(255, 255, 255, 70), 1, true),
            new EmptyBorder(4, 4, 4, 4)));
        canvas.add(localPreviewLabel, JLayeredPane.PALETTE_LAYER);
        root.add(canvas, BorderLayout.CENTER);

        UITheme.RoundedPanel dock = new UITheme.RoundedPanel(32);
        dock.setBackground(new Color(255, 255, 255, 24));
        dock.setLayout(new FlowLayout(FlowLayout.CENTER, 24, 14));

        btnMic = callButton("call-mic");
        lblMicAction = controlLabel("Mute");
        btnMic.addActionListener(e -> toggleMicrophone());
        dock.add(controlGroup(btnMic, lblMicAction));

        btnCamera = callButton("call-video");
        lblCameraAction = controlLabel("Camera");
        btnCamera.addActionListener(e -> toggleCamera());
        dock.add(controlGroup(btnCamera, lblCameraAction));

        btnSpeaker = callButton("call-volume");
        lblSpeakerAction = controlLabel("Speaker");
        btnSpeaker.addActionListener(e -> toggleSpeaker());
        dock.add(controlGroup(btnSpeaker, lblSpeakerAction));

        UITheme.CircleButton btnEnd = new UITheme.CircleButton(
            UITheme.icon("call-end", 25), UITheme.DANGER, 58);
        btnEnd.setButtonColors(UITheme.DANGER, new Color(255, 96, 96));
        btnEnd.addActionListener(e -> endCall());
        dock.add(controlGroup(btnEnd, controlLabel("End")));
        root.add(dock, BorderLayout.SOUTH);

        videoFrame.setContentPane(root);
        videoFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { endCall(); }
        });
        videoFrame.setVisible(true);
        startTimer();
    }

    private UITheme.CircleButton callButton(String icon) {
        UITheme.CircleButton button = new UITheme.CircleButton(UITheme.icon(icon, 23), CONTROL_BG, 58);
        button.setButtonColors(CONTROL_BG, CONTROL_HOVER);
        return button;
    }

    private JPanel controlGroup(JButton button, JLabel label) {
        JPanel group = new JPanel();
        group.setOpaque(false);
        group.setLayout(new BoxLayout(group, BoxLayout.Y_AXIS));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        group.add(button);
        group.add(Box.createRigidArea(new Dimension(0, 6)));
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

    private void toggleCamera() {
        cameraMuted = !cameraMuted;
        btnCamera.setIcon(UITheme.icon(cameraMuted ? "call-video-off" : "call-video", 23));
        btnCamera.setButtonColors(cameraMuted ? UITheme.DANGER : CONTROL_BG,
            cameraMuted ? new Color(255, 96, 96) : CONTROL_HOVER);
        lblCameraAction.setText(cameraMuted ? "Start video" : "Camera");
        if (cameraMuted && localPreviewLabel != null) {
            localPreviewLabel.setIcon(null);
            localPreviewLabel.setText("Camera off");
        }
    }

    private void toggleSpeaker() {
        speakerMuted = !speakerMuted;
        audioHandler.setSpeakerMuted(speakerMuted);
        btnSpeaker.setIcon(UITheme.icon(speakerMuted ? "call-volume-off" : "call-volume", 23));
        btnSpeaker.setButtonColors(speakerMuted ? UITheme.DANGER : CONTROL_BG,
            speakerMuted ? new Color(255, 96, 96) : CONTROL_HOVER);
        lblSpeakerAction.setText(speakerMuted ? "Unmute" : "Speaker");
    }

    private void endCall() {
        Message msg = new Message(Message.MessageType.VIDEO_CALL_END, client.getUsername(), null);
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
        if (webcam != null && webcam.isOpen()) webcam.close();
        if (callTimer != null) callTimer.stop();
        audioHandler.stopCall();
        audioHandler.setSpeakerMuted(false);
        SwingUtilities.invokeLater(() -> {
            if (videoFrame != null) {
                videoFrame.dispose();
                videoFrame = null;
            }
        });
    }

    public void displayVideo(byte[] videoData) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(videoData));
            if (image == null) return;
            SwingUtilities.invokeLater(() -> {
                if (videoLabel == null) return;
                int maxW = Math.max(640, videoLabel.getWidth() - 20);
                int maxH = Math.max(420, videoLabel.getHeight() - 20);
                videoLabel.setIcon(new ImageIcon(scaleToFit(image, maxW, maxH)));
                videoLabel.setText("");
                if (lblStatus != null) lblStatus.setText("Connected • Encrypted");
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void updateLocalPreview(BufferedImage image) {
        SwingUtilities.invokeLater(() -> {
            if (localPreviewLabel == null || cameraMuted) return;
            int w = Math.max(160, localPreviewLabel.getWidth() - 8);
            int h = Math.max(120, localPreviewLabel.getHeight() - 8);
            localPreviewLabel.setIcon(new ImageIcon(scaleToFit(image, w, h)));
            localPreviewLabel.setText("");
        });
    }

    private BufferedImage scaleFrame(BufferedImage source, int width, int height) {
        Image scaled = source.getScaledInstance(width, height, Image.SCALE_SMOOTH);
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = result.createGraphics();
        g2.drawImage(scaled, 0, 0, null);
        g2.dispose();
        return result;
    }

    private Image scaleToFit(BufferedImage image, int maxW, int maxH) {
        double scale = Math.min((double) maxW / image.getWidth(), (double) maxH / image.getHeight());
        int width = Math.max(1, (int) (image.getWidth() * scale));
        int height = Math.max(1, (int) (image.getHeight() * scale));
        return image.getScaledInstance(width, height, Image.SCALE_SMOOTH);
    }

    private void showCallError(String message) {
        SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
            videoFrame, message, "Video call", JOptionPane.ERROR_MESSAGE));
    }

    private AudioFormat getAudioFormat() {
        return new AudioFormat(8000.0f, 16, 1, true, true);
    }
}
