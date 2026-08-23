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

class StealthEffectTest {
    @Test
    void convertsSecondsToTicksAndResolvesDamageMultiplier() {
        StealthEffect.ResolvedRank rank = StealthEffect.resolve(new StealthEffect.RankValues(Map.of(
                "wait_seconds", 9.0D,
                "damage_multiplier", 3.0D
        )));

        assertEquals(180L, rank.waitTicks());
        assertEquals(3.0D, rank.damageMultiplier());
    }

    @Test
    void registersAndValidatesTheBuiltInDefinition() throws Exception {
        AbilityDefinition definition;
        try (var stream = StealthEffectTest.class.getResourceAsStream(
                "/data/ability/ability/abilities/stealth.json"
        )) {
            if (stream == null) {
                throw new IllegalStateException("Missing stealth definition");
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        }

        assertTrue(AbilityEffectTypeRegistry.isRegistered(StealthEffect.TYPE));
        assertTrue(
                AbilityEffectTypeRegistry.validateDefinition(definition).isEmpty(),
                () -> AbilityEffectTypeRegistry.validateDefinition(definition).toString()
        );
    }
}
