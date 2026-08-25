package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class ChargedLeapFovEffect {
    private static final float MAXIMUM_FOV_REDUCTION = 0.10F;
    private static float previousStrength;
    private static float strength;

    private ChargedLeapFovEffect() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        previousStrength = strength;
        if (ChargedLeapInputEvents.isCharging()) {
            strength = ChargedLeapInputEvents.chargeProgress(0.0F);
        } else {
            strength *= 0.45F;
            if (strength < 0.002F) {
                strength = 0.0F;
            }
        }
    }

    @SubscribeEvent
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || strength <= 0.0F && previousStrength <= 0.0F) {
            return;
        }
        float partialTick = (float) Math.clamp(event.getPartialTick(), 0.0D, 1.0D);
        float visualStrength = Mth.lerp(partialTick, previousStrength, strength);
        float eased = visualStrength * visualStrength * (3.0F - 2.0F * visualStrength);
        event.setFOV(event.getFOV() * (1.0D - MAXIMUM_FOV_REDUCTION * eased));
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        previousStrength = 0.0F;
        strength = 0.0F;
    }
}
