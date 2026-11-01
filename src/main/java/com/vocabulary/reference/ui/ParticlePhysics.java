package com.vocabulary.reference.ui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** Pure animation model so collision and finite lifetime can be tested without Swing. */
public final class ParticlePhysics {
    public static final class Particle {
        public double x, y, vx, vy, angle, spin, alpha, life;
        Particle(double x, double y, double vx, double vy, double spin, double life) {
            this.x = x; this.y = y; this.vx = vx; this.vy = vy; this.spin = spin; this.life = life; this.alpha = 1.0;
        }
    }

    private final List<Particle> particles = new ArrayList<>();
    private final double gravity;
    private final Random random;

    public ParticlePhysics(int count, long seed, double width, double height) {
        this(count, seed, width, height, 540.0);
    }
    public ParticlePhysics(int count, long seed, double width, double height, double gravity) {
        this.gravity = gravity;
        this.random = new Random(seed);
        for (int i = 0; i < count; i++) {
            double x = width / 2.0 + (random.nextDouble() - 0.5) * 50;
            double y = height * 0.28;
            double vx = (random.nextDouble() - 0.5) * 520;
            double vy = -100 - random.nextDouble() * 420;
            particles.add(new Particle(x, y, vx, vy, (random.nextDouble() - 0.5) * 8, 2.5 + random.nextDouble() * 3));
        }
    }
    public List<Particle> particles() { return particles; }
    public boolean isAlive() { return !particles.isEmpty(); }
    public void update(double seconds, double width, double height) {
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle p = iterator.next();
            p.vy += gravity * seconds;
            p.x += p.vx * seconds;
            p.y += p.vy * seconds;
            p.angle += p.spin * seconds;
            if (p.x < 0) { p.x = 0; p.vx = Math.abs(p.vx) * 0.82; }
            if (p.x > width) { p.x = width; p.vx = -Math.abs(p.vx) * 0.82; }
            if (p.y > height) { p.y = height; p.vy = -Math.abs(p.vy) * 0.58; }
            p.life -= seconds;
            p.alpha = Math.max(0, Math.min(1, p.life));
            if (p.life <= 0) iterator.remove();
        }
    }
}
