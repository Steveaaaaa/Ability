package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ChargedLeapStateTrackerTest {
    @AfterEach
    void resetTracker() {
        ChargedLeapStateTracker.resetForTests();
    }

    @Test
    void measuresChargeAndConsumesDoubleJumpOnce() {
        UUID player = UUID.randomUUID();

        ChargedLeapStateTracker.beginCharge(player, 100L);
        assertEquals(15L, ChargedLeapStateTracker.releaseCharge(player, 115L).orElseThrow());
        assertTrue(ChargedLeapStateTracker.releaseCharge(player, 116L).isEmpty());

        ChargedLeapStateTracker.armLeap(player, 200L, 5.0D, 1.9D, 50, true);
        assertTrue(ChargedLeapStateTracker.useDoubleJump(player, 150L));
        assertFalse(ChargedLeapStateTracker.useDoubleJump(player, 151L));
    }

    @Test
    void impactArmsOneShotFallProtectionUntilExpiry() {
        UUID player = UUID.randomUUID();
        ChargedLeapStateTracker.armLeap(player, 200L, 5.0D, 1.7D, 38, false);

        ChargedLeapStateTracker.LeapState impact =
                ChargedLeapStateTracker.consumeImpact(player, 150L).orElseThrow();
        assertEquals(5.0D, impact.impactRadius());
        assertEquals(1.7D, impact.damageMultiplier());
        assertEquals(38, impact.stunTicks());
        assertTrue(ChargedLeapStateTracker.consumeImpact(player, 151L).isEmpty());
        assertTrue(ChargedLeapStateTracker.consumeFallProtection(player, 199L));
        assertFalse(ChargedLeapStateTracker.consumeFallProtection(player, 199L));
    }
}
