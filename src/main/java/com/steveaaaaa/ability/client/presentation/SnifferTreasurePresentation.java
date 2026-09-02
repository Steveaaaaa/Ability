package com.steveaaaaa.ability.client.presentation;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
import com.steveaaaaa.ability.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

public final class SnifferTreasurePresentation {
    private static final net.minecraft.resources.ResourceLocation ABILITY = AbilityMod.id("sniffer_treasure");
    private static final net.minecraft.resources.ResourceLocation TREASURE_FOUND = AbilityMod.id("treasure_found");

    private SnifferTreasurePresentation() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(ABILITY) || !cue.cueId().equals(TREASURE_FOUND)
                || cue.action() == AbilityCue.Action.STOP) {
            return;
        }
        RandomSource random = RandomSource.create(cue.randomSeed());
        Vec3 origin = cue.position();
        emitGroundRing(level, origin, random);
        emitSoil(level, origin, random);
        emitAncientGlints(level, origin, random);
        emitDiscoveryFrame(level, origin, random);
    }

    private static void emitGroundRing(ClientLevel level, Vec3 origin, RandomSource random) {
        int count = 14;
        double phase = random.nextDouble() * Math.PI * 2.0D;
        for (int index = 0; index < count; index++) {
            double angle = phase + Math.PI * 2.0D * index / count;
            double radius = 0.46D + random.nextDouble() * 0.13D;
            double x = Math.cos(angle);
            double z = Math.sin(angle);
            level.addParticle(ModParticles.SNIFFER_TREASURE_GOLD.get(),
                    origin.x + x * radius, origin.y + 0.035D, origin.z + z * radius,
                    x * 0.012D, 0.012D + random.nextDouble() * 0.012D, z * 0.012D);
        }
    }

    private static void emitSoil(ClientLevel level, Vec3 origin, RandomSource random) {
        for (int index = 0; index < 9; index++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double speed = 0.035D + random.nextDouble() * 0.055D;
            level.addParticle(ModParticles.SNIFFER_TREASURE_SOIL.get(),
                    origin.x + (random.nextDouble() - 0.5D) * 0.34D,
                    origin.y + 0.05D + random.nextDouble() * 0.10D,
                    origin.z + (random.nextDouble() - 0.5D) * 0.34D,
                    Math.cos(angle) * speed,
                    0.075D + random.nextDouble() * 0.075D,
                    Math.sin(angle) * speed);
        }
    }

    private static void emitAncientGlints(ClientLevel level, Vec3 origin, RandomSource random) {
        for (int index = 0; index < 6; index++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = random.nextDouble() * 0.24D;
            level.addParticle(ModParticles.SNIFFER_TREASURE_GLINT.get(),
                    origin.x + Math.cos(angle) * radius,
                    origin.y + 0.12D + random.nextDouble() * 0.22D,
                    origin.z + Math.sin(angle) * radius,
                    (random.nextDouble() - 0.5D) * 0.012D,
                    0.025D + random.nextDouble() * 0.025D,
                    (random.nextDouble() - 0.5D) * 0.012D);
        }
    }

    private static void emitDiscoveryFrame(ClientLevel level, Vec3 origin, RandomSource random) {
        Vector3f cameraLeft = Minecraft.getInstance().gameRenderer.getMainCamera().getLeftVector();
        Vec3 left = new Vec3(cameraLeft.x(), cameraLeft.y(), cameraLeft.z());
        Vec3 up = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 center = origin.add(0.0D, 0.48D, 0.0D);
        int pointsPerEdge = 3;
        for (int edge = 0; edge < 4; edge++) {
            Vec3 start = diamondCorner(left, up, edge);
            Vec3 end = diamondCorner(left, up, (edge + 1) & 3);
            for (int point = 0; point < pointsPerEdge; point++) {
                double progress = point / (double) pointsPerEdge;
                Vec3 position = center.add(start.lerp(end, progress));
                level.addParticle(ModParticles.SNIFFER_TREASURE_GOLD.get(),
                        position.x, position.y, position.z,
                        (random.nextDouble() - 0.5D) * 0.002D,
                        0.004D + random.nextDouble() * 0.003D,
                        (random.nextDouble() - 0.5D) * 0.002D);
            }
        }
    }

    private static Vec3 diamondCorner(Vec3 left, Vec3 up, int corner) {
        return switch (corner) {
            case 0 -> up.scale(0.29D);
            case 1 -> left.scale(0.29D);
            case 2 -> up.scale(-0.29D);
            default -> left.scale(-0.29D);
        };
    }
}
