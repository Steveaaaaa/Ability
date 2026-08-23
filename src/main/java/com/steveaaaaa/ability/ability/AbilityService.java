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
        int purchasedRank = progress.abilityRank(abilityId);
        if (purchasedRank >= definition.ranks().values().size()) {
            return PurchaseResult.failure(PurchaseStatus.MAX_RANK, abilityId, "Already at maximum rank");
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
        int nextRankIndex = purchasedRank;
        int requiredSkillLevel = Math.max(
                definition.purchase().skillLevel(),
                definition.ranks().unlockSkillLevels().get(nextRankIndex)
        );
        if (skillLevel < requiredSkillLevel) {
            return new PurchaseResult(
                    PurchaseStatus.SKILL_LEVEL_TOO_LOW,
                    abilityId,
                    requiredSkillLevel,
                    skillLevel,
                    "Requires owning skill level " + requiredSkillLevel
            );
        }

        int skillPointCost = definition.ranks().skillPointCosts().get(nextRankIndex);
        int availableSkillPoints = progress.availableSkillPoints(definition.skill(), skill.maximumSkillPoints());
        if (availableSkillPoints < skillPointCost) {
            return new PurchaseResult(
                    PurchaseStatus.NOT_ENOUGH_SKILL_POINTS,
                    abilityId,
                    skillPointCost,
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

        PlayerProgress updated = progress.purchaseRank(abilityId, definition.skill(), skillPointCost);
        player.setData(ModAttachments.PLAYER_PROGRESS, updated);
        AttributeModifierEffect.reconcile(player);
        PlayerProgressSynchronizer.send(player);
        return new PurchaseResult(
                PurchaseStatus.SUCCESS,
                abilityId,
                skillPointCost,
                updated.availableSkillPoints(definition.skill(), skill.maximumSkillPoints()),
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
        int purchasedRank = progress.abilityRank(abilityId);
        boolean purchased = purchasedRank > 0;
        ConditionEvaluation requirements = evaluateRequirements(player, definition);
        boolean active = purchased
                && skillLevel >= definition.purchase().skillLevel()
                && requirements.isSatisfied();
        int rank = active
                ? Math.min(purchasedRank, definition.ranks().rankForSkillLevel(skillLevel))
                : 0;
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
        MAX_RANK,
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
