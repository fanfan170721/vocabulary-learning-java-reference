package com.vocabulary.reference.ui;

import javax.swing.BorderFactory;
import javax.swing.UIManager;
import javax.swing.border.Border;
import java.awt.Color;
import java.awt.Font;

/** Shared visual language for the reference Swing application. */
public final class Theme {
    public static final Color WHITE = new Color(255, 255, 255);
    public static final Color INK = new Color(17, 17, 17);
    public static final Color MUTED = new Color(94, 101, 112);
    public static final Color PINK = new Color(255, 244, 254);
    public static final Color BLUE = new Color(228, 245, 255);
    public static final Color PINK_EDGE = new Color(231, 166, 210);
    public static final Color BLUE_EDGE = new Color(139, 198, 224);
    public static final Color BORDER = new Color(226, 232, 238);
    public static final Font TITLE = new Font("Segoe UI", Font.BOLD, 25);
    public static final Font SUBTITLE = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font BODY = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font BUTTON = new Font("Segoe UI", Font.BOLD, 14);

    private Theme() { }

    public static void applyGlobal() {
        UIManager.put("Panel.background", WHITE);
        UIManager.put("Viewport.background", WHITE);
        UIManager.put("Label.foreground", INK);
        UIManager.put("Button.font", BUTTON);
        UIManager.put("Button.foreground", INK);
        UIManager.put("TextField.font", BODY);
        UIManager.put("TextField.foreground", INK);
        UIManager.put("TextField.background", WHITE);
        UIManager.put("TextArea.font", BODY);
        UIManager.put("TextArea.foreground", INK);
        UIManager.put("TextArea.background", WHITE);
        UIManager.put("List.font", BODY);
        UIManager.put("List.foreground", INK);
        UIManager.put("List.background", WHITE);
        UIManager.put("List.selectionBackground", PINK);
        UIManager.put("List.selectionForeground", INK);
        UIManager.put("CheckBox.font", BODY);
        UIManager.put("CheckBox.foreground", INK);
    }

    public static Border cardBorder(Color edge) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(edge, 1, true),
                BorderFactory.createEmptyBorder(14, 16, 14, 16));
    }
}
