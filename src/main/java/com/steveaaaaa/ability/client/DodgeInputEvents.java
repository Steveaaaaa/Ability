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
        int forward = (minecraft.options.keyUp.isDown() ? 1 : 0)
                - (minecraft.options.keyDown.isDown() ? 1 : 0);
        int right = (minecraft.options.keyRight.isDown() ? 1 : 0)
                - (minecraft.options.keyLeft.isDown() ? 1 : 0);
        return directionForAxes(forward, right);
    }

    static ActiveAbilityInput directionForAxes(int forward, int right) {
        if (forward > 0) {
            return right < 0 ? ActiveAbilityInput.FORWARD_LEFT
                    : right > 0 ? ActiveAbilityInput.FORWARD_RIGHT : ActiveAbilityInput.FORWARD;
        }
        if (forward < 0) {
            return right < 0 ? ActiveAbilityInput.BACKWARD_LEFT
                    : right > 0 ? ActiveAbilityInput.BACKWARD_RIGHT : ActiveAbilityInput.BACKWARD;
        }
        return right < 0 ? ActiveAbilityInput.LEFT
                : right > 0 ? ActiveAbilityInput.RIGHT : ActiveAbilityInput.BACKWARD;
    }
}
