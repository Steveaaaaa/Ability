package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.SkillDefinition;
import com.steveaaaaa.ability.progress.ModAttachments;
import com.steveaaaaa.ability.progress.PlayerProgress;
import com.steveaaaaa.ability.progress.SkillProgress;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record PlayerProgressSnapshot(
        int schemaVersion,
        Map<ResourceLocation, SkillSnapshot> skills,
        Set<ResourceLocation> purchasedAbilities,
        int legacyUnassignedSkillPoints
) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    public static final PlayerProgressSnapshot EMPTY = new PlayerProgressSnapshot(
            CURRENT_SCHEMA_VERSION,
            Map.of(),
            Set.of(),
            0
    );

    public PlayerProgressSnapshot {
        skills = Map.copyOf(skills);
        purchasedAbilities = Set.copyOf(purchasedAbilities);
        if (schemaVersion < 0 || legacyUnassignedSkillPoints < 0) {
            throw new IllegalArgumentException("Snapshot counters must be non-negative");
        }
    }

    public static PlayerProgressSnapshot from(ServerPlayer player) {
        PlayerProgress progress = player.getData(ModAttachments.PLAYER_PROGRESS);
        Registry<SkillDefinition> definitions = player.registryAccess().registryOrThrow(ModDataRegistries.SKILLS);
        TreeMap<ResourceLocation, SkillSnapshot> skills = new TreeMap<>();
        definitions.entrySet().forEach(entry -> {
            ResourceLocation skillId = entry.getKey().location();
            SkillDefinition definition = entry.getValue();
            SkillProgress skillProgress = progress.skill(skillId);
            skills.put(skillId, new SkillSnapshot(
                    skillProgress.totalXp(),
                    definition.levelForExperience(skillProgress.totalXp()),
                    skillProgress.grantedSkillPoints(),
                    skillProgress.spentSkillPoints()
            ));
        });
        return new PlayerProgressSnapshot(
                CURRENT_SCHEMA_VERSION,
                skills,
                progress.purchasedAbilities(),
                progress.legacyUnassignedSkillPoints()
        );
    }

    public int availableSkillPoints(ResourceLocation skillId) {
        long available = (long) skills.getOrDefault(skillId, SkillSnapshot.EMPTY).availableSkillPoints()
                + legacyUnassignedSkillPoints;
        return (int) Math.min(Integer.MAX_VALUE, available);
    }

    public record SkillSnapshot(long totalXp, int level, int grantedSkillPoints, int spentSkillPoints) {
        public static final SkillSnapshot EMPTY = new SkillSnapshot(0L, 0, 0, 0);

        public SkillSnapshot {
            if (totalXp < 0L || level < 0 || grantedSkillPoints < 0 || spentSkillPoints < 0) {
                throw new IllegalArgumentException("Skill snapshot counters must be non-negative");
            }
        }

        public int availableSkillPoints() {
            return Math.max(0, grantedSkillPoints - spentSkillPoints);
        }
    }
}
