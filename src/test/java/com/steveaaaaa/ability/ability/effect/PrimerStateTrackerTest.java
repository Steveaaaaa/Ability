package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PrimerStateTrackerTest {
    @AfterEach
    void resetTracker() {
        PrimerStateTracker.resetForTests();
    }

    @Test
    void measuresOneShotChargeDuration() {
        UUID player = UUID.randomUUID();

        PrimerStateTracker.beginCharge(player, 100L);
        assertEquals(30L, PrimerStateTracker.releaseCharge(player, 130L).orElseThrow());
        assertTrue(PrimerStateTracker.releaseCharge(player, 131L).isEmpty());
    }

    @Test
    void tracksProjectileMultiplierUntilExpiry() {
        UUID projectile = UUID.randomUUID();

        PrimerStateTracker.trackProjectile(projectile, 200L, 1.45D);
        assertEquals(1.45D, PrimerStateTracker.explosionDamageMultiplier(projectile, 199L));
        assertEquals(1.0D, PrimerStateTracker.explosionDamageMultiplier(projectile, 200L));
    }
}
