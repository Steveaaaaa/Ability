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
public final class CrushingBlowForgeSparkParticle extends TextureSheetParticle {
    private final float startingAlpha;

    private CrushingBlowForgeSparkParticle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.xd = xSpeed * 1.35D;
        this.yd = Math.abs(ySpeed) * 1.15D + 0.055D;
        this.zd = zSpeed * 1.35D;
        this.friction = 0.91F;
        this.gravity = 0.22F;
        this.hasPhysics = false;
        this.lifetime = 7 + random.nextInt(7);
        this.quadSize = 0.065F + random.nextFloat() * 0.055F;
        this.startingAlpha = 0.9F + random.nextFloat() * 0.1F;
        this.roll = random.nextFloat() * ((float) Math.PI * 2.0F);
        this.oRoll = roll;
        setAlpha(startingAlpha);
        setSprite(sprites.get(random));
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            float remaining = 1.0F - Math.clamp((float) age / lifetime, 0.0F, 1.0F);
            setAlpha(startingAlpha * remaining);
            quadSize *= 0.94F;
        }
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0x00F000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @SubscribeEvent
    public static void registerProvider(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.CRUSHING_BLOW_FORGE_SPARK.get(), Provider::new);
    }

    private record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed) {
            return new CrushingBlowForgeSparkParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
