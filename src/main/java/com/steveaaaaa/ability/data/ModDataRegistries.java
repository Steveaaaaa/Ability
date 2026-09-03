package com.steveaaaaa.ability.data;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import com.steveaaaaa.ability.data.model.ExperienceSourceDefinition;
import com.steveaaaaa.ability.data.model.SkillDefinition;
import java.util.Set;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

public final class ModDataRegistries {
    private static final Set<ResourceLocation> BUILTIN_SKILLS = Set.of(
            AbilityMod.id("agility"),
            AbilityMod.id("archery"),
            AbilityMod.id("building"),
            AbilityMod.id("combat"),
            AbilityMod.id("defense"),
            AbilityMod.id("farming"),
            AbilityMod.id("gathering"),
            AbilityMod.id("husbandry"),
            AbilityMod.id("magic"),
            AbilityMod.id("mining")
    );
    private static final Set<ResourceLocation> BUILTIN_ABILITIES = Set.of(
            AbilityMod.id("ambush"),
            AbilityMod.id("associated_ore"),
            AbilityMod.id("blast_excavation"),
            AbilityMod.id("breakthrough"),
            AbilityMod.id("ceiling_wire"),
            AbilityMod.id("charged_leap"),
            AbilityMod.id("chorus_transmutation"),
            AbilityMod.id("cold_current"),
            AbilityMod.id("counter_sniper"),
            AbilityMod.id("crushing_blow"),
            AbilityMod.id("dangerous_charge"),
            AbilityMod.id("dodge"),
            AbilityMod.id("enchanted_edge"),
            AbilityMod.id("energetic"),
            AbilityMod.id("exhaustion"),
            AbilityMod.id("fine_feed"),
            AbilityMod.id("frugality"),
            AbilityMod.id("gravel_panning"),
            AbilityMod.id("greed"),
            AbilityMod.id("harvest"),
            AbilityMod.id("iron_cavalry"),
            AbilityMod.id("long_journey"),
            AbilityMod.id("lucky_cat"),
            AbilityMod.id("obsidian_reinforcement"),
            AbilityMod.id("primer"),
            AbilityMod.id("rapid_thrust"),
            AbilityMod.id("retaliatory_flame"),
            AbilityMod.id("sniffer_treasure"),
            AbilityMod.id("stealth"),
            AbilityMod.id("support_aura"),
            AbilityMod.id("survival_skills"),
            AbilityMod.id("survivor"),
            AbilityMod.id("weak_point"),
            AbilityMod.id("well_prepared"),
            AbilityMod.id("wolf_pack"),
            AbilityMod.id("world_traveler")
    );
    private static final Set<ResourceLocation> BUILTIN_EXPERIENCE_SOURCES = Set.of(
            AbilityMod.id("breed_animals"),
            AbilityMod.id("enchant_items"),
            AbilityMod.id("harvest_mature_crops"),
            AbilityMod.id("kill_hostile_mobs"),
            AbilityMod.id("mine_ores"),
            AbilityMod.id("place_building_blocks"),
            AbilityMod.id("ranged_kill_hostile_mobs"),
            AbilityMod.id("take_final_damage"),
            AbilityMod.id("travel_on_foot")
    );
    public static final ResourceKey<Registry<SkillDefinition>> SKILLS =
            ResourceKey.createRegistryKey(AbilityMod.id("skills"));
    public static final ResourceKey<Registry<AbilityDefinition>> ABILITIES =
            ResourceKey.createRegistryKey(AbilityMod.id("abilities"));
    public static final ResourceKey<Registry<ExperienceSourceDefinition>> EXPERIENCE_SOURCES =
            ResourceKey.createRegistryKey(AbilityMod.id("experience_sources"));

    private ModDataRegistries() {
    }

    public static boolean isBuiltinAbility(ResourceLocation abilityId) {
        return BUILTIN_ABILITIES.contains(abilityId);
    }

    public static Set<ResourceLocation> builtinSkillIds() {
        return BUILTIN_SKILLS;
    }

    public static Set<ResourceLocation> builtinAbilityIds() {
        return BUILTIN_ABILITIES;
    }

    public static Set<ResourceLocation> builtinExperienceSourceIds() {
        return BUILTIN_EXPERIENCE_SOURCES;
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
