package com.steveaaaaa.ability.data;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import com.steveaaaaa.ability.data.model.ExperienceSourceDefinition;
import com.steveaaaaa.ability.data.model.SkillDefinition;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

public final class ModDataRegistries {
    public static final ResourceKey<Registry<SkillDefinition>> SKILLS =
            ResourceKey.createRegistryKey(AbilityMod.id("skills"));
    public static final ResourceKey<Registry<AbilityDefinition>> ABILITIES =
            ResourceKey.createRegistryKey(AbilityMod.id("abilities"));
    public static final ResourceKey<Registry<ExperienceSourceDefinition>> EXPERIENCE_SOURCES =
            ResourceKey.createRegistryKey(AbilityMod.id("experience_sources"));

    private ModDataRegistries() {
    }

    public static void registerDatapackRegistries(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(SKILLS, SkillDefinition.CODEC, SkillDefinition.CODEC);
        event.dataPackRegistry(ABILITIES, AbilityDefinition.CODEC, AbilityDefinition.CODEC);
        event.dataPackRegistry(
                EXPERIENCE_SOURCES,
                ExperienceSourceDefinition.CODEC,
                ExperienceSourceDefinition.CODEC
        );
    }
}
