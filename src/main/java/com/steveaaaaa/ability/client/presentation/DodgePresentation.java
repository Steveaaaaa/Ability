package com.steveaaaaa.ability.client.presentation;

import com.steveaaaaa.ability.AbilityMod;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class DodgePresentation {
    private static final int TRAIL_TICKS = 8;
    private static final Map<Integer, Trail> TRAILS = new HashMap<>();
    private static ClientLevel activeLevel;

    private DodgePresentation() {
    }

    public static void start(AbstractClientPlayer player, float motionX, float motionZ) {
        if (!(player.level() instanceof ClientLevel level)) return;
        if (activeLevel != level) clear(level);
        Vec3 direction = new Vec3(motionX, 0.0D, motionZ);
        if (direction.lengthSqr() < 1.0E-6D) return;
        direction = direction.normalize();
        long now = level.getGameTime();
        TRAILS.put(player.getId(), new Trail(direction, now, now + TRAIL_TICKS,
                player.getUUID().getLeastSignificantBits() ^ now));
        emitGroundDust(level, player, direction, RandomSource.create(player.getUUID().getMostSignificantBits() ^ now));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level != activeLevel) {
            clear(level);
            return;
        }
        long now = level.getGameTime();
        TRAILS.entrySet().removeIf(entry -> {
            Trail trail = entry.getValue();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof AbstractClientPlayer player) || now >= trail.expiresAt()) return true;
            emitSpeedPixels(level, player, trail, now);
            return false;
        });
    }

    private static void emitGroundDust(ClientLevel level, AbstractClientPlayer player,
            Vec3 direction, RandomSource random) {
        BlockPos floor = BlockPos.containing(player.getX(), player.getY() - 0.12D, player.getZ());
        BlockState state = level.getBlockState(floor);
        if (state.isAir()) {
            floor = floor.below();
            state = level.getBlockState(floor);
        }
        if (state.isAir()) return;

        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, state).setPos(floor);
        for (int index = 0; index < 8; index++) {
            double lateral = (random.nextDouble() - 0.5D) * 0.72D;
            double rear = 0.18D + random.nextDouble() * 0.28D;
            double speed = 0.025D + random.nextDouble() * 0.055D;
            level.addParticle(particle,
                    player.getX() - direction.x * rear + side.x * lateral,
                    player.getY() + 0.04D + random.nextDouble() * 0.08D,
                    player.getZ() - direction.z * rear + side.z * lateral,
                    -direction.x * speed + side.x * (random.nextDouble() - 0.5D) * 0.04D,
                    0.045D + random.nextDouble() * 0.075D,
                    -direction.z * speed + side.z * (random.nextDouble() - 0.5D) * 0.04D);
        }
    }

    private static void emitSpeedPixels(ClientLevel level, AbstractClientPlayer player, Trail trail, long now) {
        int age = (int) (now - trail.startedAt());
        RandomSource random = RandomSource.create(trail.seed() + age * 0x9E3779B97F4A7C15L);
        Vec3 direction = trail.direction();
        Vec3 side = new Vec3(-direction.z, 0.0D, direction.x);
        for (int segment = 0; segment < 3; segment++) {
            double rear = 0.28D + segment * 0.25D + random.nextDouble() * 0.08D;
            double lateral = (segment - 1) * 0.20D + (random.nextDouble() - 0.5D) * 0.08D;
            double y = player.getY() + 0.25D + segment * 0.27D + random.nextDouble() * 0.08D;
            double x = player.getX() - direction.x * rear + side.x * lateral;
            double z = player.getZ() - direction.z * rear + side.z * lateral;
            SupportAuraParticle.addMote(level, x, y, z,
                    -direction.x * 0.028D,
                    0.002D,
                    -direction.z * 0.028D,
                    0.78F, 0.82F, 0.86F);
            SupportAuraParticle.addMote(level,
                    x - direction.x * 0.09D, y, z - direction.z * 0.09D,
                    -direction.x * 0.022D,
                    0.002D,
                    -direction.z * 0.022D,
                    0.58F, 0.63F, 0.69F);
        }
    }

    private static void clear(ClientLevel level) {
        TRAILS.clear();
        activeLevel = level;
    }

    private record Trail(Vec3 direction, long startedAt, long expiresAt, long seed) {
    }
}
