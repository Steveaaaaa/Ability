package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FrugalityEffectTest {
    @Test
    void convertsSpreadsheetPercentagesToFractions() {
        FrugalityEffect.ResolvedRank rank = FrugalityEffect.resolve(new FrugalityEffect.RankValues(Map.of(
                "healing_hunger_reduction_percent", 30.0D,
                "ability_hunger_reduction_percent", 60.0D
        )));

        assertEquals(0.30D, rank.healingHungerReduction());
        assertEquals(0.60D, rank.abilityHungerReduction());
    }

    @Test
    void detectsHealingExhaustionAcrossVanillaCycleProcessing() {
        assertEquals(6.0D, FrugalityEffect.naturalHealingExhaustion(4.5D, 6.5D, 1.0D, 4.0D, 6.0D));
        assertEquals(0.0D, FrugalityEffect.naturalHealingExhaustion(2.0D, 2.0D, 1.0D, 4.0D, 6.0D));
    }

    @Test
    void rejectsIncompleteRankValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> FrugalityEffect.resolve(new FrugalityEffect.RankValues(Map.of(
                        "healing_hunger_reduction_percent", 7.5D
                )))
        );
    }

    @Test
    void registersAndValidatesBuiltInDefinition() throws Exception {
        AbilityDefinition definition;
        try (var stream = FrugalityEffectTest.class.getResourceAsStream(
                "/data/ability/ability/abilities/frugality.json"
        )) {
            if (stream == null) {
                throw new IllegalStateException("Missing frugality definition");
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        }

        assertTrue(AbilityEffectTypeRegistry.isRegistered(FrugalityEffect.TYPE));
        assertTrue(
                AbilityEffectTypeRegistry.validateDefinition(definition).isEmpty(),
                () -> AbilityEffectTypeRegistry.validateDefinition(definition).toString()
        );
    }
}
