package com.steveaaaaa.ability.client.presentation;

import com.steveaaaaa.ability.registry.ModParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Layered pixel explosion used instead of the vanilla firework burst. */
public final class DangerousChargeExplosionPresentation {
    private DangerousChargeExplosionPresentation() {
    }

    public static void spawn(ClientLevel level, Vec3 center, long seed) {
        RandomSource random = RandomSource.create(seed ^ level.getGameTime());
        level.addParticle(ModParticles.DANGEROUS_CHARGE_CORE.get(), true,
                center.x, center.y, center.z, 0.0D, 0.0D, 0.0D);
        level.addParticle(ModParticles.DANGEROUS_CHARGE_SHOCKWAVE.get(), true,
                center.x, center.y, center.z, 0.0D, 0.0D, 0.0D);

        for (int index = 0; index < 34; index++) {
            Vec3 direction = randomDirection(random);
            double speed = 0.22D + random.nextDouble() * 0.46D;
            Vec3 velocity = direction.scale(speed);
            level.addParticle(ModParticles.DANGEROUS_CHARGE_SPARK.get(), true,
                    center.x, center.y, center.z,
                    velocity.x, velocity.y, velocity.z);
        }

        for (int index = 0; index < 13; index++) {
            double angle = random.nextDouble() * Math.TAU;
            double radius = random.nextDouble() * 0.42D;
            double x = center.x + Math.cos(angle) * radius;
            double y = center.y - 0.08D + random.nextDouble() * 0.42D;
            double z = center.z + Math.sin(angle) * radius;
            level.addParticle(ModParticles.DANGEROUS_CHARGE_SMOKE.get(), true,
                    x, y, z,
                    Math.cos(angle) * (0.006D + random.nextDouble() * 0.018D),
                    0.045D + random.nextDouble() * 0.075D,
                    Math.sin(angle) * (0.006D + random.nextDouble() * 0.018D));
        }
    }

    private static Vec3 randomDirection(RandomSource random) {
        Vec3 direction;
        do {
            direction = new Vec3(
                    random.nextDouble() * 2.0D - 1.0D,
                    random.nextDouble() * 1.55D - 0.55D,
                    random.nextDouble() * 2.0D - 1.0D
            );
        } while (direction.lengthSqr() < 1.0E-5D || direction.lengthSqr() > 1.0D);
        return direction.normalize();
    }
}
