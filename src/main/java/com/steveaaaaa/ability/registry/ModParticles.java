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

    private ModParticles() {
    }
}
