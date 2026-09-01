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

    private ModParticles() {
    }
}
