package com.chat.client;

import com.chat.common.Message;
import org.vosk.Model;
import org.vosk.Recognizer;

import javax.sound.sampled.*;
import javax.swing.*;
import java.io.File;

public class SttHandler {
    private ClientUI ui;
    private ChatClient client;
    private String selectedUser;

    public SttHandler(ClientUI ui, ChatClient client, String selectedUser) {
        this.ui = ui;
        this.client = client;
        this.selectedUser = selectedUser;
    }

    public void startRecording() {
        File modelDir = new File("model");
        if (!modelDir.exists() || !modelDir.isDirectory()) {
            JOptionPane.showMessageDialog(ui, "Vosk model not found. Please download a model from https://alphacephei.com/vosk/models\n" +
                    "(e.g., vosk-model-small-en-us-0.15) and extract it to a folder named 'model' in the project root.", "STT Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        Thread sttThread = new Thread(() -> {
            try {
                Model model = new Model("model");
                float[] sampleRates = {16000.0f, 44100.0f, 48000.0f, 8000.0f};
                AudioFormat format = null;
                TargetDataLine microphone = null;

                for (float rate : sampleRates) {
                    try {
                        AudioFormat tempFormat = new AudioFormat(rate, 16, 1, true, false);
                        DataLine.Info info = new DataLine.Info(TargetDataLine.class, tempFormat);
                        if (AudioSystem.isLineSupported(info)) {
                            microphone = (TargetDataLine) AudioSystem.getLine(info);
                            format = tempFormat;
                            break;
                        }
                    } catch (Exception ignored) {}
                }

                if (microphone == null) {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(ui, "No supported microphone format found on your PC.", "STT Error", JOptionPane.ERROR_MESSAGE));
                    return;
                }

                microphone.open(format);
                microphone.start();

                JDialog dialog = new JDialog(ui, "STT Recording", false); // non-modal
                dialog.setLayout(new java.awt.BorderLayout());
                dialog.add(new JLabel("Recording... Speak now!", SwingConstants.CENTER), java.awt.BorderLayout.CENTER);
                JButton btnStop = new JButton("Stop & Send");
                boolean[] isRecording = {true};
                btnStop.addActionListener(e -> {
                    isRecording[0] = false;
                    dialog.dispose();
                });
                dialog.add(btnStop, java.awt.BorderLayout.SOUTH);
                dialog.setSize(250, 120);
                dialog.setLocationRelativeTo(ui);
                dialog.addWindowListener(new java.awt.event.WindowAdapter() {
                    public void windowClosing(java.awt.event.WindowEvent we) {
                        isRecording[0] = false;
                    }
                });
                SwingUtilities.invokeLater(() -> dialog.setVisible(true));

                Recognizer recognizer = new Recognizer(model, format.getSampleRate());
                byte[] b = new byte[4096];
                
                StringBuilder fullText = new StringBuilder();

                while (isRecording[0]) {
                    int numBytesRead = microphone.read(b, 0, 1024);
                    if (recognizer.acceptWaveForm(b, numBytesRead)) {
                        String result = recognizer.getResult();
                        String parsed = extractText(result);
                        if (!parsed.isEmpty()) fullText.append(parsed).append(" ");
                    }
                }
                
                String finalResult = recognizer.getFinalResult();
                String parsedFinal = extractText(finalResult);
                if (!parsedFinal.isEmpty()) fullText.append(parsedFinal);

                microphone.close();
                
                String transcribed = fullText.toString().trim();
                if (!transcribed.isEmpty()) {
                    Message msg = new Message(Message.MessageType.TEXT, client.getUsername(), "[Transcribed]: " + transcribed);
                    msg.setReceiver(selectedUser.equals("All") ? null : selectedUser);
                    client.sendMessage(msg);
                } else {
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(ui, "No speech detected. Please try again."));
                }

            } catch (Exception e) {
                e.printStackTrace();
                SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(ui, "STT Error: " + e.getMessage()));
            }
        });
        sttThread.start();
    }

    private String extractText(String json) {
        int start = json.indexOf("\"text\" : \"");
        if (start != -1) {
            start += 10;
            int end = json.indexOf("\"", start);
            if (end != -1) return json.substring(start, end).trim();
        }
        return "";
    }
}
