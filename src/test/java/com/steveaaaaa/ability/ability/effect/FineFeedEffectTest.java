package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import org.junit.jupiter.api.Test;

class FineFeedEffectTest {
    @Test
    void registersTheFineFeedEffectType() {
        assertTrue(AbilityEffectTypeRegistry.isRegistered(FineFeedEffect.TYPE));
    }

    @Test
    void acceptsEitherSpeedOrJumpAtItsInclusiveThreshold() {
        FineFeedEffect.ResolvedRank rank = new FineFeedEffect.ResolvedRank(0.25D, 0.75D);

        assertTrue(FineFeedEffect.qualifies(0.25D, 1.0D, rank));
        assertTrue(FineFeedEffect.qualifies(1.0D, 0.75D, rank));
        assertFalse(FineFeedEffect.qualifies(0.250001D, 0.750001D, rank));
        assertFalse(FineFeedEffect.qualifies(Double.NaN, 0.5D, rank));
    }

    @Test
    void validatesACompleteFineFeedDefinition() {
        AbilityDefinition definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {
                  "schema_version": 1,
                  "skill": "ability:husbandry",
                  "display": {
                    "name": "ability.ability.test",
                    "description": "ability.ability.test.description",
                    "icon": "minecraft:golden_carrot",
                    "sort_order": 1
                  },
                  "purchase": { "skill_level": 8, "skill_points": 4, "requirements": [] },
                  "ranks": {
                    "unlock_skill_levels": [8],
                    "skill_point_costs": [4],
                    "values": [{
                      "maximum_movement_speed": 0.25,
                      "maximum_jump_strength": 0.75
                    }]
                  },
                  "effect": {
                    "type": "ability:fine_feed",
                    "config": {
                      "mount_entity_type_tag": "ability:fine_feed_mounts",
                      "speed_effect": "minecraft:speed",
                      "jump_effect": "minecraft:jump_boost"
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
