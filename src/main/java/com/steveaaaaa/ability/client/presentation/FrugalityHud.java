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
import net.minecraft.resources.ResourceLocation;
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
    private static final ResourceLocation GRAIN_TEXTURE = AbilityMod.id("textures/gui/frugality_grain.png");
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
                Mth.clamp((int) Math.round(cue.direction().y), 0, 20),
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
        int hungerRight = graphics.guiWidth() / 2 + 91;
        int hungerY = graphics.guiHeight() - 39;
        RenderSystem.enableBlend();
        for (SavingPulse pulse : PULSES) renderPulse(graphics, pulse, visualTime, hungerRight, hungerY);
        RenderSystem.disableBlend();
    }

    @SubscribeEvent
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        clear(null);
    }

    private static void renderPulse(GuiGraphics graphics, SavingPulse pulse, double visualTime, int right, int y) {
        float progress = Mth.clamp((float) ((visualTime - pulse.startedAt()) / DURATION_TICKS), 0.0F, 1.0F);
        float fadeIn = Mth.clamp(progress / 0.12F, 0.0F, 1.0F);
        float fadeOut = Mth.clamp((1.0F - progress) / 0.34F, 0.0F, 1.0F);
        float envelope = fadeIn * fadeOut;
        int protectedPoints = Mth.clamp((int) Math.ceil(pulse.savedFoodPoints()), 1, 6);
        int count = Mth.clamp(3 + protectedPoints, 4, 9);
        for (int index = 0; index < count; index++) {
            long mixed = pulse.seed() + index * 0x9E3779B97F4A7C15L;
            int protectedOffset = index % protectedPoints;
            int foodPoint = Mth.clamp(pulse.foodLevel() - 1 - protectedOffset, 0, 19);
            int iconIndex = foodPoint / 2;
            int iconX = right - 9 - iconIndex * 8;
            float targetX = iconX + (foodPoint % 2 == 0 ? 3.0F : 6.0F);
            float startX = targetX - 14.0F + ((mixed >>> 8) & 31L);
            float startY = y - 18.0F - ((mixed >>> 16) & 7L);
            float eased = 1.0F - (1.0F - progress) * (1.0F - progress);
            int x = Math.round(Mth.lerp(eased, startX, targetX));
            int moteY = Math.round(Mth.lerp(eased, startY, y + 4.0F) - Mth.sin(progress * Mth.PI) * 5.0F);
            float scale = 0.72F + ((mixed >>> 24) & 3L) * 0.08F;
            renderGrain(graphics, x, moteY, envelope, scale);
        }
    }

    private static void renderGrain(GuiGraphics graphics, int x, int y, float alpha, float scale) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
        graphics.blit(GRAIN_TEXTURE, -8, -8, 0.0F, 0.0F, 16, 16, 16, 16);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.pose().popPose();
    }

    private static void clear(ClientLevel level) {
        PULSES.clear();
        activeLevel = level;
    }

    private record SavingPulse(long startedAt, double savedFoodPoints, int foodLevel, boolean natural, long seed) {
    }
}
