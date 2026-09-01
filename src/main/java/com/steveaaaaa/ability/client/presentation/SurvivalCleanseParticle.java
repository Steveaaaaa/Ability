package com.steveaaaaa.ability.client.presentation;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SurvivalCleanseParticle extends TextureSheetParticle {
    private static final net.minecraft.resources.ResourceLocation SURVIVAL_SKILLS =
            AbilityMod.id("survival_skills");
    private static final net.minecraft.resources.ResourceLocation PURIFICATION =
            AbilityMod.id("purification");
    private static final net.minecraft.resources.ResourceLocation PURIFICATION_LAYER =
            AbilityMod.id("purification_layer");
    private static SpriteSet particleSprites;

    private final float startRed;
    private final float startGreen;
    private final float startBlue;
    private final float initialSize;
    private final float initialAlpha;

    private SurvivalCleanseParticle(
            ClientLevel level,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            float red,
            float green,
            float blue,
            SpriteSet sprites
    ) {
        super(level, x, y, z);
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;
        this.friction = 0.93F;
        this.gravity = -0.008F;
        this.hasPhysics = false;
        this.lifetime = 19 + random.nextInt(10);
        this.initialSize = 0.055F + random.nextFloat() * 0.045F;
        this.quadSize = initialSize;
        this.startRed = Mth.clamp(red * 0.42F, 0.025F, 0.48F);
        this.startGreen = Mth.clamp(green * 0.42F, 0.025F, 0.48F);
        this.startBlue = Mth.clamp(blue * 0.42F, 0.025F, 0.48F);
        this.initialAlpha = 0.78F + random.nextFloat() * 0.18F;
        setColor(startRed, startGreen, startBlue);
        setAlpha(initialAlpha);
        setSprite(sprites.get(random));
    }

    @Override
    public void tick() {
        super.tick();
        if (removed) return;
        float progress = Mth.clamp((float) age / lifetime, 0.0F, 1.0F);
        float whitening = progress * progress * (3.0F - 2.0F * progress);
        setColor(
                Mth.lerp(whitening, startRed, 1.0F),
                Mth.lerp(whitening, startGreen, 1.0F),
                Mth.lerp(whitening, startBlue, 1.0F)
        );
        quadSize = initialSize * (1.0F - progress * 0.72F);
        setAlpha(initialAlpha * AssociatedOreSparkleParticle.alphaMultiplier(age, lifetime));
    }

    @Override
    protected int getLightColor(float partialTick) {
        float progress = Mth.clamp((age + partialTick) / lifetime, 0.0F, 1.0F);
        int light = Mth.clamp((int) Mth.lerp(progress, 160.0F, 240.0F), 0, 240);
        return light << 16 | light;
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(SURVIVAL_SKILLS)
                || (!cue.cueId().equals(PURIFICATION) && !cue.cueId().equals(PURIFICATION_LAYER))
                || cue.action() == AbilityCue.Action.STOP
                || particleSprites == null) {
            return;
        }
        Entity entity = level.getEntity(cue.targetEntityId());
        if (entity == null) return;

        Vec3 color = cue.direction();
        float red = (float) Mth.clamp(color.x, 0.0D, 1.0D);
        float green = (float) Mth.clamp(color.y, 0.0D, 1.0D);
        float blue = (float) Mth.clamp(color.z, 0.0D, 1.0D);
        RandomSource random = RandomSource.create(cue.randomSeed());
        int count = 18 + random.nextInt(7);
        double radius = Math.max(0.28D, entity.getBbWidth() * 0.56D);
        for (int index = 0; index < count; index++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double horizontal = radius * (0.88D + random.nextDouble() * 0.22D);
            double outwardX = Mth.cos((float) angle);
            double outwardZ = Mth.sin((float) angle);
            double x = entity.getX() + outwardX * horizontal;
            double y = entity.getY() + 0.12D + random.nextDouble() * Math.max(0.35D, entity.getBbHeight() - 0.18D);
            double z = entity.getZ() + outwardZ * horizontal;
            double speed = 0.018D + random.nextDouble() * 0.042D;
            Minecraft.getInstance().particleEngine.add(new SurvivalCleanseParticle(
                    level,
                    x,
                    y,
                    z,
                    outwardX * speed + random.nextGaussian() * 0.006D,
                    0.012D + random.nextDouble() * 0.035D,
                    outwardZ * speed + random.nextGaussian() * 0.006D,
                    red,
                    green,
                    blue,
                    particleSprites
            ));
        }
    }

    @SubscribeEvent
    public static void registerProvider(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SURVIVAL_CLEANSE_SHARD.get(), sprites -> {
            particleSprites = sprites;
            return new Provider(sprites);
        });
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
            return new SurvivalCleanseParticle(
                    level, x, y, z, xSpeed, ySpeed, zSpeed,
                    0.35F, 0.35F, 0.35F, sprites
            );
        }
    }
}
