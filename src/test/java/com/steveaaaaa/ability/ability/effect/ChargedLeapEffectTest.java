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

class ChargedLeapEffectTest {
    private static final ChargedLeapEffect.Config CONFIG = new ChargedLeapEffect.Config(
            5.0D,
            20,
            0.42D,
            0.74D,
            100,
            6,
            0.42D
    );

    @Test
    void chargeInterpolatesAndCapsVerticalSpeed() {
        assertEquals(0.42D, ChargedLeapEffect.chargedVerticalSpeed(0L, CONFIG), 1.0E-9D);
        assertEquals(0.58D, ChargedLeapEffect.chargedVerticalSpeed(10L, CONFIG), 1.0E-9D);
        assertEquals(0.74D, ChargedLeapEffect.chargedVerticalSpeed(20L, CONFIG), 1.0E-9D);
        assertEquals(0.74D, ChargedLeapEffect.chargedVerticalSpeed(200L, CONFIG), 1.0E-9D);
    }

    @Test
    void resolvesSpreadsheetDamageAndStunValues() {
        ChargedLeapEffect.ResolvedRank rank = ChargedLeapEffect.resolve(
                new ChargedLeapEffect.RankValues(Map.of(
                        "damage_multiplier", 2.05D,
                        "stun_seconds", 2.6D
                ))
        );

        assertEquals(2.05D, rank.damageMultiplier());
        assertEquals(52, rank.stunTicks());
    }

    @Test
    void registersAndValidatesBuiltInDefinition() throws Exception {
        AbilityDefinition definition;
        try (var stream = ChargedLeapEffectTest.class.getResourceAsStream(
                "/data/ability/ability/abilities/charged_leap.json"
        )) {
            if (stream == null) {
                throw new IllegalStateException("Missing charged leap definition");
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        }

        assertTrue(AbilityEffectTypeRegistry.isRegistered(ChargedLeapEffect.TYPE));
        assertTrue(
                AbilityEffectTypeRegistry.validateDefinition(definition).isEmpty(),
                () -> AbilityEffectTypeRegistry.validateDefinition(definition).toString()
        );
    }
}
