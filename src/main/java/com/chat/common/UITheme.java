package com.chat.common;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Shared visual language for the desktop client and server windows. */
public final class UITheme {
    public static final Color PRIMARY = new Color(91, 108, 255);
    public static final Color PRIMARY_DARK = new Color(66, 78, 214);
    public static final Color ACCENT = new Color(38, 198, 218);
    public static final Color BACKGROUND = new Color(246, 248, 252);
    public static final Color SURFACE = Color.WHITE;
    public static final Color SURFACE_ALT = new Color(239, 243, 250);
    public static final Color TEXT = new Color(31, 39, 55);
    public static final Color TEXT_MUTED = new Color(119, 130, 151);
    public static final Color BORDER = new Color(226, 231, 240);
    public static final Color SUCCESS = new Color(32, 181, 126);
    public static final Color DANGER = new Color(235, 87, 87);

    private UITheme() {}

    public static void applyDefaults() {
        UIManager.put("defaultFont", new Font("Segoe UI", Font.PLAIN, 14));
        UIManager.put("Component.arc", 16);
        UIManager.put("Button.arc", 16);
        UIManager.put("TextComponent.arc", 14);
        UIManager.put("ScrollBar.thumbArc", 999);
        UIManager.put("ScrollBar.width", 10);
        UIManager.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        UIManager.put("ToolTip.background", TEXT);
        UIManager.put("ToolTip.foreground", Color.WHITE);
        UIManager.put("ToolTip.border", new EmptyBorder(7, 10, 7, 10));
    }

    public static JLabel avatar(String name, int size) {
        String initial = name == null || name.isBlank() ? "?" : name.substring(0, 1).toUpperCase();
        JLabel label = new JLabel(initial, SwingConstants.CENTER);
        label.setOpaque(true);
        label.setBackground(PRIMARY);
        label.setForeground(Color.WHITE);
        label.setFont(new Font("Segoe UI", Font.BOLD, Math.max(14, size / 3)));
        label.setPreferredSize(new Dimension(size, size));
        label.setMinimumSize(new Dimension(size, size));
        label.setBorder(BorderFactory.createLineBorder(new Color(255, 255, 255, 120), 2, true));
        return label;
    }

    public static class RoundedPanel extends JPanel {
        private final int radius;

        public RoundedPanel(int radius) {
            this.radius = radius;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static class GradientPanel extends JPanel {
        private final Color start;
        private final Color end;

        public GradientPanel(Color start, Color end) {
            this.start = start;
            this.end = end;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setPaint(new GradientPaint(0, 0, start, getWidth(), getHeight(), end));
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.dispose();
            super.paintComponent(g);
        }
    }

    public static class RoundedButton extends JButton {
        private final Color normalColor;
        private final Color hoverColor;
        private Color currentColor;
        private final int radius;

        public RoundedButton(String text, Color normalColor, Color hoverColor, int radius) {
            super(text);
            this.normalColor = normalColor;
            this.hoverColor = hoverColor;
            this.currentColor = normalColor;
            this.radius = radius;
            setForeground(Color.WHITE);
            setFont(new Font("Segoe UI", Font.BOLD, 14));
            setFocusPainted(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(new EmptyBorder(10, 18, 10, 18));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { currentColor = RoundedButton.this.hoverColor; repaint(); }
                @Override public void mouseExited(MouseEvent e) { currentColor = RoundedButton.this.normalColor; repaint(); }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isEnabled() ? currentColor : new Color(180, 188, 205));
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
