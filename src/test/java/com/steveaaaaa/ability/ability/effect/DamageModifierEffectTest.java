package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DamageModifierEffectTest {
    @Test
    void registersOutgoingAndIncomingDamageTypes() {
        assertTrue(AbilityEffectTypeRegistry.isRegistered(DamageModifierEffect.DAMAGE_MODIFIER));
        assertTrue(AbilityEffectTypeRegistry.isRegistered(DamageModifierEffect.DAMAGE_REDUCTION));
    }

    @Test
    void appliesOutgoingBeforeIncomingAndNeverReturnsNegativeDamage() {
        float outgoing = DamageModifierEffect.applyOutgoing(10.0F, 1.5D, 2.0D);
        float incoming = DamageModifierEffect.applyIncoming(outgoing, 0.5D, 1.0D);

        assertEquals(17.0F, outgoing);
        assertEquals(7.5F, incoming);
        assertEquals(0.0F, DamageModifierEffect.applyIncoming(2.0F, 0.5D, 5.0D));
    }

    @Test
    void sparseRanksPreserveEarlierDamageParameters() {
        DamageModifierEffect.RankValues first = new DamageModifierEffect.RankValues(Map.of(
                "damage_multiplier", 1.1D,
                "flat_damage", 1.0D
        ));
        DamageModifierEffect.RankValues second = new DamageModifierEffect.RankValues(Map.of(
                "damage_multiplier", 1.25D
        ));

        DamageModifierEffect.RankValues merged = DamageModifierEffect.merge(first, second);

        assertEquals(1.25D, merged.values().get("damage_multiplier"));
        assertEquals(1.0D, merged.values().get("flat_damage"));
    }

    @Test
    void calculatesTargetHealthRatioForThresholdFilters() {
        assertEquals(0.95D, DamageModifierEffect.healthRatio(19.0F, 20.0F), 0.000001D);
        assertEquals(0.0D, DamageModifierEffect.healthRatio(10.0F, 0.0F));
        assertEquals(1.0D, DamageModifierEffect.healthRatio(25.0F, 20.0F));
    }

    @Test
    void validatesDamageFiltersAndRankValues() {
        AbilityDefinition definition = parseDefinition("ability:damage_modifier", """
                { "damage_multiplier": 1.1, "flat_damage": 1.0 }
                """, """
                {
                  "damage_type_tags": ["minecraft:is_projectile"],
                  "target_entity_type_tags": ["minecraft:raiders"],
                  "directness": "indirect",
                  "target_state": "mob_without_target"
                }
                """);

        assertTrue(
                AbilityEffectTypeRegistry.validateDefinition(definition).isEmpty(),
                () -> AbilityEffectTypeRegistry.validateDefinition(definition).toString()
        );
    }

    @Test
    void rejectsParametersFromTheOppositeDamageDirection() {
        AbilityDefinition definition = parseDefinition(
                "ability:damage_reduction",
                "{ \"flat_damage\": 2.0 }",
                "{}"
        );

        assertTrue(AbilityEffectTypeRegistry.validateDefinition(definition).stream().anyMatch(error ->
                error.contains("unsupported damage parameter")
        ));
    }

    private static AbilityDefinition parseDefinition(String type, String rank, String config) {
        String json = """
                {
                  "schema_version": 1,
                  "skill": "ability:combat",
                  "display": {
                    "name": "ability.ability.test",
                    "description": "ability.ability.test.description",
                    "icon": "minecraft:iron_sword",
                    "sort_order": 1
                  },
                  "purchase": { "skill_level": 1, "skill_points": 1, "requirements": [] },
                  "ranks": { "unlock_skill_levels": [1], "skill_point_costs": [1], "values": [%s] },
                  "effect": { "type": "%s", "config": %s }
                }
                """.formatted(rank, type, config);
        return AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .result()
                .orElseThrow();
    }
}
