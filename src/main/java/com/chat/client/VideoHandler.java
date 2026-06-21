package com.chat.client;

import com.chat.common.Message;
import com.github.sarxos.webcam.Webcam;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class VideoHandler {
    private ChatClient client;
    private String targetUser;
    private boolean isRecording = false;
    private Webcam webcam;
    
    private JFrame videoFrame;
    private JLabel videoLabel;
    
    private Timer callTimer;
    private int callDuration = 0;
    private JLabel lblTimer;

    public VideoHandler(ChatClient client) {
        this.client = client;
    }

    public void startCall(String targetUser) {
        this.targetUser = targetUser;
        isRecording = true;
        
        SwingUtilities.invokeLater(this::createCallWindow);
        
        Thread recordThread = new Thread(() -> {
            try {
                webcam = Webcam.getDefault();
                if (webcam != null) {
                    webcam.open();
                    
                    while (isRecording && webcam.isOpen()) {
                        try {
                            BufferedImage image = webcam.getImage();
                            if (image != null) {
                                // Scale down image to 320x240 for network performance
                                Image tmp = image.getScaledInstance(320, 240, Image.SCALE_SMOOTH);
                                BufferedImage scaledImage = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
                                Graphics2D g2d = scaledImage.createGraphics();
                                g2d.drawImage(tmp, 0, 0, null);
                                g2d.dispose();

                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                ImageIO.write(scaledImage, "jpg", baos);
                                byte[] data = baos.toByteArray();
                                
                                Message msg = new Message(Message.MessageType.VIDEO_DATA, client.getUsername(), null);
                                msg.setReceiver(this.targetUser);
                                msg.setFileData(data);
                                client.sendMessage(msg);
                                
                                Thread.sleep(100); // ~10 fps
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    webcam.close();
                } else {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(videoFrame, "No webcam detected on this device."));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(videoFrame, "Error opening webcam: " + ex.getMessage()));
            }
        });
        recordThread.start();
    }

    private void createCallWindow() {
        if (videoFrame != null) return;
        videoFrame = new JFrame("Messenger Video Call");
        videoFrame.setSize(800, 600);
        videoFrame.setLocationRelativeTo(null);
        videoFrame.getContentPane().setBackground(new Color(36, 37, 38)); // Messenger Dark Theme
        videoFrame.setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new GridLayout(2, 1));
        header.setBackground(new Color(36, 37, 38));
        header.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        
        JLabel lblName = new JLabel(targetUser, SwingConstants.CENTER);
        lblName.setFont(new Font("Segoe UI", Font.BOLD, 24));
        lblName.setForeground(Color.WHITE);
        header.add(lblName);
        
        lblTimer = new JLabel("00:00", SwingConstants.CENTER);
        lblTimer.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        lblTimer.setForeground(new Color(150, 150, 150));
        header.add(lblTimer);
        
        videoFrame.add(header, BorderLayout.NORTH);

        // Center
        videoLabel = new JLabel("Waiting for video feed...", SwingConstants.CENTER);
        videoLabel.setForeground(Color.GRAY);
        videoFrame.add(videoLabel, BorderLayout.CENTER);

        // Bottom
        JPanel controls = new JPanel();
        controls.setBackground(new Color(36, 37, 38));
        controls.setBorder(BorderFactory.createEmptyBorder(20, 0, 30, 0));
        
        JButton btnEnd = new JButton("End Call");
        btnEnd.setFont(new Font("Segoe UI", Font.BOLD, 16));
        btnEnd.setBackground(new Color(255, 59, 48)); // Red
        btnEnd.setForeground(Color.WHITE);
        btnEnd.setFocusPainted(false);
        btnEnd.setPreferredSize(new Dimension(150, 50));
        btnEnd.addActionListener(e -> {
            Message msg = new Message(Message.MessageType.VIDEO_CALL_END, client.getUsername(), null);
            msg.setReceiver(targetUser);
            client.sendMessage(msg);
            stopCall();
        });
        controls.add(btnEnd);
        
        videoFrame.add(controls, BorderLayout.SOUTH);

        videoFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                Message msg = new Message(Message.MessageType.VIDEO_CALL_END, client.getUsername(), null);
                msg.setReceiver(targetUser);
                client.sendMessage(msg);
                stopCall();
            }
        });

        videoFrame.setVisible(true);

        // Start Timer
        callDuration = 0;
        if (callTimer != null) callTimer.stop();
        callTimer = new Timer(1000, e -> {
            callDuration++;
            int min = callDuration / 60;
            int sec = callDuration % 60;
            lblTimer.setText(String.format("%02d:%02d", min, sec));
        });
        callTimer.start();
    }

    public void stopCall() {
        isRecording = false;
        if (callTimer != null) {
            callTimer.stop();
        }
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
            SwingUtilities.invokeLater(() -> {
                if (videoLabel != null && image != null) {
                    int maxW = 640;
                    int newH = (image.getHeight() * maxW) / image.getWidth();
                    videoLabel.setIcon(new ImageIcon(image.getScaledInstance(maxW, newH, Image.SCALE_SMOOTH)));
                    videoLabel.setText(""); // Remove waiting text
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
