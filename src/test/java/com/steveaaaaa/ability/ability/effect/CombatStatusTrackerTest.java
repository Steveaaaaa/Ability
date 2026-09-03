package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CombatStatusTrackerTest {
    private static final ResourceLocation MARK = ResourceLocation.fromNamespaceAndPath("fantasypower", "test_mark");

    @AfterEach
    void resetTracker() {
        CombatStatusTracker.resetForTests();
    }

    @Test
    void keepsMarksSeparateForEachOwnerAndTarget() {
        UUID ownerA = UUID.randomUUID();
        UUID ownerB = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        assertEquals(1, CombatStatusTracker.addMark(ownerA, target, MARK, 1, 3).remainingCount());
        assertEquals(1, CombatStatusTracker.addMark(ownerB, target, MARK, 1, 3).remainingCount());
        assertEquals(2, CombatStatusTracker.addMark(ownerA, target, MARK, 1, 3).remainingCount());
    }

    @Test
    void clearsOnlyTheTriggeredMarkStackAtThreshold() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        assertFalse(CombatStatusTracker.addMark(owner, target, MARK, 1, 2).triggered());
        assertTrue(CombatStatusTracker.addMark(owner, target, MARK, 1, 2).triggered());
        assertEquals(1, CombatStatusTracker.addMark(owner, target, MARK, 1, 2).remainingCount());
    }

    @Test
    void tracksAndConsumesGlowingOneShotMarks() {
        UUID owner = UUID.randomUUID();
        UUID target = UUID.randomUUID();

        CombatStatusTracker.setMark(owner, target, MARK, true);

        assertTrue(CombatStatusTracker.hasMark(owner, target, MARK));
        assertTrue(CombatStatusTracker.hasGlowingMark(target));
        assertTrue(CombatStatusTracker.consumeMark(owner, target, MARK));
        assertFalse(CombatStatusTracker.hasMark(owner, target, MARK));
        assertFalse(CombatStatusTracker.hasGlowingMark(target));
    }
}
