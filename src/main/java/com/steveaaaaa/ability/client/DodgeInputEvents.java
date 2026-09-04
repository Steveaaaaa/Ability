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
    private static final int SHARED_SNEAK_TAP_TICKS = 6;
    private static boolean sharedKeyWasDown;
    private static boolean sharedKeyTapArmed;
    private static int sharedKeyHeldTicks;

    private DodgeInputEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null
                || !ClientProgressCache.snapshot().purchasedAbilities().contains(DODGE)) {
            resetSharedKeyState();
            return;
        }
        if (AbilityKeyMappings.DODGE.same(minecraft.options.keyShift)) {
            handleSharedSneakKey(minecraft);
            return;
        }
        resetSharedKeyState();
        while (AbilityKeyMappings.DODGE.consumeClick()) {
            requestDodge(minecraft);
        }
    }

    private static void handleSharedSneakKey(Minecraft minecraft) {
        while (AbilityKeyMappings.DODGE.consumeClick()) {
            // The physical press is handled by the tap/hold state machine below.
        }
        boolean down = AbilityKeyMappings.DODGE.isDown();
        if (down && !sharedKeyWasDown) {
            sharedKeyTapArmed = true;
            sharedKeyHeldTicks = 0;
        } else if (down && sharedKeyTapArmed) {
            if (++sharedKeyHeldTicks > SHARED_SNEAK_TAP_TICKS) {
                sharedKeyTapArmed = false;
            }
        } else if (!down && sharedKeyWasDown && sharedKeyTapArmed) {
            requestDodge(minecraft);
            sharedKeyTapArmed = false;
            sharedKeyHeldTicks = 0;
        }
        sharedKeyWasDown = down;
    }

    private static void requestDodge(Minecraft minecraft) {
        PacketDistributor.sendToServer(new ServerboundActivateAbilityPayload(DODGE, requestedDirection(minecraft)));
    }

    private static void resetSharedKeyState() {
        sharedKeyWasDown = false;
        sharedKeyTapArmed = false;
        sharedKeyHeldTicks = 0;
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
