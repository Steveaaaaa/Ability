package com.steveaaaaa.ability.ability;

import com.steveaaaaa.ability.ability.effect.AttributeModifierEffect;
import com.steveaaaaa.ability.condition.ConditionEvaluation;
import com.steveaaaaa.ability.condition.ConditionTypeRegistry;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import com.steveaaaaa.ability.data.model.SkillDefinition;
import com.steveaaaaa.ability.data.model.TypedConfig;
import com.steveaaaaa.ability.progress.ModAttachments;
import com.steveaaaaa.ability.progress.PlayerProgress;
import com.steveaaaaa.ability.network.PlayerProgressSynchronizer;
import java.util.Optional;
import java.util.List;
import com.mojang.serialization.Dynamic;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class AbilityService {
    private AbilityService() {
    }

    public static Optional<AbilityDefinition> findAbility(ServerPlayer player, ResourceLocation abilityId) {
        Registry<AbilityDefinition> registry = player.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
        return registry.getOptional(abilityId);
    }

    public static PurchaseResult purchase(ServerPlayer player, ResourceLocation abilityId) {
        AbilityDefinition definition = findAbility(player, abilityId).orElse(null);
        if (definition == null) {
            return PurchaseResult.failure(PurchaseStatus.UNKNOWN_ABILITY, abilityId, "Unknown ability");
        }

        PlayerProgress progress = player.getData(ModAttachments.PLAYER_PROGRESS);
        if (progress.purchasedAbilities().contains(abilityId)) {
            return PurchaseResult.failure(PurchaseStatus.ALREADY_PURCHASED, abilityId, "Already purchased");
        }

        SkillDefinition skill = player.registryAccess().registryOrThrow(ModDataRegistries.SKILLS)
                .getOptional(definition.skill())
                .orElse(null);
        if (skill == null) {
            return PurchaseResult.failure(
                    PurchaseStatus.INVALID_DEFINITION,
                    abilityId,
                    "Unknown owning skill: " + definition.skill()
            );
        }

        int skillLevel = skill.levelForExperience(progress.skill(definition.skill()).totalXp());
        if (skillLevel < definition.purchase().skillLevel()) {
            return new PurchaseResult(
                    PurchaseStatus.SKILL_LEVEL_TOO_LOW,
                    abilityId,
                    definition.purchase().skillLevel(),
                    skillLevel,
                    "Requires owning skill level " + definition.purchase().skillLevel()
            );
        }

        int availableSkillPoints = progress.availableSkillPoints(definition.skill());
        if (availableSkillPoints < definition.purchase().skillPoints()) {
            return new PurchaseResult(
                    PurchaseStatus.NOT_ENOUGH_SKILL_POINTS,
                    abilityId,
                    definition.purchase().skillPoints(),
                    availableSkillPoints,
                    "Not enough skill points"
            );
        }

        ConditionEvaluation requirements = evaluateRequirements(player, definition);
        if (!requirements.isSatisfied()) {
            PurchaseStatus status = requirements.status() == ConditionEvaluation.Status.INVALID
                    ? PurchaseStatus.INVALID_DEFINITION
                    : PurchaseStatus.REQUIREMENT_NOT_MET;
            return PurchaseResult.failure(status, abilityId, requirements.detail());
        }

        PlayerProgress updated = progress.purchase(abilityId, definition.skill(), definition.purchase().skillPoints());
        player.setData(ModAttachments.PLAYER_PROGRESS, updated);
        AttributeModifierEffect.reconcile(player);
        PlayerProgressSynchronizer.send(player);
        return new PurchaseResult(
                PurchaseStatus.SUCCESS,
                abilityId,
                definition.purchase().skillPoints(),
                updated.availableSkillPoints(definition.skill()),
                ""
        );
    }

    public static RankView rank(ServerPlayer player, ResourceLocation abilityId) {
        AbilityDefinition definition = findAbility(player, abilityId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown ability: " + abilityId));
        PlayerProgress progress = player.getData(ModAttachments.PLAYER_PROGRESS);
        SkillDefinition skill = player.registryAccess().registryOrThrow(ModDataRegistries.SKILLS)
                .getOptional(definition.skill())
                .orElseThrow(() -> new IllegalArgumentException("Unknown owning skill: " + definition.skill()));
        int skillLevel = skill.levelForExperience(progress.skill(definition.skill()).totalXp());
        boolean purchased = progress.purchasedAbilities().contains(abilityId);
        ConditionEvaluation requirements = evaluateRequirements(player, definition);
        boolean active = purchased
                && skillLevel >= definition.purchase().skillLevel()
                && requirements.isSatisfied();
        int rank = active ? definition.ranks().rankForSkillLevel(skillLevel) : 0;
        return new RankView(
                abilityId,
                purchased,
                active && rank > 0,
                skillLevel,
                rank,
                definition.ranks().values().size(),
                requirements.detail()
        );
    }

    public static Optional<ActiveAbility> active(ServerPlayer player, ResourceLocation abilityId) {
        AbilityDefinition definition = findAbility(player, abilityId).orElse(null);
        if (definition == null) {
            return Optional.empty();
        }
        RankView rank = rank(player, abilityId);
        if (!rank.active()) {
            return Optional.empty();
        }
        return Optional.of(new ActiveAbility(
                abilityId,
                definition,
                rank.rank(),
                definition.ranks().values().subList(0, rank.rank())
        ));
    }

    private static ConditionEvaluation evaluateRequirements(ServerPlayer player, AbilityDefinition definition) {
        for (TypedConfig requirement : definition.purchase().requirements()) {
            ConditionEvaluation result = ConditionTypeRegistry.evaluate(player, requirement);
            if (!result.isSatisfied()) {
                return result;
            }
        }
        return ConditionEvaluation.satisfied();
    }

    public enum PurchaseStatus {
        SUCCESS,
        UNKNOWN_ABILITY,
        ALREADY_PURCHASED,
        SKILL_LEVEL_TOO_LOW,
        NOT_ENOUGH_SKILL_POINTS,
        REQUIREMENT_NOT_MET,
        INVALID_DEFINITION
    }

    public record PurchaseResult(
            PurchaseStatus status,
            ResourceLocation abilityId,
            int required,
            int actual,
            String detail
    ) {
        public static PurchaseResult failure(PurchaseStatus status, ResourceLocation abilityId, String detail) {
            return new PurchaseResult(status, abilityId, 0, 0, detail);
        }

        public boolean successful() {
            return status == PurchaseStatus.SUCCESS;
        }
    }

    public record RankView(
            ResourceLocation abilityId,
            boolean purchased,
            boolean active,
            int skillLevel,
            int rank,
            int maxRank,
            String detail
    ) {
    }

    public record ActiveAbility(
            ResourceLocation abilityId,
            AbilityDefinition definition,
            int rank,
            List<Dynamic<?>> unlockedRankValues
    ) {
        public ActiveAbility {
            unlockedRankValues = List.copyOf(unlockedRankValues);
        }
    }
}
