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
public final class GravelPanningSparkParticle extends TextureSheetParticle {
    static final double MIN_HORIZONTAL_SPEED = 0.04D;
    static final double MAX_HORIZONTAL_SPEED = 0.065D;
    static final double FRICTION = 0.98D;
    static final int MAX_LIFETIME = 40;
    private final float initialAlpha;

    private GravelPanningSparkParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double ySpeed,
            SpriteSet sprites
    ) {
        super(level, x, y, z);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        double horizontalSpeed = MIN_HORIZONTAL_SPEED
                + random.nextDouble() * (MAX_HORIZONTAL_SPEED - MIN_HORIZONTAL_SPEED);
        this.xd = Math.cos(angle) * horizontalSpeed;
        this.yd = ySpeed + 0.025D + random.nextDouble() * 0.035D;
        this.zd = Math.sin(angle) * horizontalSpeed;
        this.friction = (float) FRICTION;
        this.gravity = 0.035F;
        this.hasPhysics = true;
        this.lifetime = 32 + random.nextInt(MAX_LIFETIME - 31);
        this.quadSize = 0.055F + random.nextFloat() * 0.045F;
        setColor(1.0F, 0.64F + random.nextFloat() * 0.2F, 0.08F);
        this.initialAlpha = 0.85F + random.nextFloat() * 0.15F;
        setAlpha(initialAlpha);
        setSprite(sprites.get(random));
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            setAlpha(initialAlpha * AssociatedOreSparkleParticle.alphaMultiplier(age, lifetime));
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

    static double maximumHorizontalTravel() {
        return MAX_HORIZONTAL_SPEED * (1.0D - Math.pow(FRICTION, MAX_LIFETIME)) / (1.0D - FRICTION);
    }

    @SubscribeEvent
    public static void registerProvider(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.GRAVEL_PANNING_SPARK.get(), Provider::new);
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
            return new GravelPanningSparkParticle(level, x, y, z, ySpeed, sprites);
        }
    }
}
