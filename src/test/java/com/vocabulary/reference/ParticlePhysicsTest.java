package com.vocabulary.reference;

import com.vocabulary.reference.ui.ParticlePhysics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ParticlePhysicsTest {
    @Test
    void particlesBounceAndEventuallyExpire() {
        ParticlePhysics physics = new ParticlePhysics(12, 42L, 200, 120);
        for (int i = 0; i < 800 && physics.isAlive(); i++) physics.update(0.016, 200, 120);
        assertFalse(physics.isAlive());
    }

    @Test
    void deterministicSeedProducesSameInitialState() {
        ParticlePhysics a = new ParticlePhysics(2, 7L, 200, 120);
        ParticlePhysics b = new ParticlePhysics(2, 7L, 200, 120);
        assertEquals(a.particles().get(0).x, b.particles().get(0).x);
        assertEquals(a.particles().get(0).vy, b.particles().get(0).vy);
    }
}
