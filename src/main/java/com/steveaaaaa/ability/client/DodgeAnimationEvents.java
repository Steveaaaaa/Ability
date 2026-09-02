package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.client.presentation.DodgePresentation;
import com.steveaaaaa.ability.network.ClientDodgeAnimationQueue;
import com.steveaaaaa.ability.network.ClientboundDodgeAnimationPayload;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranimcore.animation.layered.modifier.AbstractFadeModifier;
import com.zigythebird.playeranimcore.animation.layered.modifier.AdjustmentModifier;
import com.zigythebird.playeranimcore.easing.EasingType;
import com.zigythebird.playeranimcore.math.Vec3f;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class DodgeAnimationEvents {
    public static final ResourceLocation LAYER = AbilityMod.id("dodge_animation_layer");
    private static final ResourceLocation ROLL = AbilityMod.id("dodge_roll");

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
            if (minecraft.level.getEntity(payload.playerEntityId()) instanceof AbstractClientPlayer player) {
                if (PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER)
                        instanceof PlayerAnimationController controller) {
                    play(controller, player, payload.motionX(), payload.motionZ());
                }
                DodgePresentation.start(player, payload.motionX(), payload.motionZ());
            }
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDodgeAnimationQueue.clear();
    }

    private static void play(
            PlayerAnimationController controller,
            AbstractClientPlayer player,
            float motionX,
            float motionZ
    ) {
        float yawOffset = relativeYawDegrees(movementYawDegrees(motionX, motionZ), player.yBodyRot);
        controller.removeModifierIf(DirectionModifier.class::isInstance);
        controller.addModifierLast(new DirectionModifier(yawOffset * Mth.DEG_TO_RAD));
        controller.replaceAnimationWithFade(
                AbstractFadeModifier.standardFadeIn(2, EasingType.EASE_IN_OUT_SINE),
                ROLL,
                true
        );
    }

    static float movementYawDegrees(float motionX, float motionZ) {
        return (float) Math.toDegrees(Math.atan2(-motionX, motionZ));
    }

    static float relativeYawDegrees(float movementYaw, float bodyYaw) {
        return Mth.wrapDegrees(movementYaw - bodyYaw);
    }

    private static final class DirectionModifier extends AdjustmentModifier {
        private DirectionModifier(float yawRadians) {
            super(bone -> "body".equals(bone)
                    ? Optional.of(new PartModifier(new Vec3f(0.0F, yawRadians, 0.0F), Vec3f.ZERO))
                    : Optional.empty());
        }
    }
}
