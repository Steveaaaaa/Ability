package com.steveaaaaa.ability.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

public record PlayerProgress(
        int dataVersion,
        Map<ResourceLocation, SkillProgress> skills,
        Set<ResourceLocation> purchasedAbilities,
        int legacyUnassignedSkillPoints
) {
    public static final int CURRENT_DATA_VERSION = 2;
    public static final PlayerProgress EMPTY = new PlayerProgress(
            CURRENT_DATA_VERSION,
            Map.of(),
            Set.of(),
            0
    );

    private static final Codec<Set<ResourceLocation>> RESOURCE_LOCATION_SET_CODEC =
            ResourceLocation.CODEC.listOf().xmap(HashSet::new, List::copyOf);

    private static final Codec<SerializedProgress> SERIALIZED_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", 1).forGetter(SerializedProgress::dataVersion),
            Codec.unboundedMap(ResourceLocation.CODEC, SkillProgress.CODEC)
                    .optionalFieldOf("skills", Map.of())
                    .forGetter(SerializedProgress::skills),
            RESOURCE_LOCATION_SET_CODEC.optionalFieldOf("purchased_abilities", Set.of())
                    .forGetter(SerializedProgress::purchasedAbilities),
            Codec.INT.optionalFieldOf("legacy_unassigned_skill_points", 0)
                    .forGetter(SerializedProgress::legacyUnassignedSkillPoints),
            Codec.INT.optionalFieldOf("granted_skill_points", 0)
                    .forGetter(SerializedProgress::legacyGrantedSkillPoints),
            Codec.INT.optionalFieldOf("spent_skill_points", 0)
                    .forGetter(SerializedProgress::legacySpentSkillPoints)
    ).apply(instance, SerializedProgress::new));

    public static final Codec<PlayerProgress> CODEC = SERIALIZED_CODEC.xmap(
            SerializedProgress::toPlayerProgress,
            PlayerProgress::toSerializedProgress
    );

    public PlayerProgress {
        skills = Map.copyOf(skills);
        purchasedAbilities = Set.copyOf(purchasedAbilities);
        if (legacyUnassignedSkillPoints < 0) {
            throw new IllegalArgumentException("Legacy skill point balance must be non-negative");
        }
    }

    public SkillProgress skill(ResourceLocation skillId) {
        return skills.getOrDefault(skillId, SkillProgress.EMPTY);
    }

    public int availableSkillPoints(ResourceLocation skillId) {
        return Math.addExact(skill(skillId).availableSkillPoints(), legacyUnassignedSkillPoints);
    }

    public PlayerProgress withSkill(ResourceLocation skillId, SkillProgress progress, int newlyGrantedPoints) {
        if (newlyGrantedPoints < 0) {
            throw new IllegalArgumentException("Granted skill points must be non-negative");
        }
        HashMap<ResourceLocation, SkillProgress> updatedSkills = new HashMap<>(skills);
        updatedSkills.put(skillId, progress.add(0L, newlyGrantedPoints));
        return new PlayerProgress(
                CURRENT_DATA_VERSION,
                updatedSkills,
                purchasedAbilities,
                legacyUnassignedSkillPoints
        );
    }

    public PlayerProgress purchase(ResourceLocation abilityId, ResourceLocation skillId, int skillPointCost) {
        if (skillPointCost < 0) {
            throw new IllegalArgumentException("Skill point cost must be non-negative");
        }
        if (purchasedAbilities.contains(abilityId)) {
            throw new IllegalStateException("Ability is already purchased: " + abilityId);
        }
        if (skillPointCost > availableSkillPoints(skillId)) {
            throw new IllegalStateException("Not enough skill points to purchase: " + abilityId);
        }

        int spentFromLegacy = Math.min(skillPointCost, legacyUnassignedSkillPoints);
        int spentFromSkill = skillPointCost - spentFromLegacy;
        HashMap<ResourceLocation, SkillProgress> updatedSkills = new HashMap<>(skills);
        updatedSkills.put(skillId, skill(skillId).spend(spentFromSkill));
        HashSet<ResourceLocation> updatedAbilities = new HashSet<>(purchasedAbilities);
        updatedAbilities.add(abilityId);
        return new PlayerProgress(
                CURRENT_DATA_VERSION,
                updatedSkills,
                updatedAbilities,
                legacyUnassignedSkillPoints - spentFromLegacy
        );
    }

    private SerializedProgress toSerializedProgress() {
        return new SerializedProgress(
                CURRENT_DATA_VERSION,
                skills,
                purchasedAbilities,
                legacyUnassignedSkillPoints,
                0,
                0
        );
    }

    private record SerializedProgress(
            int dataVersion,
            Map<ResourceLocation, SkillProgress> skills,
            Set<ResourceLocation> purchasedAbilities,
            int legacyUnassignedSkillPoints,
            int legacyGrantedSkillPoints,
            int legacySpentSkillPoints
    ) {
        private PlayerProgress toPlayerProgress() {
            int legacyBalance = dataVersion <= 1
                    ? Math.max(0, legacyGrantedSkillPoints - legacySpentSkillPoints)
                    : Math.max(0, legacyUnassignedSkillPoints);
            return new PlayerProgress(
                    CURRENT_DATA_VERSION,
                    skills,
                    purchasedAbilities,
                    legacyBalance
            );
        }
    }
}
