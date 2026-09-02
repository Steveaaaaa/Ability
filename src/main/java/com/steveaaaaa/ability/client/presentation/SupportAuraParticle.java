package com.steveaaaaa.ability.client.presentation;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SupportAuraParticle extends TextureSheetParticle {
    private static SpriteSet healSprites;
    private static SpriteSet moteSprites;
    private final float initialSize;
    private final float initialAlpha;
    private final float spin;

    private SupportAuraParticle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed,
            float red, float green, float blue, SpriteSet sprites, boolean healing) {
        super(level, x, y, z);
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.hasPhysics = false;
        this.friction = healing ? 0.93F : 0.90F;
        this.gravity = healing ? -0.012F : 0.0F;
        this.lifetime = healing ? 18 + random.nextInt(8) : 11 + random.nextInt(5);
        this.initialSize = healing
                ? 0.050F + random.nextFloat() * 0.038F
                : 0.030F + random.nextFloat() * 0.025F;
        this.quadSize = initialSize;
        this.initialAlpha = 0.82F + random.nextFloat() * 0.18F;
        this.spin = (random.nextBoolean() ? 1.0F : -1.0F) * (healing ? 0.055F : 0.12F);
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        setColor(red, green, blue);
        setAlpha(initialAlpha);
        setSprite(sprites.get(random));
    }

    static void addHealing(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed) {
        if (healSprites == null) return;
        Minecraft.getInstance().particleEngine.add(new SupportAuraParticle(
                level, x, y, z, xSpeed, ySpeed, zSpeed,
                0.48F, 1.0F, 0.30F, healSprites, true
        ));
    }

    static void addMote(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, float red, float green, float blue) {
        if (moteSprites == null) return;
        Minecraft.getInstance().particleEngine.add(new SupportAuraParticle(
                level, x, y, z, xSpeed, ySpeed, zSpeed,
                red, green, blue, moteSprites, false
        ));
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        float progress = Mth.clamp((float) age / lifetime, 0.0F, 1.0F);
        roll += spin;
        quadSize = initialSize * (1.0F - progress * 0.42F);
        float fade = progress < 0.58F ? 1.0F : (1.0F - progress) / 0.42F;
        setAlpha(initialAlpha * Mth.clamp(fade, 0.0F, 1.0F));
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
        event.registerSpriteSet(ModParticles.SUPPORT_AURA_HEAL.get(), sprites -> {
            healSprites = sprites;
            return new Provider(sprites, true);
        });
        event.registerSpriteSet(ModParticles.SUPPORT_AURA_MOTE.get(), sprites -> {
            moteSprites = sprites;
            return new Provider(sprites, false);
        });
    }

    private record Provider(SpriteSet sprites, boolean healing) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                double x, double y, double z, double xSpeed, double ySpeed, double zSpeed) {
            return new SupportAuraParticle(level, x, y, z, xSpeed, ySpeed, zSpeed,
                    healing ? 0.48F : 0.90F,
                    healing ? 1.0F : 0.90F,
                    healing ? 0.30F : 0.90F,
                    sprites, healing);
        }
    }
}
