package com.vocabulary.reference.ui;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/** Small vector icons drawn with the same pink/blue palette as the UI. */
public final class AppIcon implements Icon {
    public enum Kind { LEARN, MISTAKES, HISTORY, DICTIONARY, SETTINGS, PLAY, BACK }
    private final Kind kind;
    private final int size;
    private final Color fill;
    private final Color edge;

    public AppIcon(Kind kind, int size, boolean pink) {
        this.kind = kind;
        this.size = size;
        this.fill = pink ? Theme.PINK : Theme.BLUE;
        this.edge = pink ? Theme.PINK_EDGE : Theme.BLUE_EDGE;
    }
    @Override public int getIconWidth() { return size; }
    @Override public int getIconHeight() { return size; }

    @Override public void paintIcon(Component component, Graphics graphics, int x, int y) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(fill);
        g.fillRoundRect(x, y, size, size, 14, 14);
        g.setColor(edge);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x, y, size - 1, size - 1, 14, 14);
        g.setColor(Theme.INK);
        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        int cx = x + size / 2, cy = y + size / 2;
        switch (kind) {
            case LEARN:
                g.drawLine(x + 11, y + 13, cx, y + 10); g.drawLine(cx, y + 10, x + size - 11, y + 13);
                g.drawLine(x + 11, y + 13, x + 11, y + size - 12); g.drawLine(x + size - 11, y + 13, x + size - 11, y + size - 12);
                g.drawLine(x + 11, y + size - 12, cx, y + size - 8); g.drawLine(cx, y + size - 8, x + size - 11, y + size - 12);
                break;
            case MISTAKES:
                g.drawOval(x + 10, y + 10, size - 20, size - 20); g.drawLine(cx, y + 15, cx, y + size - 15); g.fillOval(cx - 1, y + size - 11, 3, 3); break;
            case HISTORY:
                g.drawOval(x + 10, y + 10, size - 20, size - 20); g.drawLine(cx, cy, cx, y + 15); g.drawLine(cx, cy, x + size - 14, cy); break;
            case DICTIONARY:
                g.drawOval(x + 10, y + 10, size - 20, size - 20); g.drawLine(x + size - 14, y + size - 14, x + size - 8, y + size - 8); break;
            case SETTINGS:
                g.drawOval(x + 10, y + 10, size - 20, size - 20); g.drawOval(x + 17, y + 17, size - 34, size - 34);
                for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; int x1 = (int) (cx + Math.cos(a) * (size / 2 - 10)); int y1 = (int) (cy + Math.sin(a) * (size / 2 - 10)); int x2 = (int) (cx + Math.cos(a) * (size / 2 - 5)); int y2 = (int) (cy + Math.sin(a) * (size / 2 - 5)); g.drawLine(x1, y1, x2, y2); }
                break;
            case PLAY:
                java.awt.Polygon triangle = new java.awt.Polygon(new int[]{x + 17, x + 17, x + size - 13}, new int[]{y + 12, y + size - 12, cy}, 3); g.fillPolygon(triangle); break;
            case BACK:
                g.drawLine(x + size - 12, cy, x + 13, cy); g.drawLine(x + 13, cy, x + 21, y + 12); g.drawLine(x + 13, cy, x + 21, y + size - 12); break;
        }
        g.dispose();
    }
}
