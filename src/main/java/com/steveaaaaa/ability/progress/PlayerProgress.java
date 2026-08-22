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
        int grantedSkillPoints,
        int spentSkillPoints
) {
    public static final int CURRENT_DATA_VERSION = 1;
    public static final PlayerProgress EMPTY = new PlayerProgress(
            CURRENT_DATA_VERSION,
            Map.of(),
            Set.of(),
            0,
            0
    );

    private static final Codec<Set<ResourceLocation>> RESOURCE_LOCATION_SET_CODEC =
            ResourceLocation.CODEC.listOf().xmap(HashSet::new, List::copyOf);

    public static final Codec<PlayerProgress> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("data_version", CURRENT_DATA_VERSION).forGetter(PlayerProgress::dataVersion),
            Codec.unboundedMap(ResourceLocation.CODEC, SkillProgress.CODEC)
                    .optionalFieldOf("skills", Map.of())
                    .forGetter(PlayerProgress::skills),
            RESOURCE_LOCATION_SET_CODEC.optionalFieldOf("purchased_abilities", Set.of())
                    .forGetter(PlayerProgress::purchasedAbilities),
            Codec.INT.optionalFieldOf("granted_skill_points", 0).forGetter(PlayerProgress::grantedSkillPoints),
            Codec.INT.optionalFieldOf("spent_skill_points", 0).forGetter(PlayerProgress::spentSkillPoints)
    ).apply(instance, PlayerProgress::new));

    public PlayerProgress {
        skills = Map.copyOf(skills);
        purchasedAbilities = Set.copyOf(purchasedAbilities);
        if (grantedSkillPoints < 0 || spentSkillPoints < 0) {
            throw new IllegalArgumentException("Skill point counters must be non-negative");
        }
    }

    public SkillProgress skill(ResourceLocation skillId) {
        return skills.getOrDefault(skillId, SkillProgress.EMPTY);
    }

    public int availableSkillPoints() {
        return Math.max(0, grantedSkillPoints - spentSkillPoints);
    }

    public PlayerProgress withSkill(ResourceLocation skillId, SkillProgress progress, int newlyGrantedPoints) {
        HashMap<ResourceLocation, SkillProgress> updatedSkills = new HashMap<>(skills);
        updatedSkills.put(skillId, progress);
        return new PlayerProgress(
                CURRENT_DATA_VERSION,
                updatedSkills,
                purchasedAbilities,
                Math.addExact(grantedSkillPoints, newlyGrantedPoints),
                spentSkillPoints
        );
    }
}
