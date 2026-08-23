package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;
import net.minecraft.resources.ResourceLocation;

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

    @Test
    void rejectsASecondSessionForTheSameTargetGroup() {
        ResourceLocation snowGolems = ResourceLocation.fromNamespaceAndPath("ability", "support_snow_golems");
        assertFalse(SupportAuraEffect.canStartSession(Set.of(snowGolems), snowGolems));
        assertTrue(SupportAuraEffect.canStartSession(Set.of(), snowGolems));
    }
}
