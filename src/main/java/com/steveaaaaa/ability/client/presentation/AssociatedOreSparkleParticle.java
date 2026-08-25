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
public final class AssociatedOreSparkleParticle extends TextureSheetParticle {
    private final float initialAlpha;

    private AssociatedOreSparkleParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            SpriteSet sprites
    ) {
        super(level, x, y, z);
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.hasPhysics = false;
        this.lifetime = 24 + random.nextInt(13);
        this.quadSize = 0.045F + random.nextFloat() * 0.055F;
        float white = 0.92F + random.nextFloat() * 0.08F;
        setColor(white, white, white);
        this.initialAlpha = 0.8F + random.nextFloat() * 0.2F;
        setAlpha(initialAlpha);
        setSprite(sprites.get(random));
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        setAlpha(initialAlpha * alphaMultiplier(age, lifetime));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0x00F000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    static float alphaMultiplier(int age, int lifetime) {
        if (lifetime <= 0 || age >= lifetime) {
            return 0.0F;
        }
        float progress = Math.clamp((float) age / lifetime, 0.0F, 1.0F);
        float smooth = progress * progress * (3.0F - 2.0F * progress);
        return 1.0F - smooth;
    }

    @SubscribeEvent
    public static void registerProvider(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.ASSOCIATED_ORE_SPARKLE.get(), Provider::new);
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
            return new AssociatedOreSparkleParticle(level, x, y, z, sprites);
        }
    }
}
