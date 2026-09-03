package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RetaliatoryFlameEffectTest {
    @Test
    void prioritizesLavaThenSoulFire() {
        assertEquals(
                RetaliatoryFlameEffect.Environment.LAVA,
                RetaliatoryFlameEffect.environmentPriority(true, true, true)
        );
        assertEquals(
                RetaliatoryFlameEffect.Environment.SOUL_FIRE,
                RetaliatoryFlameEffect.environmentPriority(true, true, false)
        );
        assertEquals(
                RetaliatoryFlameEffect.Environment.NORMAL_FIRE,
                RetaliatoryFlameEffect.environmentPriority(true, false, false)
        );
    }

    @Test
    void usesSpreadsheetEnvironmentDamage() {
        RetaliatoryFlameEffect.Config config = new RetaliatoryFlameEffect.Config(
                20,
                3.0D,
                2.0D,
                4.0D,
                com.steveaaaaa.ability.AbilityMod.id("normal"),
                com.steveaaaaa.ability.AbilityMod.id("soul"),
                com.steveaaaaa.ability.AbilityMod.id("lava")
        );

        assertEquals(0.0D, RetaliatoryFlameEffect.damageFor(
                RetaliatoryFlameEffect.Environment.NORMAL_FIRE,
                config
        ));
        assertEquals(2.0D, RetaliatoryFlameEffect.damageFor(
                RetaliatoryFlameEffect.Environment.SOUL_FIRE,
                config
        ));
        assertEquals(4.0D, RetaliatoryFlameEffect.damageFor(
                RetaliatoryFlameEffect.Environment.LAVA,
                config
        ));
    }

    @Test
    void registersAndValidatesBuiltInDefinition() throws Exception {
        AbilityDefinition definition;
        try (var stream = RetaliatoryFlameEffectTest.class.getResourceAsStream(
                "/data/fantasypower/fantasypower/abilities/retaliatory_flame.json"
        )) {
            if (stream == null) {
                throw new IllegalStateException("Missing retaliatory flame definition");
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        }

        assertTrue(AbilityEffectTypeRegistry.isRegistered(RetaliatoryFlameEffect.TYPE));
        assertTrue(
                AbilityEffectTypeRegistry.validateDefinition(definition).isEmpty(),
                () -> AbilityEffectTypeRegistry.validateDefinition(definition).toString()
        );
    }
}
