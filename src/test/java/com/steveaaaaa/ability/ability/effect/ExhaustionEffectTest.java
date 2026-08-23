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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.junit.jupiter.api.Test;

class ExhaustionEffectTest {
    @Test
    void settlesPoisonWithVisibleLevelCappedByRank() {
        MobEffectInstance poison = new MobEffectInstance(MobEffects.POISON, 200, 2);

        assertEquals(32.0D, ExhaustionEffect.settledDamage(poison, 2, 0.8D, 72000));
    }

    @Test
    void clampsEffectDurationToConfiguredSafetyLimit() {
        MobEffectInstance wither = new MobEffectInstance(MobEffects.WITHER, 100000, 0);

        assertEquals(3600.0D, ExhaustionEffect.settledDamage(wither, 1, 0.5D, 72000));
    }

    @Test
    void rejectsMissingRankCaps() {
        assertThrows(
                IllegalArgumentException.class,
                () -> ExhaustionEffect.resolve(new ExhaustionEffect.RankValues(Map.of("poison_level_cap", 1)))
        );
    }

    @Test
    void registersAndValidatesBuiltInDefinition() throws Exception {
        AbilityDefinition definition;
        try (var stream = ExhaustionEffectTest.class.getResourceAsStream(
                "/data/ability/ability/abilities/exhaustion.json"
        )) {
            if (stream == null) {
                throw new IllegalStateException("Missing exhaustion definition");
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        }

        assertTrue(AbilityEffectTypeRegistry.isRegistered(ExhaustionEffect.TYPE));
        assertTrue(
                AbilityEffectTypeRegistry.validateDefinition(definition).isEmpty(),
                () -> AbilityEffectTypeRegistry.validateDefinition(definition).toString()
        );
    }
}
