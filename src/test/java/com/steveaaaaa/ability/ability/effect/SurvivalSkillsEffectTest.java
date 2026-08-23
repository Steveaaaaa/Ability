package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import org.junit.jupiter.api.Test;

class SurvivalSkillsEffectTest {
    @Test
    void removesOnlyHarmfulEffectsStrictlyBelowThreshold() {
        int threshold = SurvivalSkillsEffect.durationThresholdTicks(8);

        assertTrue(SurvivalSkillsEffect.shouldRemove(
                new MobEffectInstance(MobEffects.POISON, threshold - 1),
                threshold
        ));
        assertFalse(SurvivalSkillsEffect.shouldRemove(
                new MobEffectInstance(MobEffects.POISON, threshold),
                threshold
        ));
        assertFalse(SurvivalSkillsEffect.shouldRemove(
                new MobEffectInstance(MobEffects.MOVEMENT_SPEED, threshold - 1),
                threshold
        ));
    }

    @Test
    void registersAndValidatesBuiltInDefinition() throws Exception {
        AbilityDefinition definition;
        try (var stream = SurvivalSkillsEffectTest.class.getResourceAsStream(
                "/data/ability/ability/abilities/survival_skills.json"
        )) {
            if (stream == null) {
                throw new IllegalStateException("Missing survival skills definition");
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        }

        assertTrue(AbilityEffectTypeRegistry.isRegistered(SurvivalSkillsEffect.TYPE));
        assertTrue(
                AbilityEffectTypeRegistry.validateDefinition(definition).isEmpty(),
                () -> AbilityEffectTypeRegistry.validateDefinition(definition).toString()
        );
    }
}
