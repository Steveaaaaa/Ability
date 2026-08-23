package com.steveaaaaa.ability.progress;

import com.steveaaaaa.ability.ability.effect.AttributeModifierEffect;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.SkillDefinition;
import com.steveaaaaa.ability.network.PlayerProgressSynchronizer;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class ExperienceService {
    private ExperienceService() {
    }

    public static Optional<SkillDefinition> findSkill(ServerPlayer player, ResourceLocation skillId) {
        Registry<SkillDefinition> registry = player.registryAccess().registryOrThrow(ModDataRegistries.SKILLS);
        return registry.getOptional(skillId);
    }

    public static AwardResult award(ServerPlayer player, ResourceLocation skillId, long amount) {
        if (amount < 0L) {
            throw new IllegalArgumentException("Experience amount must be non-negative");
        }

        SkillDefinition definition = findSkill(player, skillId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillId));
        PlayerProgress before = player.getData(ModAttachments.PLAYER_PROGRESS);
        SkillProgress oldProgress = before.skill(skillId);
        int oldLevel = definition.levelForExperience(oldProgress.totalXp());
        SkillProgress experienceUpdated = oldProgress.add(amount, 0);
        int newLevel = definition.levelForExperience(experienceUpdated.totalXp());
        int grantedPoints = Math.max(0, newLevel - oldLevel) * definition.skillPointsPerLevel();
        PlayerProgress after = before.withSkill(skillId, experienceUpdated, grantedPoints);
        player.setData(ModAttachments.PLAYER_PROGRESS, after);
        AttributeModifierEffect.reconcile(player);
        PlayerProgressSynchronizer.send(player);

        return new AwardResult(skillId, amount, oldLevel, newLevel, experienceUpdated.totalXp(), grantedPoints);
    }

    public static ProgressView view(ServerPlayer player, ResourceLocation skillId) {
        SkillDefinition definition = findSkill(player, skillId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown skill: " + skillId));
        SkillProgress progress = player.getData(ModAttachments.PLAYER_PROGRESS).skill(skillId);
        PlayerProgress playerProgress = player.getData(ModAttachments.PLAYER_PROGRESS);
        return new ProgressView(
                skillId,
                definition.levelForExperience(progress.totalXp()),
                progress.totalXp(),
                playerProgress.availableSkillPoints(skillId)
        );
    }

    public record AwardResult(
            ResourceLocation skillId,
            long amount,
            int oldLevel,
            int newLevel,
            long totalXp,
            int grantedSkillPoints
    ) {
    }

    public record ProgressView(ResourceLocation skillId, int level, long totalXp, int availableSkillPoints) {
    }
}
