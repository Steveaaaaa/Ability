package com.steveaaaaa.ability.trigger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class MovementExperienceTrackerTest {
    @AfterEach
    void clearTracker() {
        MovementExperienceTracker.clear();
    }

    @Test
    void emitsAccumulatedHorizontalDistanceEveryTwentyTicks() {
        UUID player = UUID.randomUUID();
        assertTrue(update(player, 0.0D).isEmpty());
        for (int tick = 1; tick < 20; tick++) {
            assertTrue(update(player, tick).isEmpty());
        }

        MovementExperienceTracker.Sample sample = update(player, 20.0D).orElseThrow();

        assertEquals(20.0D, sample.distance());
        assertEquals(ExperienceContext.MovementMode.ON_FOOT, sample.mode());
    }

    @Test
    void ignoresTeleportSizedMovementWithinAWindow() {
        UUID player = UUID.randomUUID();
        assertTrue(update(player, 0.0D).isEmpty());
        assertTrue(update(player, 100.0D).isEmpty());
        Optional<MovementExperienceTracker.Sample> emitted = Optional.empty();
        for (int tick = 1; tick < 20; tick++) {
            emitted = update(player, 100.0D + tick);
        }

        MovementExperienceTracker.Sample sample = emitted.orElseThrow();

        assertEquals(19.0D, sample.distance());
    }

    private static Optional<MovementExperienceTracker.Sample> update(UUID player, double x) {
        return MovementExperienceTracker.update(
                player,
                Level.OVERWORLD,
                new Vec3(x, 64.0D, 0.0D),
                ExperienceContext.MovementMode.ON_FOOT
        );
    }
}
