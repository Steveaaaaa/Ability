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
public final class CrushingBlowPressureParticle extends TextureSheetParticle {
    private final float startingAlpha;

    private CrushingBlowPressureParticle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.friction = 0.86F;
        this.gravity = 0.035F;
        this.hasPhysics = false;
        this.lifetime = 11 + random.nextInt(8);
        this.quadSize = 0.13F + random.nextFloat() * 0.1F;
        this.startingAlpha = 0.76F + random.nextFloat() * 0.22F;
        this.rCol = 0.78F + random.nextFloat() * 0.18F;
        this.gCol = 0.8F + random.nextFloat() * 0.16F;
        this.bCol = 0.84F + random.nextFloat() * 0.14F;
        setAlpha(startingAlpha);
        setSprite(sprites.get(random));
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            float remaining = 1.0F - Math.clamp((float) age / lifetime, 0.0F, 1.0F);
            setAlpha(startingAlpha * remaining * remaining);
        }
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @SubscribeEvent
    public static void registerProvider(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.CRUSHING_BLOW_PRESSURE.get(), Provider::new);
    }

    private record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed) {
            return new CrushingBlowPressureParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
