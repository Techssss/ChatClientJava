package com.chat.common;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public class SteganoUtils {

    public static byte[] encodeTextToImage(byte[] imageBytes, String text, String password) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
        String message = password + "::" + text;
        byte[] msgBytes = message.getBytes();
        int msgLen = msgBytes.length;

        int width = img.getWidth();
        int height = img.getHeight();

        int maxMsgLen = (width * height * 3) / 8; // 3 channels, 1 bit per channel
        if (msgLen + 4 > maxMsgLen) {
            throw new Exception("Image is too small to hold this message");
        }

        // Hide length in first 32 bits (4 pixels approx)
        int pixelIdx = 0;
        for (int i = 0; i < 32; i++) {
            int bit = (msgLen >> (31 - i)) & 1;
            int x = pixelIdx % width;
            int y = pixelIdx / width;
            int rgb = img.getRGB(x, y);
            int channel = i % 3; // 0=R, 1=G, 2=B
            
            int r = (rgb >> 16) & 0xFF;
            int g = (rgb >> 8) & 0xFF;
            int b = rgb & 0xFF;

            if (channel == 0) r = (r & 0xFE) | bit;
            else if (channel == 1) g = (g & 0xFE) | bit;
            else b = (b & 0xFE) | bit;

            img.setRGB(x, y, (rgb & 0xFF000000) | (r << 16) | (g << 8) | b);
            if (channel == 2) pixelIdx++;
        }
        if (32 % 3 != 0) pixelIdx++;

        // Hide message
        int bitIdx = 0;
        for (int i = 0; i < msgLen; i++) {
            byte bValue = msgBytes[i];
            for (int j = 0; j < 8; j++) {
                int bit = (bValue >> (7 - j)) & 1;
                int x = pixelIdx % width;
                int y = pixelIdx / width;
                int rgb = img.getRGB(x, y);
                int channel = bitIdx % 3;

                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;

                if (channel == 0) r = (r & 0xFE) | bit;
                else if (channel == 1) g = (g & 0xFE) | bit;
                else b = (b & 0xFE) | bit;

                img.setRGB(x, y, (rgb & 0xFF000000) | (r << 16) | (g << 8) | b);
                
                bitIdx++;
                if (bitIdx % 3 == 0) pixelIdx++;
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        return baos.toByteArray();
    }

    public static String decodeTextFromImage(byte[] imageBytes, String password) throws Exception {
        BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
        int width = img.getWidth();
        
        int msgLen = 0;
        int pixelIdx = 0;
        
        // Extract length
        for (int i = 0; i < 32; i++) {
            int x = pixelIdx % width;
            int y = pixelIdx / width;
            int rgb = img.getRGB(x, y);
            int channel = i % 3;

            int bit = 0;
            if (channel == 0) bit = ((rgb >> 16) & 0xFF) & 1;
            else if (channel == 1) bit = ((rgb >> 8) & 0xFF) & 1;
            else bit = (rgb & 0xFF) & 1;

            msgLen = (msgLen << 1) | bit;
            if (channel == 2) pixelIdx++;
        }
        if (32 % 3 != 0) pixelIdx++;

        if (msgLen <= 0 || msgLen > 10000) return null; // Sanity check

        byte[] msgBytes = new byte[msgLen];
        int bitIdx = 0;
        for (int i = 0; i < msgLen; i++) {
            byte bValue = 0;
            for (int j = 0; j < 8; j++) {
                int x = pixelIdx % width;
                int y = pixelIdx / width;
                int rgb = img.getRGB(x, y);
                int channel = bitIdx % 3;

                int bit = 0;
                if (channel == 0) bit = ((rgb >> 16) & 0xFF) & 1;
                else if (channel == 1) bit = ((rgb >> 8) & 0xFF) & 1;
                else bit = (rgb & 0xFF) & 1;

                bValue = (byte) ((bValue << 1) | bit);
                
                bitIdx++;
                if (bitIdx % 3 == 0) pixelIdx++;
            }
            msgBytes[i] = bValue;
        }

        String decodedMessage = new String(msgBytes);
        String[] parts = decodedMessage.split("::", 2);
        if (parts.length == 2 && parts[0].equals(password)) {
            return parts[1];
        } else {
            return null; // Incorrect password or not steganography
        }
    }
}
