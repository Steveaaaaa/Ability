package com.steveaaaaa.ability.ability.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

class LootInjectionEffectTest {
    @Test
    void registersTheGenericLootInjectionType() {
        assertTrue(AbilityEffectTypeRegistry.isRegistered(LootInjectionEffect.TYPE));
    }

    @Test
    void selectsEntriesAtWeightBoundaries() {
        List<LootInjectionEffect.Entry> entries = List.of(
                entry("coal", 3),
                entry("diamond", 1)
        );

        assertEquals("coal", LootInjectionEffect.select(entries, 0).item().getPath());
        assertEquals("coal", LootInjectionEffect.select(entries, 2).item().getPath());
        assertEquals("diamond", LootInjectionEffect.select(entries, 3).item().getPath());
    }

    @Test
    void sparseRanksPreserveEarlierLootParameters() {
        LootInjectionEffect.RankValues first = new LootInjectionEffect.RankValues(Map.of(
                "chance", 0.25D,
                "rolls", 1.0D,
                "min_count", 1.0D,
                "max_count", 2.0D
        ));
        LootInjectionEffect.RankValues later = new LootInjectionEffect.RankValues(Map.of(
                "chance", 0.5D,
                "rolls", 2.0D
        ));

        LootInjectionEffect.ResolvedRank resolved = LootInjectionEffect.resolve(
                LootInjectionEffect.merge(first, later)
        );

        assertEquals(0.5D, resolved.chance());
        assertEquals(2, resolved.rolls());
        assertEquals(1, resolved.minCount());
        assertEquals(2, resolved.maxCount());
    }

    @Test
    void producesOneStackPerCertainRollForASingleEntry() {
        List<net.minecraft.world.item.ItemStack> result = LootInjectionEffect.roll(
                List.of(entry("emerald", 1)),
                new LootInjectionEffect.ResolvedRank(1.0D, 2, 3, 3),
                RandomSource.create(42L)
        );

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(stack -> stack.is(Items.EMERALD) && stack.getCount() == 3));
    }

    @Test
    void filtersRankGatedEntriesBeforeRolling() {
        List<LootInjectionEffect.Entry> entries = List.of(
                entry("copper_nugget", 1, 1),
                entry("iron_nugget", 1, 2),
                entry("gold_nugget", 1, 3)
        );

        assertEquals(1, LootInjectionEffect.eligibleEntries(entries, 1).size());
        assertEquals(2, LootInjectionEffect.eligibleEntries(entries, 2).size());
        assertEquals(3, LootInjectionEffect.eligibleEntries(entries, 3).size());
    }

    @Test
    void reportsSuccessfulRollCountForDropReplacement() {
        LootInjectionEffect.RollResult result = LootInjectionEffect.rollResult(
                List.of(entry("iron_nugget", 1)),
                new LootInjectionEffect.ResolvedRank(1.0D, 2, 1, 1),
                RandomSource.create(7L)
        );

        assertEquals(2, result.successfulRolls());
        assertEquals(2, result.drops().size());
    }

    @Test
    void validatesBlockLootConfigurationAndRanks() {
        AbilityDefinition definition = AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString("""
                {
                  "schema_version": 1,
                  "skill": "ability:gathering",
                  "display": {
                    "name": "ability.ability.test",
                    "description": "ability.ability.test.description",
                    "icon": "minecraft:stick",
                    "sort_order": 1
                  },
                  "purchase": { "skill_level": 1, "skill_points": 1, "requirements": [] },
                  "ranks": {
                    "unlock_skill_levels": [1, 2],
                    "values": [
                      { "chance": 0.25, "rolls": 1, "min_count": 1, "max_count": 2 },
                      { "chance": 0.5, "rolls": 2 }
                    ]
                  },
                  "effect": {
                    "type": "ability:loot_injection",
                    "config": {
                      "context": "block_drops",
                      "block_tags": ["minecraft:logs"],
                      "tool_tags": ["minecraft:axes"],
                      "entries": [
                        { "item": "minecraft:stick", "weight": 3 },
                        { "item": "minecraft:apple", "weight": 1 }
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

    private static LootInjectionEffect.Entry entry(String item, int weight) {
        return entry(item, weight, 1);
    }

    private static LootInjectionEffect.Entry entry(String item, int weight, int minimumRank) {
        return new LootInjectionEffect.Entry(
                ResourceLocation.fromNamespaceAndPath("minecraft", item),
                weight,
                minimumRank
        );
    }
}
