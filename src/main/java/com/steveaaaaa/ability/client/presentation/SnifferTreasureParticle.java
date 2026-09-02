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
public final class SnifferTreasureParticle extends TextureSheetParticle {
    private final Style style;
    private final float initialSize;
    private final float initialAlpha;
    private final float spin;

    private SnifferTreasureParticle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites, Style style) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.style = style;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.hasPhysics = style == Style.SOIL;
        this.gravity = style == Style.SOIL ? 0.48F : 0.0F;
        this.friction = style == Style.SOIL ? 0.88F : 0.94F;
        this.lifetime = style.baseLifetime + random.nextInt(style.lifetimeVariance + 1);
        this.initialSize = style.minimumSize + random.nextFloat() * style.sizeVariance;
        this.quadSize = initialSize;
        this.initialAlpha = 0.82F + random.nextFloat() * 0.18F;
        this.spin = (random.nextBoolean() ? 1.0F : -1.0F) * style.spin;
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
        roll += spin;
        if (style == Style.GLINT) {
            quadSize = initialSize * (0.72F + 0.28F * (float) Math.sin(progress * Math.PI));
        } else {
            quadSize = initialSize * (1.0F - progress * 0.34F);
        }
        float fade = progress < 0.62F ? 1.0F : (1.0F - progress) / 0.38F;
        setAlpha(initialAlpha * Math.clamp(fade, 0.0F, 1.0F));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return style == Style.SOIL ? super.getLightColor(partialTick) : 0x00F000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @SubscribeEvent
    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SNIFFER_TREASURE_GOLD.get(),
                sprites -> new Provider(sprites, Style.GOLD));
        event.registerSpriteSet(ModParticles.SNIFFER_TREASURE_SOIL.get(),
                sprites -> new Provider(sprites, Style.SOIL));
        event.registerSpriteSet(ModParticles.SNIFFER_TREASURE_GLINT.get(),
                sprites -> new Provider(sprites, Style.GLINT));
    }

    private record Provider(SpriteSet sprites, Style style) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new SnifferTreasureParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, style);
        }
    }

    private enum Style {
        GOLD(17, 6, 0.032F, 0.021F, 0.045F),
        SOIL(13, 5, 0.045F, 0.030F, 0.16F),
        GLINT(22, 7, 0.038F, 0.024F, 0.025F);

        private final int baseLifetime;
        private final int lifetimeVariance;
        private final float minimumSize;
        private final float sizeVariance;
        private final float spin;

        Style(int baseLifetime, int lifetimeVariance, float minimumSize, float sizeVariance, float spin) {
            this.baseLifetime = baseLifetime;
            this.lifetimeVariance = lifetimeVariance;
            this.minimumSize = minimumSize;
            this.sizeVariance = sizeVariance;
            this.spin = spin;
        }
    }
}
