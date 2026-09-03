package com.steveaaaaa.ability.registry;

import com.steveaaaaa.ability.AbilityMod;
import java.util.function.Supplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(
            BuiltInRegistries.PARTICLE_TYPE,
            AbilityMod.MOD_ID
    );
    public static final Supplier<SimpleParticleType> ASSOCIATED_ORE_SPARKLE = PARTICLE_TYPES.register(
            "associated_ore_sparkle",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> GRAVEL_PANNING_SPARK = PARTICLE_TYPES.register(
            "gravel_panning_spark",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> AMBUSH_BLOOD_SPLASH = PARTICLE_TYPES.register(
            "ambush_blood_splash",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> WEAK_POINT_BLOOD_DROP = PARTICLE_TYPES.register(
            "weak_point_blood_drop",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> COLD_CURRENT_SNOWFLAKE = PARTICLE_TYPES.register(
            "cold_current_snowflake",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> CRUSHING_BLOW_PRESSURE = PARTICLE_TYPES.register(
            "crushing_blow_pressure",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> CRUSHING_BLOW_FORGE_SPARK = PARTICLE_TYPES.register(
            "crushing_blow_forge_spark",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> SURVIVAL_CLEANSE_SHARD = PARTICLE_TYPES.register(
            "survival_cleanse_shard",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> HARVEST_CHAFF = PARTICLE_TYPES.register(
            "harvest_chaff",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> SNIFFER_TREASURE_GOLD = PARTICLE_TYPES.register(
            "sniffer_treasure_gold",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> SNIFFER_TREASURE_SOIL = PARTICLE_TYPES.register(
            "sniffer_treasure_soil",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> SNIFFER_TREASURE_GLINT = PARTICLE_TYPES.register(
            "sniffer_treasure_glint",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> LUCKY_CAT_PAW = PARTICLE_TYPES.register(
            "lucky_cat_paw",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> LUCKY_CAT_COIN = PARTICLE_TYPES.register(
            "lucky_cat_coin",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> LUCKY_CAT_KNOT = PARTICLE_TYPES.register(
            "lucky_cat_knot",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> SUPPORT_AURA_MOTE = PARTICLE_TYPES.register(
            "support_aura_mote",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> DANGEROUS_CHARGE_CORE = PARTICLE_TYPES.register(
            "dangerous_charge_core",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> DANGEROUS_CHARGE_SHOCKWAVE = PARTICLE_TYPES.register(
            "dangerous_charge_shockwave",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> DANGEROUS_CHARGE_SPARK = PARTICLE_TYPES.register(
            "dangerous_charge_spark",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> DANGEROUS_CHARGE_SMOKE = PARTICLE_TYPES.register(
            "dangerous_charge_smoke",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> PRIMER_EMBER = PARTICLE_TYPES.register(
            "primer_ember",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> PRIMER_IGNITION = PARTICLE_TYPES.register(
            "primer_ignition",
            () -> new SimpleParticleType(false)
    );
    public static final Supplier<SimpleParticleType> PRIMER_BURST = PARTICLE_TYPES.register(
            "primer_burst",
            () -> new SimpleParticleType(false)
    );

    private ModParticles() {
    }
}
