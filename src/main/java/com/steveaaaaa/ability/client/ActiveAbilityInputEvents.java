package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.ActiveAbilityInput;
import com.steveaaaaa.ability.network.ServerboundActivateAbilityPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class ActiveAbilityInputEvents {
    private static boolean previousLeftChord;
    private static boolean previousRightChord;

    private ActiveAbilityInputEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            resetChordState();
            return;
        }

        boolean sprintAndBack = minecraft.options.keySprint.isDown()
                && minecraft.options.keyDown.isDown();
        boolean leftChord = sprintAndBack
                && minecraft.options.keyLeft.isDown()
                && !minecraft.options.keyRight.isDown();
        boolean rightChord = sprintAndBack
                && minecraft.options.keyRight.isDown()
                && !minecraft.options.keyLeft.isDown();

        if (leftChord && !previousLeftChord) {
            send(ActiveAbilityInput.LEFT);
        } else if (rightChord && !previousRightChord) {
            send(ActiveAbilityInput.RIGHT);
        }
        previousLeftChord = leftChord;
        previousRightChord = rightChord;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        resetChordState();
    }

    private static void send(ActiveAbilityInput input) {
        PacketDistributor.sendToServer(new ServerboundActivateAbilityPayload(
                AbilityMod.id("dodge"),
                input
        ));
    }

    private static void resetChordState() {
        previousLeftChord = false;
        previousRightChord = false;
    }
}
