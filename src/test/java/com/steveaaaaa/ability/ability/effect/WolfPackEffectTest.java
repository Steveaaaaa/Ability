package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WolfPackEffectTest {
    @Test
    void registersTheWolfPackEffectType() {
        assertTrue(AbilityEffectTypeRegistry.isRegistered(WolfPackEffect.TYPE));
    }

    @Test
    void resolvesPercentagesAndCooldown() {
        WolfPackEffect.ResolvedRank rank = WolfPackEffect.resolve(new WolfPackEffect.RankValues(Map.of(
                "damage_bonus_percent", 50.0D,
                "dodge_chance_percent", 35.0D,
                "cooldown_seconds", 72.0D
        )));

        assertEquals(0.5D, rank.damageBonus(), 0.000001D);
        assertEquals(0.35D, rank.dodgeChance(), 0.000001D);
        assertEquals(1440, rank.cooldownTicks());
    }

    @Test
    void buffWindowUsesExclusiveEndTick() {
        WolfPackEffect.WolfState state = new WolfPackEffect.WolfState(
                true, 200L, 1000L, 0.5D, 0.35D, UUID.randomUUID()
        );
        assertTrue(WolfPackEffect.isBuffActive(state, 199L));
        assertFalse(WolfPackEffect.isBuffActive(state, 200L));
        assertEquals(15.0F, WolfPackEffect.applyDamageBonus(10.0F, 0.5D), 0.0001F);
    }
}
