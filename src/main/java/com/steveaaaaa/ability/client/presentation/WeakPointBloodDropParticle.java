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
public final class WeakPointBloodDropParticle extends TextureSheetParticle {
    private final float initialAlpha;
    private boolean landed;

    private WeakPointBloodDropParticle(ClientLevel level, double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed, SpriteSet sprites) {
        super(level, x, y, z);
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.friction = 0.91F;
        this.gravity = 0.095F;
        this.hasPhysics = true;
        this.lifetime = 112 + random.nextInt(25);
        this.quadSize = 0.018F + random.nextFloat() * 0.031F;
        setColor(0.27F + random.nextFloat() * 0.19F,
                0.008F + random.nextFloat() * 0.018F,
                0.006F + random.nextFloat() * 0.014F);
        this.initialAlpha = 0.88F + random.nextFloat() * 0.12F;
        setAlpha(initialAlpha);
        setSprite(sprites.get(random));
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        if (onGround && !landed) {
            landed = true;
            xd = 0.0D;
            yd = 0.0D;
            zd = 0.0D;
            gravity = 0.0F;
            friction = 1.0F;
            y += 0.006D;
            yo = y;
        } else if (landed) {
            xd = 0.0D;
            yd = 0.0D;
            zd = 0.0D;
        }
        int remaining = lifetime - age;
        setAlpha(remaining >= 22 ? initialAlpha : initialAlpha * Math.max(0.0F, remaining / 22.0F));
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @SubscribeEvent
    public static void registerProvider(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.WEAK_POINT_BLOOD_DROP.get(), Provider::new);
    }

    private record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed) {
            return new WeakPointBloodDropParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, sprites);
        }
    }
}
