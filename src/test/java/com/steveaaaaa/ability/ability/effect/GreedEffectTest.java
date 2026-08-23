package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class GreedEffectTest {
    @Test
    void registersTheGreedEffectType() {
        assertTrue(AbilityEffectTypeRegistry.isRegistered(GreedEffect.TYPE));
    }

    @Test
    void unlocksToolTiersByRank() {
        ResourceLocation axes = ResourceLocation.fromNamespaceAndPath("ability", "axes");
        ResourceLocation shears = ResourceLocation.fromNamespaceAndPath("ability", "shears");
        List<GreedEffect.ToolTier> tiers = List.of(
                new GreedEffect.ToolTier(1, axes),
                new GreedEffect.ToolTier(2, shears)
        );

        assertTrue(GreedEffect.rankUnlocksTool(1, axes, tiers));
        assertFalse(GreedEffect.rankUnlocksTool(1, shears, tiers));
        assertTrue(GreedEffect.rankUnlocksTool(2, shears, tiers));
    }
}
