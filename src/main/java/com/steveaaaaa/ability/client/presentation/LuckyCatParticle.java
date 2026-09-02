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
public final class LuckyCatParticle extends TextureSheetParticle {
    private final Style style;
    private final float initialSize;
    private final float initialAlpha;

    private LuckyCatParticle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites, Style style) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.style = style;
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.hasPhysics = false;
        this.gravity = style == Style.COIN ? 0.14F : 0.0F;
        this.friction = style == Style.COIN ? 0.99F : 0.94F;
        this.lifetime = style.lifetime;
        this.initialSize = style.size;
        this.quadSize = initialSize;
        this.initialAlpha = style == Style.PAW ? 0.88F : 1.0F;
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
        if (style == Style.COIN) {
            roll += 0.34F;
        } else if (style == Style.KNOT) {
            roll += 0.025F;
            quadSize = initialSize * (0.72F + 0.28F * Math.min(1.0F, progress * 4.0F));
        }
        float appear = Math.min(1.0F, progress * 5.0F);
        float fade = progress < 0.60F ? 1.0F : (1.0F - progress) / 0.40F;
        setAlpha(initialAlpha * appear * Math.clamp(fade, 0.0F, 1.0F));
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
    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.LUCKY_CAT_PAW.get(),
                sprites -> new Provider(sprites, Style.PAW));
        event.registerSpriteSet(ModParticles.LUCKY_CAT_COIN.get(),
                sprites -> new Provider(sprites, Style.COIN));
        event.registerSpriteSet(ModParticles.LUCKY_CAT_KNOT.get(),
                sprites -> new Provider(sprites, Style.KNOT));
    }

    private record Provider(SpriteSet sprites, Style style) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new LuckyCatParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites, style);
        }
    }

    private enum Style {
        PAW(13, 0.072F),
        COIN(16, 0.090F),
        KNOT(18, 0.125F);

        private final int lifetime;
        private final float size;

        Style(int lifetime, float size) {
            this.lifetime = lifetime;
            this.size = size;
        }
    }
}
