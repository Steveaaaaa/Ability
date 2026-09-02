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
public final class HarvestChaffParticle extends TextureSheetParticle {
    private final float initialSize;
    private final float initialAlpha;

    private HarvestChaffParticle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.friction = 0.91F;
        this.gravity = 0.24F;
        this.hasPhysics = false;
        this.lifetime = 10 + random.nextInt(8);
        this.initialSize = 0.065F + random.nextFloat() * 0.055F;
        this.quadSize = initialSize;
        this.initialAlpha = 0.82F + random.nextFloat() * 0.18F;
        this.roll = random.nextFloat() * ((float) Math.PI * 2.0F);
        this.oRoll = roll;
        setAlpha(initialAlpha);
        setSprite(sprites.get(random));
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        float progress = Math.clamp((float) age / lifetime, 0.0F, 1.0F);
        roll += 0.18F;
        quadSize = initialSize * (1.0F - progress * 0.42F);
        setAlpha(initialAlpha * (1.0F - progress));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @SubscribeEvent
    public static void registerProvider(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.HARVEST_CHAFF.get(), Provider::new);
    }

    private record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new HarvestChaffParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
