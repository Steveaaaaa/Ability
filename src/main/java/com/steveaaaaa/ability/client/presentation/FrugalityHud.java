package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.systems.RenderSystem;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.effect.FrugalityEffect;
import com.steveaaaaa.ability.presentation.AbilityCue;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class FrugalityHud {
    private static final net.minecraft.resources.ResourceLocation NATURAL = AbilityMod.id("natural_saving");
    private static final net.minecraft.resources.ResourceLocation ABILITY = AbilityMod.id("ability_saving");
    private static final int DURATION_TICKS = 24;
    private static final List<SavingPulse> PULSES = new ArrayList<>();
    private static ClientLevel activeLevel;

    private FrugalityHud() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(FrugalityEffect.TYPE)
                || (!cue.cueId().equals(NATURAL) && !cue.cueId().equals(ABILITY))
                || cue.action() == AbilityCue.Action.STOP) return;
        if (activeLevel != level) clear(level);
        PULSES.add(new SavingPulse(
                level.getGameTime(),
                Math.max(0.0D, cue.direction().x),
                cue.cueId().equals(NATURAL),
                cue.randomSeed()
        ));
        if (PULSES.size() > 6) PULSES.removeFirst();
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level != activeLevel || minecraft.player == null || minecraft.options.hideGui
                || minecraft.player.isSpectator() || minecraft.player.getAbilities().invulnerable) return;

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double visualTime = level.getGameTime() + partialTick;
        PULSES.removeIf(pulse -> visualTime - pulse.startedAt() >= DURATION_TICKS);
        GuiGraphics graphics = event.getGuiGraphics();
        int hungerLeft = graphics.guiWidth() / 2 + 10;
        int hungerRight = graphics.guiWidth() / 2 + 91;
        int hungerY = graphics.guiHeight() - 39;
        RenderSystem.enableBlend();
        for (SavingPulse pulse : PULSES) renderPulse(graphics, pulse, visualTime, hungerLeft, hungerRight, hungerY);
        RenderSystem.disableBlend();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear(null);
    }

    private static void renderPulse(GuiGraphics graphics, SavingPulse pulse, double visualTime,
            int left, int right, int y) {
        float progress = Mth.clamp((float) ((visualTime - pulse.startedAt()) / DURATION_TICKS), 0.0F, 1.0F);
        float fadeIn = Mth.clamp(progress / 0.12F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((1.0F - progress) / 0.34F, 0.0F, 1.0F);
        float envelope = fadeIn * fadeOut;
        int alpha = Mth.clamp((int) (envelope * 235.0F), 0, 235);
        int accent = pulse.natural() ? 0x76A84B : 0xD18B36;
        int color = alpha << 24 | accent;

        int shadow = Mth.clamp((int) (envelope * 96.0F), 0, 96) << 24 | accent;
        graphics.fill(left - 3, y - 5, right + 3, y + 11, shadow);
        graphics.fill(left - 2, y - 4, right + 2, y - 2, color);
        graphics.fill(left - 2, y - 4, left, y + 10, color);
        graphics.fill(right, y - 4, right + 2, y + 10, color);
        graphics.fill(left - 2, y + 8, left + 4, y + 10, color);
        graphics.fill(right - 4, y + 8, right + 2, y + 10, color);

        int sweep = Mth.clamp((int) (progress * 13.0F) - 1, 0, 9);
        for (int index = 0; index < 10; index++) {
            int iconX = right - 9 - index * 8;
            int distance = Math.abs(index - sweep);
            int iconAlpha = Mth.clamp((int) (envelope * (distance == 0 ? 255 : distance == 1 ? 145 : 58)), 0, 255);
            int iconColor = iconAlpha << 24 | (pulse.natural() ? 0xA9C95B : 0xE4B14B);
            renderIconCorners(graphics, iconX, y, iconColor);
        }

        int count = Mth.clamp(4 + (int) Math.ceil(pulse.saved() * 4.0D), 4, 9);
        for (int index = 0; index < count; index++) {
            long mixed = pulse.seed() + index * 0x9E3779B97F4A7C15L;
            float startX = left + 3.0F + ((mixed >>> 8) & 63L);
            float startY = y - 15.0F - ((mixed >>> 16) & 5L);
            float targetX = right - (index % 10) * 8.0F - 5.0F;
            float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
            int x = Math.round(Mth.lerp(eased, startX, targetX));
            int moteY = Math.round(Mth.lerp(eased, startY, y + 3.0F) - Mth.sin(progress * Mth.PI) * 5.0F);
            int moteAlpha = Mth.clamp((int) (envelope * 255.0F), 0, 255);
            int moteColor = moteAlpha << 24 | (pulse.natural() ? 0xA9C95B : 0xE4B14B);
            renderGrain(graphics, x, moteY, moteColor);
        }
    }

    private static void renderIconCorners(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x, y - 1, x + 3, y, color);
        graphics.fill(x, y - 1, x + 1, y + 2, color);
        graphics.fill(x + 6, y - 1, x + 9, y, color);
        graphics.fill(x + 8, y - 1, x + 9, y + 2, color);
        graphics.fill(x, y + 8, x + 3, y + 9, color);
        graphics.fill(x, y + 6, x + 1, y + 9, color);
        graphics.fill(x + 6, y + 8, x + 9, y + 9, color);
        graphics.fill(x + 8, y + 6, x + 9, y + 9, color);
    }

    private static void renderGrain(GuiGraphics graphics, int x, int y, int color) {
        graphics.fill(x + 1, y, x + 2, y + 5, color);
        graphics.fill(x, y + 1, x + 1, y + 3, color);
        graphics.fill(x + 2, y + 2, x + 3, y + 4, color);
    }

    private static void clear(ClientLevel level) {
        PULSES.clear();
        activeLevel = level;
    }

    private record SavingPulse(long startedAt, double saved, boolean natural, long seed) {
    }
}
