package com.steveaaaaa.ability.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class ChargedLeapHud {
    private static final ResourceLocation BACKGROUND = ResourceLocation.withDefaultNamespace("hud/jump_bar_background");
    private static final ResourceLocation PROGRESS = ResourceLocation.withDefaultNamespace("hud/jump_bar_progress");

    private ChargedLeapHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ChargedLeapInputEvents.isCharging() || minecraft.options.hideGui) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int x = graphics.guiWidth() / 2 - 91;
        int y = graphics.guiHeight() - 29;
        int width = (int) (ChargedLeapInputEvents.chargeProgress(
                event.getPartialTick().getGameTimeDeltaPartialTick(false)
        ) * 183.0F);
        RenderSystem.enableBlend();
        graphics.blitSprite(BACKGROUND, x, y, 182, 5);
        if (width > 0) {
            graphics.blitSprite(PROGRESS, 182, 5, 0, 0, x, y, width, 5);
        }
        RenderSystem.disableBlend();
    }
}
