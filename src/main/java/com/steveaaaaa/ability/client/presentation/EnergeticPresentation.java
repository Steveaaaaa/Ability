package com.steveaaaaa.ability.client.presentation;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.effect.EnergeticPresentationTracker;
import com.steveaaaaa.ability.presentation.AbilityCue;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class EnergeticPresentation {
    private static final DustParticleOptions GOLD = dust(0xC9912F, 0.72F);
    private static final DustParticleOptions BRIGHT_GOLD = dust(0xE0B84F, 0.82F);
    private static final DustParticleOptions FRESH_GREEN = dust(0x83A83E, 0.66F);
    private static final DustParticleOptions DIM_GOLD = dust(0x66512A, 0.62F);
    private static final DustParticleOptions DIM_GREEN = dust(0x3C4928, 0.58F);
    private static final Map<Integer, Long> ACTIVE = new HashMap<>();
    private static ClientLevel activeLevel;

    private EnergeticPresentation() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(EnergeticPresentationTracker.ABILITY_ID)
                || !cue.cueId().equals(EnergeticPresentationTracker.ACTIVE_CUE)) return;
        if (level != activeLevel) clear(level);

        if (cue.action() == AbilityCue.Action.STOP) {
            if (ACTIVE.remove(cue.targetEntityId()) != null) emitFade(level, cue.targetEntityId(), cue.randomSeed());
            return;
        }

        boolean newlyActive = !ACTIVE.containsKey(cue.targetEntityId());
        ACTIVE.put(cue.targetEntityId(), level.getGameTime() + Math.max(1, cue.durationTicks()));
        if (newlyActive) emitActivation(level, cue.targetEntityId(), cue.randomSeed());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level != activeLevel) {
            clear(level);
            return;
        }
        long gameTime = level.getGameTime();
        ACTIVE.entrySet().removeIf(entry -> gameTime >= entry.getValue());
        for (Integer entityId : ACTIVE.keySet()) {
            Entity entity = level.getEntity(entityId);
            if (!(entity instanceof Player player)) continue;
            if (player.isSprinting() && player.onGround()
                    && player.getDeltaMovement().horizontalDistanceSqr() > 0.0025D) {
                emitRunningTrail(level, player, gameTime);
            } else if (gameTime % 13L == Math.floorMod(entityId, 13)) {
                emitIdleSpark(level, player, gameTime);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        clear(null);
    }

    private static void emitActivation(ClientLevel level, int entityId, long seed) {
        Entity entity = level.getEntity(entityId);
        if (entity == null) return;
        RandomSource random = RandomSource.create(seed ^ level.getGameTime());
        double radius = Math.max(0.48D, entity.getBbWidth() * 0.72D);
        for (int index = 0; index < 24; index++) {
            double angle = Mth.TWO_PI * index / 24.0D;
            double jitter = (random.nextDouble() - 0.5D) * 0.06D;
            double dx = Math.cos(angle);
            double dz = Math.sin(angle);
            level.addParticle(index % 5 == 0 ? FRESH_GREEN : BRIGHT_GOLD,
                    entity.getX() + dx * (radius + jitter), entity.getY() + 0.06D, entity.getZ() + dz * (radius + jitter),
                    dx * 0.045D, 0.018D + random.nextDouble() * 0.018D, dz * 0.045D);
        }
    }

    private static void emitRunningTrail(ClientLevel level, Player player, long gameTime) {
        RandomSource random = RandomSource.create(((long) player.getId() << 32) ^ gameTime);
        Vec3 horizontal = player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize();
        Vec3 side = new Vec3(-horizontal.z, 0.0D, horizontal.x);
        for (int index = 0; index < 2; index++) {
            double sideOffset = (index == 0 ? -1.0D : 1.0D) * (0.13D + random.nextDouble() * 0.05D);
            Vec3 position = player.position().subtract(horizontal.scale(0.22D + random.nextDouble() * 0.15D))
                    .add(side.scale(sideOffset));
            level.addParticle(index == 0 ? GOLD : FRESH_GREEN,
                    position.x, position.y + 0.08D + random.nextDouble() * 0.08D, position.z,
                    -horizontal.x * 0.025D + random.nextGaussian() * 0.006D,
                    0.012D + random.nextDouble() * 0.018D,
                    -horizontal.z * 0.025D + random.nextGaussian() * 0.006D);
        }
    }

    private static void emitIdleSpark(ClientLevel level, Player player, long gameTime) {
        RandomSource random = RandomSource.create(((long) player.getId() << 32) ^ gameTime ^ 0x49444C45L);
        double angle = random.nextDouble() * Mth.TWO_PI;
        double radius = player.getBbWidth() * (0.3D + random.nextDouble() * 0.22D);
        level.addParticle(random.nextInt(4) == 0 ? FRESH_GREEN : GOLD,
                player.getX() + Math.cos(angle) * radius,
                player.getY() + 0.18D + random.nextDouble() * Math.max(0.2D, player.getBbHeight() * 0.55D),
                player.getZ() + Math.sin(angle) * radius,
                0.0D, 0.025D + random.nextDouble() * 0.018D, 0.0D);
    }

    private static void emitFade(ClientLevel level, int entityId, long seed) {
        Entity entity = level.getEntity(entityId);
        if (entity == null) return;
        RandomSource random = RandomSource.create(seed ^ level.getGameTime() ^ 0x46414445L);
        for (int index = 0; index < 16; index++) {
            double angle = random.nextDouble() * Mth.TWO_PI;
            double radius = entity.getBbWidth() * (0.2D + random.nextDouble() * 0.5D);
            level.addParticle(index % 4 == 0 ? DIM_GREEN : DIM_GOLD,
                    entity.getX() + Math.cos(angle) * radius,
                    entity.getY() + 0.15D + random.nextDouble() * Math.max(0.3D, entity.getBbHeight() * 0.75D),
                    entity.getZ() + Math.sin(angle) * radius,
                    Math.cos(angle) * 0.012D, -0.025D - random.nextDouble() * 0.022D, Math.sin(angle) * 0.012D);
        }
    }

    private static DustParticleOptions dust(int rgb, float scale) {
        return new DustParticleOptions(new Vector3f(
                ((rgb >> 16) & 0xFF) / 255.0F,
                ((rgb >> 8) & 0xFF) / 255.0F,
                (rgb & 0xFF) / 255.0F
        ), scale);
    }

    private static void clear(ClientLevel level) {
        ACTIVE.clear();
        activeLevel = level;
    }
}
