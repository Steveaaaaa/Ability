package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlastExcavationEffectTest {
    @Test
    void registersTheBlastExcavationEffectType() {
        assertTrue(AbilityEffectTypeRegistry.isRegistered(BlastExcavationEffect.TYPE));
    }

    @Test
    void reducesOnlyTheConfiguredFraction() {
        assertEquals(6.0F, BlastExcavationEffect.reduceDamage(10.0F, 0.4D), 0.0001F);
        assertEquals(0.0F, BlastExcavationEffect.reduceDamage(10.0F, 2.0D), 0.0001F);
    }
}
