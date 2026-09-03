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

class HarvestEffectTest {
    @Test
    void calculatesSpreadsheetDamageFormula() {
        HarvestEffect.ResolvedRank rank = new HarvestEffect.ResolvedRank(10.0D, 30.0D);

        assertEquals(7.5D, HarvestEffect.bonusDamage(rank, 40.0D, 40.0D));
        assertEquals(5.0D, HarvestEffect.bonusDamage(rank, 20.0D, 40.0D));
    }

    @Test
    void clampsInvalidRuntimeFoodValues() {
        HarvestEffect.ResolvedRank rank = new HarvestEffect.ResolvedRank(5.0D, 15.0D);

        assertEquals(0.0D, HarvestEffect.bonusDamage(rank, -2.0D, 40.0D));
        assertEquals(0.0D, HarvestEffect.bonusDamage(rank, Double.NaN, 40.0D));
    }

    @Test
    void rejectsIncompleteRankValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> HarvestEffect.resolve(new HarvestEffect.RankValues(Map.of("rank_factor", 1.0D)))
        );
    }

    @Test
    void registersAndValidatesBuiltInDefinition() throws Exception {
        AbilityDefinition definition;
        try (var stream = HarvestEffectTest.class.getResourceAsStream(
                "/data/fantasypower/fantasypower/abilities/harvest.json"
        )) {
            if (stream == null) {
                throw new IllegalStateException("Missing harvest definition");
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        }

        assertTrue(AbilityEffectTypeRegistry.isRegistered(HarvestEffect.TYPE));
        assertTrue(
                AbilityEffectTypeRegistry.validateDefinition(definition).isEmpty(),
                () -> AbilityEffectTypeRegistry.validateDefinition(definition).toString()
        );
    }
}
