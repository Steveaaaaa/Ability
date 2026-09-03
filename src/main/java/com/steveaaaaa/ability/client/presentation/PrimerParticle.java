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
public final class PrimerParticle extends TextureSheetParticle {
    private final Style style;
    private final float baseSize;
    private final float baseAlpha;

    private PrimerParticle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites, Style style) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.style = style;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.hasPhysics = style == Style.EMBER;
        this.friction = style == Style.EMBER ? 0.9F : 0.96F;
        this.gravity = style == Style.EMBER ? 0.045F : 0.0F;
        this.lifetime = switch (style) {
            case EMBER -> 12 + random.nextInt(9);
            case IGNITION -> 9;
            case BURST -> 12;
        };
        this.baseSize = switch (style) {
            case EMBER -> 0.065F + random.nextFloat() * 0.045F;
            case IGNITION -> 0.24F;
            case BURST -> 0.48F;
        };
        this.baseAlpha = style == Style.EMBER ? 0.88F + random.nextFloat() * 0.12F : 0.96F;
        setAlpha(baseAlpha);
        setSprite(sprites.get(random));
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            float progress = Math.clamp((float) age / lifetime, 0.0F, 1.0F);
            setAlpha(baseAlpha * (1.0F - progress) * (style == Style.EMBER ? 1.0F : 1.0F - progress));
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float progress = Math.clamp(((float) age + partialTick) / lifetime, 0.0F, 1.0F);
        return switch (style) {
            case EMBER -> baseSize * (1.0F - progress * 0.4F);
            case IGNITION -> baseSize + easeOut(progress) * 0.68F;
            case BURST -> baseSize + easeOut(progress) * 1.28F;
        };
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    private static float easeOut(float value) {
        float inverse = 1.0F - value;
        return 1.0F - inverse * inverse * inverse;
    }

    @SubscribeEvent
    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.PRIMER_EMBER.get(), sprites -> new Provider(sprites, Style.EMBER));
        event.registerSpriteSet(ModParticles.PRIMER_IGNITION.get(), sprites -> new Provider(sprites, Style.IGNITION));
        event.registerSpriteSet(ModParticles.PRIMER_BURST.get(), sprites -> new Provider(sprites, Style.BURST));
    }

    private enum Style {
        EMBER,
        IGNITION,
        BURST
    }

    private record Provider(SpriteSet sprites, Style style) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed) {
            return new PrimerParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, style);
        }
    }
}
