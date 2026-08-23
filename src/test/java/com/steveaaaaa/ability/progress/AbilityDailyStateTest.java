package com.steveaaaaa.ability.progress;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.steveaaaaa.ability.AbilityMod;
import org.junit.jupiter.api.Test;

class AbilityDailyStateTest {
    @Test
    void refreshesWhenTheGameDayChanges() {
        var abilityId = AbilityMod.id("well_prepared");
        AbilityDailyState consumed = AbilityDailyState.EMPTY.consume(abilityId, 12L);

        assertFalse(consumed.available(abilityId, 12L));
        assertTrue(consumed.available(abilityId, 13L));
    }

    @Test
    void repeatedConsumptionOnSameDayIsStable() {
        var abilityId = AbilityMod.id("well_prepared");
        AbilityDailyState consumed = AbilityDailyState.EMPTY.consume(abilityId, 4L);

        assertSame(consumed, consumed.consume(abilityId, 4L));
    }
}
