package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class InactivityTrackerTest {
    @AfterEach
    void resetTracker() {
        InactivityTracker.resetForTests();
    }

    @Test
    void startsAtZeroAndMeasuresElapsedServerTicks() {
        UUID player = UUID.randomUUID();

        assertEquals(0L, InactivityTracker.elapsedAt(player, 100L));
        assertEquals(40L, InactivityTracker.elapsedAt(player, 140L));
    }

    @Test
    void activityAndClockRollbackRestartTheTimer() {
        UUID player = UUID.randomUUID();
        InactivityTracker.recordAt(player, 100L);

        assertEquals(20L, InactivityTracker.elapsedAt(player, 120L));
        InactivityTracker.recordAt(player, 118L);
        assertEquals(7L, InactivityTracker.elapsedAt(player, 125L));
        assertEquals(0L, InactivityTracker.elapsedAt(player, 90L));
    }
}
