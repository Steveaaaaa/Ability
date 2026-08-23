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

class PrimerEffectTest {
    @Test
    void convertsSpreadsheetChargeAndDamageValues() {
        PrimerEffect.ResolvedRank first = PrimerEffect.resolve(new PrimerEffect.RankValues(Map.of(
                "charge_seconds", 2.0D,
                "explosion_damage_bonus", 25.0D
        )));
        PrimerEffect.ResolvedRank last = PrimerEffect.resolve(new PrimerEffect.RankValues(Map.of(
                "charge_seconds", 1.0D,
                "explosion_damage_bonus", 45.0D
        )));

        assertEquals(40, first.requiredChargeTicks());
        assertEquals(1.25D, first.explosionDamageMultiplier());
        assertEquals(20, last.requiredChargeTicks());
        assertEquals(1.45D, last.explosionDamageMultiplier());
    }

    @Test
    void registersAndValidatesBuiltInDefinition() throws Exception {
        AbilityDefinition definition;
        try (var stream = PrimerEffectTest.class.getResourceAsStream(
                "/data/ability/ability/abilities/primer.json"
        )) {
            if (stream == null) {
                throw new IllegalStateException("Missing primer definition");
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        }

        assertTrue(AbilityEffectTypeRegistry.isRegistered(PrimerEffect.TYPE));
        assertTrue(
                AbilityEffectTypeRegistry.validateDefinition(definition).isEmpty(),
                () -> AbilityEffectTypeRegistry.validateDefinition(definition).toString()
        );
    }
}
