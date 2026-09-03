package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class AttributeModifierEffectTest {
    @Test
    void registersTheGenericAttributeModifierType() {
        assertTrue(AbilityEffectTypeRegistry.isRegistered(AttributeModifierEffect.TYPE));
    }

    @Test
    void laterSparseRanksOverrideOnlyTheirDeclaredAmounts() {
        AttributeModifierEffect.RankValues earlier = new AttributeModifierEffect.RankValues(Map.of(
                "break_speed", 0.1D,
                "movement_speed", 0.05D
        ));
        AttributeModifierEffect.RankValues later = new AttributeModifierEffect.RankValues(Map.of(
                "break_speed", 0.25D
        ));

        AttributeModifierEffect.RankValues merged = AttributeModifierEffect.merge(earlier, later);

        assertEquals(0.25D, merged.amounts().get("break_speed"));
        assertEquals(0.05D, merged.amounts().get("movement_speed"));
    }

    @Test
    void createsStableNamespacedModifierIds() {
        assertEquals(
                ResourceLocation.fromNamespaceAndPath("fantasypower", "attribute/example/efficient_mining/1"),
                AttributeModifierEffect.modifierId(
                        ResourceLocation.fromNamespaceAndPath("example", "efficient_mining"),
                        1
                )
        );
    }

    @Test
    void validatesACompleteAttributeDefinition() {
        AbilityDefinition definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {
                  "schema_version": 1,
                  "skill": "fantasypower:mining",
                  "display": {
                    "name": "ability.ability.test",
                    "description": "ability.ability.test.description",
                    "icon": "minecraft:iron_pickaxe",
                    "sort_order": 1
                  },
                  "purchase": { "skill_level": 1, "skill_points": 1, "requirements": [] },
                  "ranks": {
                    "unlock_skill_levels": [1, 2],
                    "skill_point_costs": [1, 1],
                    "values": [
                      { "break_speed": 0.1, "movement_speed": 0.05 },
                      { "break_speed": 0.2 }
                    ]
                  },
                  "effect": {
                    "type": "fantasypower:attribute_modifier",
                    "config": {
                      "melee_hit_cue": "fantasypower:hit",
                      "modifiers": [
                        {
                          "attribute": "minecraft:player.block_break_speed",
                          "operation": "add_multiplied_total",
                          "amount_key": "break_speed"
                        },
                        {
                          "attribute": "minecraft:generic.movement_speed",
                          "operation": "add_multiplied_total",
                          "amount_key": "movement_speed"
                        }
                      ]
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
