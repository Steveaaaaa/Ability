package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CounterSniperEffectTest {
    @Test
    void usesAStrictMinimumDistance() {
        assertFalse(CounterSniperEffect.isDistant(64.0D, 8.0D));
        assertTrue(CounterSniperEffect.isDistant(64.01D, 8.0D));
    }

    @Test
    void resolvesTheRankDamageMultiplier() {
        CounterSniperEffect.ResolvedRank rank = CounterSniperEffect.resolve(
                new CounterSniperEffect.RankValues(Map.of("damage_multiplier", 1.5D))
        );

        assertEquals(1.5D, rank.damageMultiplier());
    }

    @Test
    void registersAndValidatesTheBuiltInDefinition() throws Exception {
        AbilityDefinition definition;
        try (var stream = CounterSniperEffectTest.class.getResourceAsStream(
                "/data/fantasypower/fantasypower/abilities/counter_sniper.json"
        )) {
            if (stream == null) {
                throw new IllegalStateException("Missing counter sniper definition");
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        }

        assertTrue(AbilityEffectTypeRegistry.isRegistered(CounterSniperEffect.TYPE));
        assertTrue(
                AbilityEffectTypeRegistry.validateDefinition(definition).isEmpty(),
                () -> AbilityEffectTypeRegistry.validateDefinition(definition).toString()
        );
    }
}
