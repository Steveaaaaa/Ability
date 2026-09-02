package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.systems.RenderSystem;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class StealthPresentation {
    private static final net.minecraft.resources.ResourceLocation ABILITY = AbilityMod.id("stealth");
    private static final net.minecraft.resources.ResourceLocation ENTER = AbilityMod.id("enter");
    private static final net.minecraft.resources.ResourceLocation ACTIVE = AbilityMod.id("active");
    private static final net.minecraft.resources.ResourceLocation BREAK_HIT = AbilityMod.id("break_hit");
    private static final Map<Integer, Long> ACTIVE_PLAYERS = new HashMap<>();
    private static ClientLevel activeLevel;

    private StealthPresentation() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(ABILITY)) return;
        if (activeLevel != level) clear(level);
        if (cue.cueId().equals(ACTIVE) && cue.action() == AbilityCue.Action.STOP) {
            ACTIVE_PLAYERS.remove(cue.targetEntityId());
            return;
        }
        if (cue.action() == AbilityCue.Action.STOP) return;
        if (cue.cueId().equals(ENTER)) {
            emitDissolve(level, cue);
        } else if (cue.cueId().equals(ACTIVE) && cue.action() == AbilityCue.Action.START) {
            ACTIVE_PLAYERS.put(cue.targetEntityId(),
                    level.getGameTime() + Math.max(1, cue.durationTicks()));
        } else if (cue.cueId().equals(BREAK_HIT)) {
            emitBreakHit(level, cue);
        }
    }

    private static void emitDissolve(ClientLevel level, AbilityCue cue) {
        Entity entity = level.getEntity(cue.targetEntityId());
        if (entity == null) return;
        RandomSource random = RandomSource.create(cue.randomSeed());
        int count = 22;
        for (int index = 0; index < count; index++) {
            double progress = (index + random.nextDouble()) / count;
            double angle = random.nextDouble() * Mth.TWO_PI;
            double radius = entity.getBbWidth() * (0.38D + random.nextDouble() * 0.18D);
            SupportAuraParticle.addMote(level,
                    entity.getX() + Mth.cos((float) angle) * radius,
                    entity.getY() + entity.getBbHeight() * progress,
                    entity.getZ() + Mth.sin((float) angle) * radius,
                    Mth.cos((float) angle) * 0.012D,
                    0.018D + progress * 0.018D,
                    Mth.sin((float) angle) * 0.012D,
                    0.24F, 0.10F, 0.34F);
        }
    }

    private static void emitBreakHit(ClientLevel level, AbilityCue cue) {
        Entity target = level.getEntity(cue.targetEntityId());
        Vec3 center = target == null ? cue.position() : target.getBoundingBox().getCenter();
        RandomSource random = RandomSource.create(cue.randomSeed());
        for (int index = 0; index < 18; index++) {
            double angle = Mth.TWO_PI * index / 18.0D + random.nextDouble() * 0.15D;
            double vertical = (random.nextDouble() - 0.5D) * 0.10D;
            SupportAuraParticle.addMote(level,
                    center.x + random.nextGaussian() * 0.06D,
                    center.y + random.nextGaussian() * 0.12D,
                    center.z + random.nextGaussian() * 0.06D,
                    Mth.cos((float) angle) * (0.055D + random.nextDouble() * 0.035D),
                    vertical,
                    Mth.sin((float) angle) * (0.055D + random.nextDouble() * 0.035D),
                    0.28F, 0.09F, 0.39F);
        }
        for (int index = 0; index < 5; index++) {
            level.addParticle(ParticleTypes.LARGE_SMOKE,
                    center.x + random.nextGaussian() * 0.16D,
                    center.y + random.nextGaussian() * 0.18D,
                    center.z + random.nextGaussian() * 0.16D,
                    random.nextGaussian() * 0.015D,
                    0.018D + random.nextDouble() * 0.025D,
                    random.nextGaussian() * 0.015D);
        }
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level != activeLevel) {
            clear(level);
            return;
        }
        long now = level.getGameTime();
        ACTIVE_PLAYERS.entrySet().removeIf(entry -> now >= entry.getValue());
    }

    @SubscribeEvent
    public static void renderHud(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.options.hideGui
                || minecraft.player.isSpectator()
                || ACTIVE_PLAYERS.getOrDefault(minecraft.player.getId(), Long.MIN_VALUE)
                        <= minecraft.level.getGameTime()) {
            return;
        }
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float pulse = 0.74F + Mth.sin((minecraft.level.getGameTime() + partialTick) * 0.16F) * 0.16F;
        renderEye(event.getGuiGraphics(), event.getGuiGraphics().guiWidth() / 2 + 14,
                event.getGuiGraphics().guiHeight() / 2 + 11, pulse);
    }

    private static void renderEye(GuiGraphics graphics, int x, int y, float pulse) {
        int alpha = Mth.clamp((int) (pulse * 255.0F), 0, 255);
        RenderSystem.enableBlend();
        graphics.fill(x + 3, y, x + 10, y + 1, alpha << 24 | 0x5D326F);
        graphics.fill(x + 1, y + 1, x + 12, y + 2, alpha << 24 | 0x382042);
        graphics.fill(x, y + 2, x + 13, y + 5, 0xB8160E1B);
        graphics.fill(x + 1, y + 5, x + 12, y + 6, alpha << 24 | 0x382042);
        graphics.fill(x + 3, y + 6, x + 10, y + 7, alpha << 24 | 0x5D326F);
        graphics.fill(x + 5, y + 2, x + 8, y + 5, alpha << 24 | 0xB76BE0);
        graphics.fill(x + 6, y + 2, x + 7, y + 5, 0xFFEEE6F4);
        RenderSystem.disableBlend();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear(null);
    }

    private static void clear(ClientLevel level) {
        ACTIVE_PLAYERS.clear();
        activeLevel = level;
    }
}
