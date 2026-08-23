package com.steveaaaaa.ability.data.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import com.steveaaaaa.ability.data.model.ExperienceSourceDefinition;
import com.steveaaaaa.ability.data.model.SkillDefinition;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DataDefinitionValidatorTest {
    private static final ResourceLocation MINING = id("mining");

    @Test
    void acceptsAllBuiltInDefinitions() throws Exception {
        Map<ResourceLocation, SkillDefinition> skills = builtInSkills();
        Map<ResourceLocation, AbilityDefinition> abilities = builtInAbilities();
        Map<ResourceLocation, ExperienceSourceDefinition> sources = builtInSources();

        DataValidationReport report = DataDefinitionValidator.validateCatalog(
                skills,
                abilities,
                sources,
                advancement -> false
        );

        assertTrue(report.valid(), () -> report.diagnostics().toString());
        assertEquals(0, report.errorCount());
    }

    @Test
    void reportsUnknownSkillReferences() throws Exception {
        ExperienceSourceDefinition valid = load(
                ExperienceSourceDefinition.CODEC,
                "/data/ability/ability/experience_sources/mine_ores.json"
        );
        ExperienceSourceDefinition broken = new ExperienceSourceDefinition(
                valid.schemaVersion(),
                id("missing_skill"),
                valid.baseXp(),
                valid.trigger(),
                valid.conditions(),
                valid.antiAbuse()
        );

        DataValidationReport report = DataDefinitionValidator.validateCatalog(
                builtInSkills(),
                Map.of(),
                Map.of(id("broken_source"), broken),
                advancement -> false
        );

        assertTrue(report.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.path().equals("skill") && diagnostic.message().contains("missing_skill")
        ));
    }

    @Test
    void reportsCyclicAbilityPrerequisites() throws Exception {
        Map<ResourceLocation, AbilityDefinition> abilities = Map.of(
                id("cycle_a"), cyclicAbility(id("cycle_b")),
                id("cycle_b"), cyclicAbility(id("cycle_a"))
        );

        DataValidationReport report = DataDefinitionValidator.validateCatalog(
                builtInSkills(),
                abilities,
                Map.of(),
                advancement -> false
        );

        assertTrue(report.diagnostics().stream().anyMatch(diagnostic ->
                diagnostic.message().contains("Cyclic ability prerequisite")
        ));
    }

    private static Map<ResourceLocation, SkillDefinition> builtInSkills() throws Exception {
        HashMap<ResourceLocation, SkillDefinition> skills = new HashMap<>();
        for (String name : new String[]{
                "husbandry", "mining", "gathering", "combat", "defense",
                "building", "farming", "agility", "magic", "archery"
        }) {
            skills.put(id(name), load(
                    SkillDefinition.CODEC,
                    "/data/ability/ability/skills/" + name + ".json"
            ));
        }
        return skills;
    }

    private static Map<ResourceLocation, AbilityDefinition> builtInAbilities() throws Exception {
        HashMap<ResourceLocation, AbilityDefinition> abilities = new HashMap<>();
        for (String name : new String[]{
                "associated_ore", "gravel_panning", "rapid_thrust", "survivor"
        }) {
            abilities.put(id(name), load(
                    AbilityDefinition.CODEC,
                    "/data/ability/ability/abilities/" + name + ".json"
            ));
        }
        return abilities;
    }

    private static Map<ResourceLocation, ExperienceSourceDefinition> builtInSources() throws Exception {
        HashMap<ResourceLocation, ExperienceSourceDefinition> sources = new HashMap<>();
        for (String name : new String[]{
                "mine_ores", "kill_hostile_mobs", "harvest_mature_crops", "breed_animals",
                "place_building_blocks", "travel_on_foot", "ranged_kill_hostile_mobs",
                "take_final_damage", "enchant_items"
        }) {
            sources.put(id(name), load(
                    ExperienceSourceDefinition.CODEC,
                    "/data/ability/ability/experience_sources/" + name + ".json"
            ));
        }
        return sources;
    }

    private static AbilityDefinition cyclicAbility(ResourceLocation prerequisite) {
        String json = """
                {
                  "schema_version": 1,
                  "skill": "ability:mining",
                  "display": {
                    "name": "ability.ability.test",
                    "description": "ability.ability.test.description",
                    "icon": "minecraft:coal",
                    "sort_order": 1
                  },
                  "purchase": {
                    "skill_level": 1,
                    "skill_points": 0,
                    "requirements": [{
                      "type": "ability:ability_purchased",
                      "config": { "ability": "%s" }
                    }]
                  },
                  "ranks": {
                    "unlock_skill_levels": [1],
                    "values": [{ "coal_bonus_chance": 0.25 }]
                  },
                  "effect": {
                    "type": "ability:associated_ore",
                    "config": {
                      "required_tool_tag": "minecraft:pickaxes",
                      "require_unenchanted_tool": true,
                      "coal_ore_tag": "minecraft:coal_ores",
                      "copper_ore_tag": "minecraft:copper_ores",
                      "iron_ore_tag": "minecraft:iron_ores",
                      "nether_gold_ore": "minecraft:nether_gold_ore",
                      "coal_item": "minecraft:coal",
                      "raw_iron_item": "minecraft:raw_iron",
                      "raw_gold_item": "minecraft:raw_gold",
                      "diamond_item": "minecraft:diamond",
                      "emerald_item": "minecraft:emerald"
                    }
                  }
                }
                """.formatted(prerequisite);
        return AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .result()
                .orElseThrow();
    }

    private static <T> T load(Codec<T> codec, String path) throws Exception {
        try (var stream = DataDefinitionValidatorTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test resource " + path);
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return codec.parse(JsonOps.INSTANCE, json).result().orElseThrow();
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("ability", path);
    }
}
