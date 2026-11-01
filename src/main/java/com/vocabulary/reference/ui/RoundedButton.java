package com.vocabulary.reference.ui;

import javax.swing.JButton;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public final class RoundedButton extends JButton {
    public enum Style { PRIMARY, SECONDARY, NEUTRAL }

    private final Style style;
    private boolean hover;
    private boolean pressed;

    public RoundedButton(String text, Style style) {
        super(text);
        this.style = style;
        setFont(Theme.BUTTON);
        setForeground(Theme.INK);
        setFocusPainted(false);
        setBorderPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 16, 12, 16));
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
            @Override public void mouseExited(MouseEvent e) { hover = false; pressed = false; repaint(); }
            @Override public void mousePressed(MouseEvent e) { pressed = true; repaint(); }
            @Override public void mouseReleased(MouseEvent e) { pressed = false; repaint(); }
        });
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color background = backgroundColor();
        int y = pressed ? 2 : 0;
        g.setColor(new Color(17, 17, 17, 18));
        g.fillRoundRect(1, y + 3, getWidth() - 2, getHeight() - 3, 18, 18);
        g.setColor(background);
        g.fillRoundRect(1, y, getWidth() - 2, getHeight() - 4, 18, 18);
        g.setColor(edgeColor());
        g.drawRoundRect(1, y, getWidth() - 2, getHeight() - 4, 18, 18);
        if (hover) {
            g.setComposite(AlphaComposite.SrcOver.derive(0.16f));
            g.setColor(Color.WHITE);
            g.fillRoundRect(2, y + 1, getWidth() - 4, getHeight() - 6, 16, 16);
        }
        g.dispose();
        super.paintComponent(graphics);
    }

    private Color backgroundColor() {
        if (style == Style.PRIMARY) return Theme.PINK;
        if (style == Style.SECONDARY) return Theme.BLUE;
        return Theme.WHITE;
    }

    private Color edgeColor() {
        if (style == Style.PRIMARY) return Theme.PINK_EDGE;
        if (style == Style.SECONDARY) return Theme.BLUE_EDGE;
        return Theme.BORDER;
    }
}
