package com.steveaaaaa.ability.client.presentation;

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
    private static final int DURATION_TICKS = 12;
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
        for (SavingPulse pulse : PULSES) renderPulse(graphics, pulse, visualTime, hungerLeft, hungerRight, hungerY);
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear(null);
    }

    private static void renderPulse(GuiGraphics graphics, SavingPulse pulse, double visualTime,
            int left, int right, int y) {
        float progress = Mth.clamp((float) ((visualTime - pulse.startedAt()) / DURATION_TICKS), 0.0F, 1.0F);
        float envelope = Mth.sin(progress * Mth.PI);
        int alpha = Mth.clamp((int) (envelope * 210.0F), 0, 210);
        int accent = pulse.natural() ? 0x76A84B : 0xD18B36;
        int color = alpha << 24 | accent;

        graphics.fill(left - 2, y - 4, right + 2, y - 3, color);
        graphics.fill(left - 2, y - 4, left - 1, y + 10, color);
        graphics.fill(right + 1, y - 4, right + 2, y + 10, color);
        graphics.fill(left - 2, y + 9, left + 3, y + 10, color);
        graphics.fill(right - 3, y + 9, right + 2, y + 10, color);

        int count = Mth.clamp(2 + (int) Math.ceil(pulse.saved() * 3.0D), 2, 6);
        for (int index = 0; index < count; index++) {
            long mixed = pulse.seed() + index * 0x9E3779B97F4A7C15L;
            float startX = right + 7.0F + ((mixed >>> 8) & 7L);
            float startY = y - 12.0F - ((mixed >>> 16) & 5L);
            float targetX = right - index * 9.0F - 3.0F;
            float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
            int x = Math.round(Mth.lerp(eased, startX, targetX));
            int moteY = Math.round(Mth.lerp(eased, startY, y + 2.0F) - Mth.sin(progress * Mth.PI) * 4.0F);
            int moteAlpha = Mth.clamp((int) ((1.0F - progress * 0.72F) * 235.0F), 0, 235);
            int moteColor = moteAlpha << 24 | (pulse.natural() ? 0xA9C95B : 0xE4B14B);
            renderGrain(graphics, x, moteY, moteColor);
        }
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
