package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class WellPreparedTrackerTest {
    @Test
    void expiresAtTheConfiguredGameTime() {
        UUID playerId = UUID.randomUUID();
        WellPreparedTracker.grant(playerId, 120L);

        assertTrue(WellPreparedTracker.isInvulnerable(playerId, 119L));
        assertFalse(WellPreparedTracker.isInvulnerable(playerId, 120L));
    }

    @Test
    void longerGrantWins() {
        UUID playerId = UUID.randomUUID();
        WellPreparedTracker.grant(playerId, 120L);
        WellPreparedTracker.grant(playerId, 140L);

        assertTrue(WellPreparedTracker.isInvulnerable(playerId, 130L));
        WellPreparedTracker.forget(playerId);
    }
}
