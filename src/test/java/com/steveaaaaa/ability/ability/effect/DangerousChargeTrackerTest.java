package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DangerousChargeTrackerTest {
    @AfterEach
    void resetTracker() {
        DangerousChargeTracker.resetForTests();
    }

    @Test
    void snapshotsMultiplierUntilProjectileStateExpires() {
        UUID projectile = UUID.randomUUID();

        DangerousChargeTracker.track(projectile, 200L, 1.9D);
        assertEquals(1.9D, DangerousChargeTracker.damageMultiplier(projectile, 199L));
        assertEquals(1.0D, DangerousChargeTracker.damageMultiplier(projectile, 200L));
    }
}
