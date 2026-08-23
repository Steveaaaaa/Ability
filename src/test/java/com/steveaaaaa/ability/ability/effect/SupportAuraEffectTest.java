package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SupportAuraEffectTest {
    @Test
    void registersTheSupportAuraEffectType() {
        assertTrue(AbilityEffectTypeRegistry.isRegistered(SupportAuraEffect.TYPE));
    }

    @Test
    void splitsTotalHealingEvenlyAcrossFivePulses() {
        assertEquals(4.0D, SupportAuraEffect.healingPerPulse(20.0D, 100.0D, 5), 0.000001D);
        assertEquals(0.0D, SupportAuraEffect.healingPerPulse(20.0D, 100.0D, 0), 0.000001D);
    }
}
