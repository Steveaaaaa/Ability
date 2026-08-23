package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.ActiveAbilityInput;
import com.steveaaaaa.ability.network.ClientProgressCache;
import com.steveaaaaa.ability.network.ServerboundActivateAbilityPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class ChargedLeapInputEvents {
    private static final ResourceLocation CHARGED_LEAP = AbilityMod.id("charged_leap");
    private static boolean previousKeyDown;
    private static boolean charging;

    private ChargedLeapInputEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
                || minecraft.screen != null
                || !ClientProgressCache.snapshot().purchasedAbilities().contains(CHARGED_LEAP)) {
            previousKeyDown = false;
            charging = false;
            return;
        }

        boolean keyDown = AbilityKeyMappings.CHARGED_LEAP.isDown();
        if (keyDown && !previousKeyDown) {
            if (minecraft.player.onGround()) {
                charging = true;
                send(ActiveAbilityInput.CHARGE_START);
            } else {
                send(ActiveAbilityInput.SECONDARY);
            }
        } else if (!keyDown && previousKeyDown && charging) {
            charging = false;
            send(ActiveAbilityInput.CHARGE_RELEASE);
        }
        previousKeyDown = keyDown;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        previousKeyDown = false;
        charging = false;
    }

    private static void send(ActiveAbilityInput input) {
        PacketDistributor.sendToServer(new ServerboundActivateAbilityPayload(CHARGED_LEAP, input));
    }
}
