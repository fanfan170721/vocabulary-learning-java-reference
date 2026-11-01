package com.vocabulary.reference.ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

public final class CelebrationOverlay extends JComponent {
    private final Timer timer;
    private ParticlePhysics physics;
    private long lastNanos;

    public CelebrationOverlay() {
        setOpaque(false);
        timer = new Timer(16, e -> tick());
        setVisible(false);
    }

    public void start(boolean reducedMotion) {
        stop();
        if (reducedMotion) return;
        physics = new ParticlePhysics(90, System.nanoTime(), Math.max(1, getWidth()), Math.max(1, getHeight()));
        lastNanos = System.nanoTime();
        setVisible(true);
        timer.start();
    }
    public void stop() {
        timer.stop();
        physics = null;
        setVisible(false);
        repaint();
    }
    public boolean isRunning() { return timer.isRunning(); }
    @Override public boolean contains(int x, int y) { return false; }
    private void tick() {
        if (physics == null) { stop(); return; }
        long now = System.nanoTime();
        double seconds = Math.min(0.05, (now - lastNanos) / 1_000_000_000.0);
        lastNanos = now;
        physics.update(seconds, getWidth(), getHeight());
        if (!physics.isAlive()) stop();
        repaint();
    }
    @Override protected void paintComponent(Graphics graphics) {
        if (physics == null) return;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color[] colors = {Theme.PINK_EDGE, Theme.BLUE_EDGE, Theme.PINK, Theme.BLUE, Theme.INK};
        int i = 0;
        for (ParticlePhysics.Particle p : physics.particles()) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) p.alpha));
            g.setColor(colors[i++ % colors.length]);
            int w = 8, h = 14;
            g.rotate(p.angle, p.x, p.y);
            g.fillRect((int) p.x - w / 2, (int) p.y - h / 2, w, h);
            g.rotate(-p.angle, p.x, p.y);
        }
        g.dispose();
    }
}
