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
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class DodgeInputEvents {
    private static final ResourceLocation DODGE = AbilityMod.id("dodge");

    private DodgeInputEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null
                || !ClientProgressCache.snapshot().purchasedAbilities().contains(DODGE)) {
            return;
        }
        while (AbilityKeyMappings.DODGE.consumeClick()) {
            PacketDistributor.sendToServer(new ServerboundActivateAbilityPayload(DODGE, requestedDirection(minecraft)));
        }
    }

    static ActiveAbilityInput requestedDirection(Minecraft minecraft) {
        if (minecraft.options.keyUp.isDown()) {
            return ActiveAbilityInput.FORWARD;
        }
        if (minecraft.options.keyDown.isDown()) {
            return ActiveAbilityInput.BACKWARD;
        }
        if (minecraft.options.keyLeft.isDown()) {
            return ActiveAbilityInput.LEFT;
        }
        if (minecraft.options.keyRight.isDown()) {
            return ActiveAbilityInput.RIGHT;
        }
        return ActiveAbilityInput.BACKWARD;
    }
}
