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

class WellPreparedEffectTest {
    @Test
    void resolvesSpreadsheetAbsorptionAndInvulnerability() {
        WellPreparedEffect.ResolvedRank rank = WellPreparedEffect.resolve(new WellPreparedEffect.RankValues(Map.of(
                "absorption_health_percent", 25.0D,
                "invulnerability_seconds", 6.0D
        )));

        assertEquals(0.25D, rank.absorptionHealthFraction());
        assertEquals(120, rank.invulnerabilityTicks());
    }

    @Test
    void calculatesGameDayFromConfiguredLength() {
        assertEquals(0L, WellPreparedEffect.gameDay(23999L, 24000L));
        assertEquals(1L, WellPreparedEffect.gameDay(24000L, 24000L));
    }

    @Test
    void registersAndValidatesBuiltInDefinition() throws Exception {
        AbilityDefinition definition;
        try (var stream = WellPreparedEffectTest.class.getResourceAsStream(
                "/data/fantasypower/fantasypower/abilities/well_prepared.json"
        )) {
            if (stream == null) {
                throw new IllegalStateException("Missing well prepared definition");
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        }

        assertTrue(AbilityEffectTypeRegistry.isRegistered(WellPreparedEffect.TYPE));
        assertTrue(
                AbilityEffectTypeRegistry.validateDefinition(definition).isEmpty(),
                () -> AbilityEffectTypeRegistry.validateDefinition(definition).toString()
        );
    }
}
