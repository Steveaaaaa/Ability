package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class IronCavalryEffectTest {
    @Test
    void registersTheIronCavalryEffectType() {
        assertTrue(AbilityEffectTypeRegistry.isRegistered(IronCavalryEffect.TYPE));
    }

    @Test
    void appliesMountedDamageAndPigArmorShares() {
        assertEquals(12.5F, IronCavalryEffect.applyDamageBonus(10.0F, 0.25D), 0.0001F);
        assertEquals(6.0D, IronCavalryEffect.sharedArmor(20.0D, 0.30D), 0.000001D);
    }
}
