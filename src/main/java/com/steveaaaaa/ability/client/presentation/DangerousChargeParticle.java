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
public final class DangerousChargeParticle extends TextureSheetParticle {
    private final Style style;
    private final float baseSize;
    private final float baseAlpha;

    private DangerousChargeParticle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites, Style style) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.style = style;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.hasPhysics = style == Style.SPARK;
        this.friction = style == Style.SPARK ? 0.88F : 0.94F;
        this.gravity = style == Style.SPARK ? 0.065F : 0.0F;
        this.lifetime = switch (style) {
            case CORE -> 9;
            case SHOCKWAVE -> 13;
            case SPARK -> 13 + random.nextInt(8);
            case SMOKE -> 22 + random.nextInt(9);
        };
        this.baseSize = switch (style) {
            case CORE -> 0.78F;
            case SHOCKWAVE -> 0.46F;
            case SPARK -> 0.095F + random.nextFloat() * 0.055F;
            case SMOKE -> 0.32F + random.nextFloat() * 0.16F;
        };
        this.baseAlpha = switch (style) {
            case CORE -> 1.0F;
            case SHOCKWAVE -> 0.88F;
            case SPARK -> 0.9F + random.nextFloat() * 0.1F;
            case SMOKE -> 0.66F + random.nextFloat() * 0.16F;
        };
        if (style == Style.SPARK && random.nextBoolean()) {
            setColor(1.0F, 0.48F + random.nextFloat() * 0.18F, 0.08F);
        } else if (style == Style.SMOKE) {
            float shade = 0.72F + random.nextFloat() * 0.2F;
            setColor(shade, shade * 0.93F, shade * 0.98F);
        }
        setAlpha(baseAlpha);
        setSprite(sprites.get(random));
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) {
            return;
        }
        float progress = Math.clamp((float) age / lifetime, 0.0F, 1.0F);
        switch (style) {
            case CORE -> setAlpha(baseAlpha * (1.0F - progress * progress));
            case SHOCKWAVE -> setAlpha(baseAlpha * (1.0F - progress) * (1.0F - progress));
            case SPARK -> setAlpha(baseAlpha * (1.0F - progress));
            case SMOKE -> {
                setAlpha(baseAlpha * Math.min(progress * 4.0F, 1.0F) * (1.0F - progress));
                this.yd += 0.0012D;
            }
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float progress = Math.clamp(((float) age + partialTick) / lifetime, 0.0F, 1.0F);
        return switch (style) {
            case CORE -> baseSize * (0.72F + 0.72F * (float) Math.sin(progress * Math.PI));
            case SHOCKWAVE -> baseSize + 3.35F * easeOut(progress);
            case SPARK -> baseSize * (1.0F - progress * 0.45F);
            case SMOKE -> baseSize * (0.72F + progress * 1.85F);
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
        event.registerSpriteSet(ModParticles.DANGEROUS_CHARGE_CORE.get(),
                sprites -> new Provider(sprites, Style.CORE));
        event.registerSpriteSet(ModParticles.DANGEROUS_CHARGE_SHOCKWAVE.get(),
                sprites -> new Provider(sprites, Style.SHOCKWAVE));
        event.registerSpriteSet(ModParticles.DANGEROUS_CHARGE_SPARK.get(),
                sprites -> new Provider(sprites, Style.SPARK));
        event.registerSpriteSet(ModParticles.DANGEROUS_CHARGE_SMOKE.get(),
                sprites -> new Provider(sprites, Style.SMOKE));
    }

    private enum Style {
        CORE,
        SHOCKWAVE,
        SPARK,
        SMOKE
    }

    private record Provider(SpriteSet sprites, Style style) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed) {
            return new DangerousChargeParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, style);
        }
    }
}
