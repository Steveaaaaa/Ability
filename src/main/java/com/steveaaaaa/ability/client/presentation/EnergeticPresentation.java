package com.steveaaaaa.ability.client.presentation;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.effect.EnergeticPresentationTracker;
import com.steveaaaaa.ability.presentation.AbilityCue;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import org.joml.Vector3f;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class EnergeticPresentation {
    private static final DustParticleOptions GOLD = dust(0xC9912F, 0.72F);
    private static final DustParticleOptions BRIGHT_GOLD = dust(0xE0B84F, 0.82F);
    private static final DustParticleOptions FRESH_GREEN = dust(0x83A83E, 0.66F);
    private static final DustParticleOptions DIM_GOLD = dust(0x66512A, 0.62F);
    private static final DustParticleOptions DIM_GREEN = dust(0x3C4928, 0.58F);
    private static final Map<Integer, EnergeticState> ACTIVE = new HashMap<>();
    private static ClientLevel activeLevel;

    private EnergeticPresentation() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(EnergeticPresentationTracker.ABILITY_ID)
                || !cue.cueId().equals(EnergeticPresentationTracker.ACTIVE_CUE)) return;
        if (level != activeLevel) clear(level);

        if (cue.action() == AbilityCue.Action.STOP) {
            EnergeticState previous = ACTIVE.remove(cue.targetEntityId());
            if (previous != null && previous.effectActive()) emitFade(level, cue.targetEntityId(), cue.randomSeed());
            return;
        }

        EnergeticState previous = ACTIVE.get(cue.targetEntityId());
        boolean effectActive = cue.direction().y > 0.5D;
        boolean newlyActive = effectActive && (previous == null || !previous.effectActive());
        boolean newlyInactive = !effectActive && previous != null && previous.effectActive();
        ACTIVE.put(cue.targetEntityId(), new EnergeticState(
                level.getGameTime() + Math.max(1, cue.durationTicks()),
                effectActive,
                Math.max(0.0D, cue.direction().x)
        ));
        if (newlyActive) emitActivation(level, cue.targetEntityId(), cue.randomSeed());
        if (newlyInactive) emitFade(level, cue.targetEntityId(), cue.randomSeed());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level != activeLevel) {
            clear(level);
            return;
        }
        long gameTime = level.getGameTime();
        ACTIVE.entrySet().removeIf(entry -> gameTime >= entry.getValue().expiresAt());
        for (Map.Entry<Integer, EnergeticState> entry : ACTIVE.entrySet()) {
            if (!entry.getValue().effectActive()) continue;
            Integer entityId = entry.getKey();
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
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()
                || player.getAbilities().invulnerable) return;
        EnergeticState state = ACTIVE.get(player.getId());
        if (state == null || state.threshold() <= 0.0D) return;

        GuiGraphics graphics = event.getGuiGraphics();
        int x = graphics.guiWidth() / 2 + 96;
        int y = graphics.guiHeight() - 44;
        renderThresholdBadge(graphics, minecraft, x, y, state.threshold(), state.effectActive(), player);
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

    private static void renderThresholdBadge(GuiGraphics graphics, Minecraft minecraft, int x, int y,
            double threshold, boolean active, Player player) {
        int width = 34;
        int height = 15;
        int border = active ? 0xFFE2B84E : 0xFF806A39;
        int glow = active ? 0x664F7C35 : 0x442D261A;
        graphics.fill(x + 1, y, x + width - 1, y + height, 0xD8141514);
        graphics.fill(x, y + 1, x + width, y + height - 1, 0xD8141514);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, glow);
        graphics.fill(x + 2, y, x + width - 2, y + 1, border);
        graphics.fill(x + 2, y + height - 1, x + width - 2, y + height, border);
        graphics.fill(x, y + 2, x + 1, y + height - 2, border);
        graphics.fill(x + width - 1, y + 2, x + width, y + height - 2, border);

        int iconX = x + 5;
        int iconY = y + 5;
        graphics.fill(iconX + 2, iconY - 2, iconX + 3, iconY + 5, 0xFFC69235);
        graphics.fill(iconX, iconY, iconX + 5, iconY + 1, 0xFFE0B84F);
        graphics.fill(iconX + 1, iconY - 1, iconX + 4, iconY + 2, 0xFFD6A43E);
        graphics.fill(iconX + 1, iconY + 3, iconX + 2, iconY + 4, 0xFF748D39);
        graphics.fill(iconX + 3, iconY + 2, iconX + 4, iconY + 3, 0xFF8AA445);
        graphics.fill(x + 12, y + 3, x + 13, y + 11, 0xFF55472B);

        String value = formatThreshold(threshold);
        graphics.drawString(minecraft.font, value, x + 16, y + 3,
                active ? 0xFFF2D477 : 0xFFB7A774, false);

        double current = player.getFoodData().getFoodLevel() + player.getFoodData().getSaturationLevel();
        int progress = Mth.clamp((int) Math.round((width - 4) * current / threshold), 0, width - 4);
        graphics.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, 0xFF332D22);
        if (progress > 0) {
            graphics.fill(x + 2, y + height - 2, x + 2 + progress, y + height - 1,
                    active ? 0xFF9EBC4A : 0xFFC18A32);
        }
    }

    private static String formatThreshold(double threshold) {
        double rounded = Math.rint(threshold);
        return Math.abs(threshold - rounded) < 0.001D
                ? Integer.toString((int) rounded)
                : String.format(java.util.Locale.ROOT, "%.1f", threshold);
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

    private record EnergeticState(long expiresAt, boolean effectActive, double threshold) {
    }
}
