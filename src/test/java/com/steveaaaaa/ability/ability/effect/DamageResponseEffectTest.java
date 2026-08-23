package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DamageResponseEffectTest {
    @Test
    void registersTheDamageResponseType() {
        assertTrue(AbilityEffectTypeRegistry.isRegistered(DamageResponseEffect.TYPE));
    }

    @Test
    void requiresActualDamageAndStrictlyLowHealth() {
        assertFalse(DamageResponseEffect.shouldTrigger(0.0F, 5.0F, 20.0F, 0.4D, true, true));
        assertFalse(DamageResponseEffect.shouldTrigger(2.0F, 8.0F, 20.0F, 0.4D, true, true));
        assertTrue(DamageResponseEffect.shouldTrigger(2.0F, 7.9F, 20.0F, 0.4D, true, true));
    }

    @Test
    void canRequireTheDamageSourceToBeLiving() {
        assertFalse(DamageResponseEffect.shouldTrigger(2.0F, 5.0F, 20.0F, 0.4D, true, false));
        assertTrue(DamageResponseEffect.shouldTrigger(2.0F, 5.0F, 20.0F, 0.4D, false, false));
    }
}
