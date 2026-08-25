package com.steveaaaaa.ability.client.presentation;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.registry.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AmbushBloodSplashParticle extends TextureSheetParticle {
    private final float initialAlpha;

    private AmbushBloodSplashParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            SpriteSet sprites
    ) {
        super(level, x, y, z);
        double outwardScale = 0.45D + random.nextDouble() * 0.55D;
        this.xd = xSpeed * outwardScale + random.nextGaussian() * 0.055D;
        this.yd = ySpeed * outwardScale + 0.045D + random.nextDouble() * 0.09D;
        this.zd = zSpeed * outwardScale + random.nextGaussian() * 0.055D;
        this.friction = 0.88F;
        this.gravity = 0.065F;
        this.hasPhysics = true;
        this.lifetime = 18 + random.nextInt(11);
        this.quadSize = 0.075F + random.nextFloat() * 0.065F;
        setColor(
                0.32F + random.nextFloat() * 0.2F,
                0.015F + random.nextFloat() * 0.025F,
                0.01F + random.nextFloat() * 0.02F
        );
        this.initialAlpha = 0.86F + random.nextFloat() * 0.14F;
        setAlpha(initialAlpha);
        setSprite(sprites.get(random));
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            setAlpha(initialAlpha * AssociatedOreSparkleParticle.alphaMultiplier(age, lifetime));
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @SubscribeEvent
    public static void registerProvider(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.AMBUSH_BLOOD_SPLASH.get(), Provider::new);
    }

    private record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(
                SimpleParticleType type,
                ClientLevel level,
                double x,
                double y,
                double z,
                double xSpeed,
                double ySpeed,
                double zSpeed
        ) {
            return new AmbushBloodSplashParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
