package com.chat.client;

import com.chat.common.Message;
import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;

public class AudioHandler {
    private ChatClient client;
    private String targetUser;
    private boolean isRecording = false;
    private TargetDataLine microphone;
    private SourceDataLine speakers;
    
    private JFrame audioFrame;
    private JLabel lblStatus;
    
    private Timer callTimer;
    private int callDuration = 0;
    private JLabel lblTimer;

    public AudioHandler(ChatClient client) {
        this.client = client;
    }

    public void startCall(String targetUser) {
        this.targetUser = targetUser;
        isRecording = true;
        
        SwingUtilities.invokeLater(this::createCallWindow);
        
        try {
            AudioFormat format = getAudioFormat();
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphone.open(format);
            microphone.start();

            Thread recordThread = new Thread(() -> {
                byte[] buffer = new byte[1024];
                while (isRecording) {
                    int bytesRead = microphone.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        byte[] data = new byte[bytesRead];
                        System.arraycopy(buffer, 0, data, 0, bytesRead);
                        Message msg = new Message(Message.MessageType.AUDIO_DATA, client.getUsername(), null);
                        msg.setReceiver(this.targetUser);
                        msg.setFileData(data);
                        client.sendMessage(msg);
                    }
                }
                microphone.close();
            });
            recordThread.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
        }
    }

    private void createCallWindow() {
        if (audioFrame != null) return;
        audioFrame = new JFrame("Messenger Voice Call");
        audioFrame.setSize(400, 500);
        audioFrame.setLocationRelativeTo(null);
        audioFrame.getContentPane().setBackground(new Color(36, 37, 38)); // Dark Theme
        audioFrame.setLayout(new BorderLayout());

        // Header
        JPanel header = new JPanel(new GridLayout(2, 1));
        header.setBackground(new Color(36, 37, 38));
        header.setBorder(BorderFactory.createEmptyBorder(20, 0, 10, 0));
        
        lblStatus = new JLabel("Calling " + targetUser + "...", SwingConstants.CENTER);
        lblStatus.setFont(new Font("Segoe UI", Font.BOLD, 24));
        lblStatus.setForeground(Color.WHITE);
        header.add(lblStatus);
        
        lblTimer = new JLabel("00:00", SwingConstants.CENTER);
        lblTimer.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        lblTimer.setForeground(new Color(150, 150, 150));
        header.add(lblTimer);
        
        audioFrame.add(header, BorderLayout.NORTH);

        // Center (Avatar)
        JPanel centerPanel = new JPanel(new GridBagLayout());
        centerPanel.setBackground(new Color(36, 37, 38));
        
        JLabel lblAvatar = new JLabel(targetUser.substring(0, 1).toUpperCase());
        lblAvatar.setFont(new Font("Segoe UI", Font.BOLD, 60));
        lblAvatar.setForeground(Color.WHITE);
        lblAvatar.setHorizontalAlignment(SwingConstants.CENTER);
        lblAvatar.setOpaque(true);
        lblAvatar.setBackground(new Color(0, 132, 255));
        lblAvatar.setPreferredSize(new Dimension(150, 150));
        lblAvatar.setBorder(new LineBorder(new Color(0, 132, 255), 2, true)); // Pseudo-round look
        
        centerPanel.add(lblAvatar);
        audioFrame.add(centerPanel, BorderLayout.CENTER);

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
            Message msg = new Message(Message.MessageType.VOICE_CALL_END, client.getUsername(), null);
            msg.setReceiver(targetUser);
            client.sendMessage(msg);
            stopCall();
        });
        controls.add(btnEnd);
        
        audioFrame.add(controls, BorderLayout.SOUTH);

        audioFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                Message msg = new Message(Message.MessageType.VOICE_CALL_END, client.getUsername(), null);
                msg.setReceiver(targetUser);
                client.sendMessage(msg);
                stopCall();
            }
        });

        audioFrame.setVisible(true);

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
        if (speakers != null) {
            speakers.close();
        }
        if (callTimer != null) {
            callTimer.stop();
        }
        SwingUtilities.invokeLater(() -> {
            if (audioFrame != null) {
                audioFrame.dispose();
                audioFrame = null;
            }
        });
    }

    public void playAudio(byte[] audioData) {
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
                    lblStatus.setText(targetUser); // Remove "Calling..."
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private AudioFormat getAudioFormat() {
        float sampleRate = 8000.0f;
        int sampleSizeInBits = 16;
        int channels = 1;
        boolean signed = true;
        boolean bigEndian = true;
        return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
    }
}
