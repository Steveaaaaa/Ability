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

class DangerousChargeEffectTest {
    @Test
    void convertsSpreadsheetBonusToFinalMultiplier() {
        DangerousChargeEffect.ResolvedRank first = DangerousChargeEffect.resolve(
                new DangerousChargeEffect.RankValues(Map.of("explosion_damage_bonus", 60.0D))
        );
        DangerousChargeEffect.ResolvedRank last = DangerousChargeEffect.resolve(
                new DangerousChargeEffect.RankValues(Map.of("explosion_damage_bonus", 90.0D))
        );

        assertEquals(1.6D, first.damageMultiplier());
        assertEquals(1.9D, last.damageMultiplier());
    }

    @Test
    void registersAndValidatesBuiltInDefinition() throws Exception {
        AbilityDefinition definition;
        try (var stream = DangerousChargeEffectTest.class.getResourceAsStream(
                "/data/fantasypower/fantasypower/abilities/dangerous_charge.json"
        )) {
            if (stream == null) {
                throw new IllegalStateException("Missing dangerous charge definition");
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        }

        assertTrue(AbilityEffectTypeRegistry.isRegistered(DangerousChargeEffect.TYPE));
        assertTrue(
                AbilityEffectTypeRegistry.validateDefinition(definition).isEmpty(),
                () -> AbilityEffectTypeRegistry.validateDefinition(definition).toString()
        );
    }
}
