package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConditionalMobEffectTest {
    @Test
    void registersTheConditionalMobEffectType() {
        assertTrue(AbilityEffectTypeRegistry.isRegistered(ConditionalMobEffect.TYPE));
    }

    @Test
    void evaluatesFoodAndAltitudeBoundariesStrictly() {
        assertFalse(ConditionalMobEffect.isFoodTotalAbove(20, 5.0F, 25.0D));
        assertTrue(ConditionalMobEffect.isFoodTotalAbove(20, 5.1F, 25.0D));
        assertFalse(ConditionalMobEffect.isOutsideRange(-20.0D, -20.0D, 200.0D));
        assertTrue(ConditionalMobEffect.isOutsideRange(-20.1D, -20.0D, 200.0D));
        assertTrue(ConditionalMobEffect.isOutsideRange(200.1D, -20.0D, 200.0D));
    }

    @Test
    void sparseRanksPreserveEarlierConditionValues() {
        ConditionalMobEffect.RankValues merged = ConditionalMobEffect.merge(
                new ConditionalMobEffect.RankValues(Map.of("lower_y", -50.0D, "upper_y", 280.0D)),
                new ConditionalMobEffect.RankValues(Map.of("upper_y", 250.0D))
        );

        assertEquals(-50.0D, merged.values().get("lower_y"));
        assertEquals(250.0D, merged.values().get("upper_y"));
    }

    @Test
    void validatesACompleteConditionalDefinition() {
        AbilityDefinition definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {
                  "schema_version": 1,
                  "skill": "fantasypower:agility",
                  "display": {
                    "name": "ability.ability.test",
                    "description": "ability.ability.test.description",
                    "icon": "minecraft:feather",
                    "sort_order": 1
                  },
                  "purchase": { "skill_level": 1, "skill_points": 1, "requirements": [] },
                  "ranks": {
                    "unlock_skill_levels": [1, 2],
                    "skill_point_costs": [1, 1],
                    "values": [
                      { "lower_y": -50, "upper_y": 280 },
                      { "lower_y": -40, "upper_y": 260 }
                    ]
                  },
                  "effect": {
                    "type": "fantasypower:conditional_mob_effect",
                    "config": {
                      "conditions": [{
                        "type": "y_outside_range",
                        "lower_bound_key": "lower_y",
                        "upper_bound_key": "upper_y"
                      }],
                      "effects": [{
                        "effect": "minecraft:speed",
                        "amplifier": 0,
                        "duration_ticks": 30
                      }]
                    }
                  }
                }
                """)).result().orElseThrow();

        assertTrue(
                AbilityEffectTypeRegistry.validateDefinition(definition).isEmpty(),
                () -> AbilityEffectTypeRegistry.validateDefinition(definition).toString()
        );
    }
}
