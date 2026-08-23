package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class WeakPointEffectTest {
    @Test
    void registersAndValidatesTheBuiltInWeakPointAbility() throws Exception {
        AbilityDefinition definition;
        try (var stream = WeakPointEffectTest.class.getResourceAsStream(
                "/data/ability/ability/abilities/weak_point.json"
        )) {
            if (stream == null) {
                throw new IllegalStateException("Missing weak point definition");
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        }

        assertTrue(AbilityEffectTypeRegistry.isRegistered(WeakPointEffect.TYPE));
        assertTrue(
                AbilityEffectTypeRegistry.validateDefinition(definition).isEmpty(),
                () -> AbilityEffectTypeRegistry.validateDefinition(definition).toString()
        );
    }

    @Test
    void resolvesThresholdMultiplierAndStunTicks() {
        WeakPointEffect.ResolvedRank rank = WeakPointEffect.resolve(new WeakPointEffect.RankValues(Map.of(
                "mark_threshold", 3.0D,
                "damage_multiplier", 2.35D,
                "stun_ticks", 5.0D
        )));

        assertEquals(3, rank.markThreshold());
        assertEquals(2.35D, rank.damageMultiplier());
        assertEquals(5, rank.stunTicks());
    }
}
