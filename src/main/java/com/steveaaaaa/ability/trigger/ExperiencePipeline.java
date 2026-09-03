package com.steveaaaaa.ability.trigger;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.condition.ConditionEvaluation;
import com.steveaaaaa.ability.config.AbilityServerConfig;
import com.steveaaaaa.ability.condition.ConditionTypeRegistry;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.ExperienceSourceDefinition;
import com.steveaaaaa.ability.data.model.TypedConfig;
import com.steveaaaaa.ability.progress.ExperienceAntiAbuseService;
import com.steveaaaaa.ability.progress.ExperienceService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

public final class ExperiencePipeline {
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    private ExperiencePipeline() {
    }

    public static List<ExperienceService.AwardResult> process(ExperienceContext context) {
        Registry<ExperienceSourceDefinition> registry = context.player().registryAccess()
                .registryOrThrow(ModDataRegistries.EXPERIENCE_SOURCES);
        ArrayList<ExperienceService.AwardResult> awards = new ArrayList<>();

        for (Map.Entry<ResourceKey<ExperienceSourceDefinition>, ExperienceSourceDefinition> entry
                : registry.entrySet()) {
            ResourceLocation sourceId = entry.getKey().location();
            ExperienceSourceDefinition source = entry.getValue();
            TriggerMatch triggerMatch = TriggerTypeRegistry.match(context, source.trigger());
            if (triggerMatch.status() == TriggerMatch.Status.INVALID) {
                logInvalidOnce(sourceId, triggerMatch.detail());
                continue;
            }
            if (!triggerMatch.matched()) {
                continue;
            }
            if (source.antiAbuse().rejectPlayerPlaced()
                    && context instanceof ExperienceContext.BlockBreak blockBreak
                    && blockBreak.playerPlaced()) {
                continue;
            }
            if (!ExperienceAntiAbuseService.isTargetReady(
                    context.player(),
                    sourceId,
                    context.targetKey(),
                    source.antiAbuse().targetCooldownTicks()
            )) {
                continue;
            }

            ConditionEvaluation conditions = evaluateConditions(context, source.conditions());
            if (conditions.status() == ConditionEvaluation.Status.INVALID) {
                logInvalidOnce(sourceId, conditions.detail());
                continue;
            }
            if (!conditions.isSatisfied()) {
                continue;
            }

            long rawXp = calculateRawXp(
                    source.baseXp(),
                    triggerMatch.xpMultiplier() * AbilityServerConfig.experienceMultiplier()
            );
            long awardedXp = ExperienceAntiAbuseService.applyDailyLimit(
                    context.player(),
                    sourceId,
                    rawXp,
                    source.antiAbuse()
            );
            if (awardedXp <= 0L) {
                continue;
            }

            ExperienceAntiAbuseService.recordTarget(
                    context.player(),
                    sourceId,
                    context.targetKey(),
                    source.antiAbuse().targetCooldownTicks()
            );
            awards.add(ExperienceService.award(context.player(), source.skill(), awardedXp));
        }
        return List.copyOf(awards);
    }

    public static long calculateRawXp(int baseXp, double multiplier) {
        if (baseXp <= 0 || multiplier <= 0.0D || !Double.isFinite(multiplier)) {
            return 0L;
        }
        return Math.max(1L, Math.round(baseXp * multiplier));
    }

    private static ConditionEvaluation evaluateConditions(
            ExperienceContext context,
            List<TypedConfig> conditions
    ) {
        for (TypedConfig condition : conditions) {
            ConditionEvaluation result = ConditionTypeRegistry.evaluate(context.player(), condition);
            if (!result.isSatisfied()) {
                return result;
            }
        }
        return ConditionEvaluation.satisfied();
    }

    private static void logInvalidOnce(ResourceLocation sourceId, String detail) {
        String key = sourceId + "|" + detail;
        if (LOGGED_INVALID_DEFINITIONS.add(key)) {
            AbilityMod.LOGGER.error("Invalid experience source {}: {}", sourceId, detail);
        }
    }
}
