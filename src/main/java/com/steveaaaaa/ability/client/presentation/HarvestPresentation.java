package com.steveaaaaa.ability.client.presentation;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.effect.HarvestEffect;
import com.steveaaaaa.ability.presentation.AbilityCue;
import com.steveaaaaa.ability.registry.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

public final class HarvestPresentation {
    private static final ResourceLocation SWEEP = AbilityMod.id("sweep");

    private HarvestPresentation() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(HarvestEffect.TYPE) || !cue.cueId().equals(SWEEP)
                || cue.action() == AbilityCue.Action.STOP) return;
        Vec3 encodedDirection = cue.direction();
        float intensity = Mth.clamp((float) encodedDirection.length(), 0.4F, 1.0F);
        Vec3 forward = encodedDirection.lengthSqr() < 1.0E-8D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : encodedDirection.normalize();
        emitChaff(level, cue.position(), forward, intensity, cue.randomSeed());
    }

    private static void emitChaff(ClientLevel level, Vec3 position, Vec3 forward,
            float intensity, long seed) {
        RandomSource random = RandomSource.create(seed);
        Vec3 side = new Vec3(-forward.z, 0.0D, forward.x).normalize();
        int count = 4 + Math.round(intensity * 5.0F);
        for (int index = 0; index < count; index++) {
            Vec3 spawn = position.add(
                    side.scale((random.nextDouble() - 0.5D) * (0.65D + intensity * 0.45D))
            ).add(0.0D, (random.nextDouble() - 0.5D) * 0.75D, 0.0D);
            double sideSpeed = (random.nextDouble() - 0.5D) * (0.08D + intensity * 0.07D);
            Vec3 velocity = side.scale(sideSpeed).add(forward.scale(0.018D + random.nextDouble() * 0.035D));
            level.addParticle(ModParticles.HARVEST_CHAFF.get(),
                    spawn.x, spawn.y, spawn.z,
                    velocity.x, 0.035D + random.nextDouble() * 0.075D, velocity.z);
        }
    }

}
