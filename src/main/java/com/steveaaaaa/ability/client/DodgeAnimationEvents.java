package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.ActiveAbilityInput;
import com.steveaaaaa.ability.network.ClientDodgeAnimationQueue;
import com.steveaaaaa.ability.network.ClientboundDodgeAnimationPayload;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class DodgeAnimationEvents {
    public static final ResourceLocation LAYER = AbilityMod.id("dodge_animation_layer");

    private DodgeAnimationEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ClientDodgeAnimationQueue.clear();
            return;
        }
        ClientboundDodgeAnimationPayload payload;
        while ((payload = ClientDodgeAnimationQueue.poll()) != null) {
            if (minecraft.level.getEntity(payload.playerEntityId()) instanceof AbstractClientPlayer player
                    && PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER)
                    instanceof PlayerAnimationController controller) {
                controller.triggerAnimation(animation(payload.direction()));
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDodgeAnimationQueue.clear();
    }

    static ResourceLocation animation(ActiveAbilityInput direction) {
        return switch (direction) {
            case FORWARD -> AbilityMod.id("dodge_forward");
            case LEFT -> AbilityMod.id("dodge_left");
            case RIGHT -> AbilityMod.id("dodge_right");
            default -> AbilityMod.id("dodge_backward");
        };
    }
}
