package com.steveaaaaa.ability.data.validation;

import com.steveaaaaa.ability.ability.effect.AbilityEffectTypeRegistry;
import com.steveaaaaa.ability.condition.ConditionTypeRegistry;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import com.steveaaaaa.ability.data.model.ExperienceSourceDefinition;
import com.steveaaaaa.ability.data.model.SkillDefinition;
import com.steveaaaaa.ability.data.model.TypedConfig;
import com.steveaaaaa.ability.trigger.TriggerTypeRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

public final class DataDefinitionValidator {
    private static final int MAX_CONDITION_DEPTH = 64;

    private DataDefinitionValidator() {
    }

    public static DataValidationReport validate(MinecraftServer server) {
        return validateCatalog(
                toMap(server.registryAccess().registryOrThrow(ModDataRegistries.SKILLS)),
                toMap(server.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES)),
                toMap(server.registryAccess().registryOrThrow(ModDataRegistries.EXPERIENCE_SOURCES)),
                id -> server.getAdvancements().get(id) != null
        );
    }

    public static DataValidationReport validateCatalog(
            Map<ResourceLocation, SkillDefinition> skills,
            Map<ResourceLocation, AbilityDefinition> abilities,
            Map<ResourceLocation, ExperienceSourceDefinition> experienceSources,
            Predicate<ResourceLocation> advancementExists
    ) {
        ArrayList<DataValidationReport.Diagnostic> diagnostics = new ArrayList<>();
        TreeMap<ResourceLocation, Set<ResourceLocation>> prerequisiteGraph = new TreeMap<>();

        abilities.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation abilityId = entry.getKey();
            AbilityDefinition ability = entry.getValue();
            prerequisiteGraph.put(abilityId, new LinkedHashSet<>());
            SkillDefinition owningSkill = skills.get(ability.skill());
            if (owningSkill == null) {
                add(diagnostics, "ability", abilityId, "skill", "Unknown skill " + ability.skill());
            } else {
                if (ability.purchase().skillLevel() > owningSkill.maxLevel()) {
                    add(
                            diagnostics,
                            "ability",
                            abilityId,
                            "purchase.skill_level",
                            "Level " + ability.purchase().skillLevel()
                                    + " exceeds owning skill maximum " + owningSkill.maxLevel()
                    );
                }
                for (int index = 0; index < ability.ranks().unlockSkillLevels().size(); index++) {
                    int unlockLevel = ability.ranks().unlockSkillLevels().get(index);
                    if (unlockLevel > owningSkill.maxLevel()) {
                        add(
                                diagnostics,
                                "ability",
                                abilityId,
                                "ranks.unlock_skill_levels[" + index + "]",
                                "Level " + unlockLevel + " exceeds owning skill maximum " + owningSkill.maxLevel()
                        );
                    }
                }
            }

            for (String error : AbilityEffectTypeRegistry.validateDefinition(ability)) {
                add(diagnostics, "ability", abilityId, "effect", error);
            }
            for (int index = 0; index < ability.purchase().requirements().size(); index++) {
                String path = "purchase.requirements[" + index + "]";
                TypedConfig condition = ability.purchase().requirements().get(index);
                validateCondition(
                        condition,
                        path,
                        "ability",
                        abilityId,
                        skills,
                        abilities,
                        advancementExists,
                        prerequisiteGraph.get(abilityId),
                        diagnostics,
                        0
                );
            }
        });

        experienceSources.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            ResourceLocation sourceId = entry.getKey();
            ExperienceSourceDefinition source = entry.getValue();
            if (!skills.containsKey(source.skill())) {
                add(
                        diagnostics,
                        "experience_source",
                        sourceId,
                        "skill",
                        "Unknown skill " + source.skill()
                );
            }
            TriggerTypeRegistry.validationError(source.trigger()).ifPresent(error ->
                    add(diagnostics, "experience_source", sourceId, "trigger", error)
            );
            for (int index = 0; index < source.conditions().size(); index++) {
                validateCondition(
                        source.conditions().get(index),
                        "conditions[" + index + "]",
                        "experience_source",
                        sourceId,
                        skills,
                        abilities,
                        advancementExists,
                        null,
                        diagnostics,
                        0
                );
            }
        });

        validatePrerequisiteCycles(prerequisiteGraph, diagnostics);
        return new DataValidationReport(diagnostics);
    }

    private static void validateCondition(
            TypedConfig condition,
            String path,
            String definitionType,
            ResourceLocation definitionId,
            Map<ResourceLocation, SkillDefinition> skills,
            Map<ResourceLocation, AbilityDefinition> abilities,
            Predicate<ResourceLocation> advancementExists,
            Set<ResourceLocation> prerequisiteEdges,
            List<DataValidationReport.Diagnostic> diagnostics,
            int depth
    ) {
        if (depth >= MAX_CONDITION_DEPTH) {
            add(diagnostics, definitionType, definitionId, path, "Condition nesting exceeds " + MAX_CONDITION_DEPTH);
            return;
        }
        List<String> configurationErrors = ConditionTypeRegistry.validateConfiguration(condition, path);
        configurationErrors.forEach(error -> add(diagnostics, definitionType, definitionId, path, error));
        if (!configurationErrors.isEmpty()) {
            return;
        }

        if (condition.type().equals(ConditionTypeRegistry.SKILL_LEVEL)) {
            ConditionTypeRegistry.SkillLevelConfig config = ConditionTypeRegistry.SkillLevelConfig.CODEC
                    .parse(condition.config()).result().orElseThrow();
            SkillDefinition skill = skills.get(config.skill());
            if (skill == null) {
                add(diagnostics, definitionType, definitionId, path + ".config.skill", "Unknown skill " + config.skill());
            } else if (config.level() > skill.maxLevel()) {
                add(
                        diagnostics,
                        definitionType,
                        definitionId,
                        path + ".config.level",
                        "Level " + config.level() + " exceeds skill maximum " + skill.maxLevel()
                );
            }
        } else if (condition.type().equals(ConditionTypeRegistry.ABILITY_PURCHASED)) {
            ConditionTypeRegistry.AbilityPurchasedConfig config = ConditionTypeRegistry.AbilityPurchasedConfig.CODEC
                    .parse(condition.config()).result().orElseThrow();
            if (!abilities.containsKey(config.ability())) {
                add(
                        diagnostics,
                        definitionType,
                        definitionId,
                        path + ".config.ability",
                        "Unknown ability " + config.ability()
                );
            } else if (prerequisiteEdges != null) {
                prerequisiteEdges.add(config.ability());
            }
        } else if (condition.type().equals(ConditionTypeRegistry.ADVANCEMENT)) {
            ConditionTypeRegistry.AdvancementConfig config = ConditionTypeRegistry.AdvancementConfig.CODEC
                    .parse(condition.config()).result().orElseThrow();
            if (!advancementExists.test(config.advancement())) {
                add(
                        diagnostics,
                        definitionType,
                        definitionId,
                        path + ".config.advancement",
                        "Unknown advancement " + config.advancement()
                );
            }
        } else if (condition.type().equals(ConditionTypeRegistry.ALL_OF)
                || condition.type().equals(ConditionTypeRegistry.ANY_OF)) {
            ConditionTypeRegistry.CompositeConfig config = ConditionTypeRegistry.CompositeConfig.CODEC
                    .parse(condition.config()).result().orElseThrow();
            for (int index = 0; index < config.conditions().size(); index++) {
                validateCondition(
                        config.conditions().get(index),
                        path + ".config.conditions[" + index + "]",
                        definitionType,
                        definitionId,
                        skills,
                        abilities,
                        advancementExists,
                        prerequisiteEdges,
                        diagnostics,
                        depth + 1
                );
            }
        } else if (condition.type().equals(ConditionTypeRegistry.NOT)) {
            ConditionTypeRegistry.NotConfig config = ConditionTypeRegistry.NotConfig.CODEC
                    .parse(condition.config()).result().orElseThrow();
            validateCondition(
                    config.condition(),
                    path + ".config.condition",
                    definitionType,
                    definitionId,
                    skills,
                    abilities,
                    advancementExists,
                    prerequisiteEdges,
                    diagnostics,
                    depth + 1
            );
        }
    }

    private static void validatePrerequisiteCycles(
            Map<ResourceLocation, Set<ResourceLocation>> graph,
            List<DataValidationReport.Diagnostic> diagnostics
    ) {
        HashMap<ResourceLocation, VisitState> states = new HashMap<>();
        ArrayList<ResourceLocation> path = new ArrayList<>();
        HashSet<String> reportedCycles = new HashSet<>();
        for (ResourceLocation abilityId : graph.keySet()) {
            if (!states.containsKey(abilityId)) {
                visit(abilityId, graph, states, path, reportedCycles, diagnostics);
            }
        }
    }

    private static void visit(
            ResourceLocation abilityId,
            Map<ResourceLocation, Set<ResourceLocation>> graph,
            Map<ResourceLocation, VisitState> states,
            List<ResourceLocation> path,
            Set<String> reportedCycles,
            List<DataValidationReport.Diagnostic> diagnostics
    ) {
        states.put(abilityId, VisitState.VISITING);
        path.add(abilityId);
        graph.getOrDefault(abilityId, Set.of()).stream().sorted().forEach(prerequisite -> {
            if (!graph.containsKey(prerequisite)) {
                return;
            }
            VisitState state = states.get(prerequisite);
            if (state == VisitState.VISITING) {
                int start = path.indexOf(prerequisite);
                ArrayList<ResourceLocation> cycle = new ArrayList<>(path.subList(start, path.size()));
                cycle.add(prerequisite);
                String message = "Cyclic ability prerequisite: " + String.join(
                        " -> ",
                        cycle.stream().map(ResourceLocation::toString).toList()
                );
                if (reportedCycles.add(message)) {
                    add(diagnostics, "ability", abilityId, "purchase.requirements", message);
                }
            } else if (state == null) {
                visit(prerequisite, graph, states, path, reportedCycles, diagnostics);
            }
        });
        path.removeLast();
        states.put(abilityId, VisitState.VISITED);
    }

    private static <T> Map<ResourceLocation, T> toMap(Registry<T> registry) {
        TreeMap<ResourceLocation, T> result = new TreeMap<>();
        registry.entrySet().forEach(entry -> result.put(entry.getKey().location(), entry.getValue()));
        return result;
    }

    private static void add(
            List<DataValidationReport.Diagnostic> diagnostics,
            String definitionType,
            ResourceLocation definitionId,
            String path,
            String message
    ) {
        diagnostics.add(new DataValidationReport.Diagnostic(definitionType, definitionId, path, message));
    }

    private enum VisitState {
        VISITING,
        VISITED
    }
}
