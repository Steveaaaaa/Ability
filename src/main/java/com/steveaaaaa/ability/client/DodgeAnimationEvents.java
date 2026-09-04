package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.client.presentation.DodgePresentation;
import com.steveaaaaa.ability.network.ClientDodgeAnimationQueue;
import com.steveaaaaa.ability.network.ClientboundDodgeAnimationPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class DodgeAnimationEvents {
    private static final Map<UUID, RollAnimation> ACTIVE = new HashMap<>();

    private DodgeAnimationEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ClientDodgeAnimationQueue.clear();
            ACTIVE.clear();
            return;
        }
        ClientboundDodgeAnimationPayload payload;
        while ((payload = ClientDodgeAnimationQueue.poll()) != null) {
            if (minecraft.level.getEntity(payload.playerEntityId()) instanceof AbstractClientPlayer player) {
                ACTIVE.put(player.getUUID(), new RollAnimation(
                        minecraft.level.getGameTime(),
                        payload.durationTicks(),
                        movementYawDegrees(payload.motionX(), payload.motionZ())
                ));
                DodgePresentation.start(player, payload.motionX(), payload.motionZ());
            }
        }
        long gameTime = minecraft.level.getGameTime();
        ACTIVE.entrySet().removeIf(entry -> gameTime >= entry.getValue().startedAt()
                + entry.getValue().durationTicks());
        if (minecraft.player != null && isRolling(minecraft.player)) {
            suppressActionKeys(minecraft);
        }
    }

    @SubscribeEvent
    public static void lockMovement(MovementInputUpdateEvent event) {
        if (!isRolling(event.getEntity())) {
            return;
        }
        event.getInput().leftImpulse = 0.0F;
        event.getInput().forwardImpulse = 0.0F;
        event.getInput().up = false;
        event.getInput().down = false;
        event.getInput().left = false;
        event.getInput().right = false;
        event.getInput().jumping = false;
        event.getInput().shiftKeyDown = false;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientDodgeAnimationQueue.clear();
        ACTIVE.clear();
    }

    public static void transformPlayer(AbstractClientPlayer player, PoseStack poseStack, float partialTick) {
        RollAnimation animation = active(player);
        if (animation == null) {
            return;
        }
        float progress = progress(player, animation, partialTick);
        float eased = smootherStep(progress);
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        float relativeYaw = relativeYawDegrees(animation.movementYaw(), bodyYaw);
        float lift = Mth.sin(progress * Mth.PI) * 0.13F;

        poseStack.translate(0.0F, lift, 0.0F);
        poseStack.translate(0.0F, 0.92F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-relativeYaw));
        poseStack.mulPose(Axis.XP.rotationDegrees(eased * 360.0F));
        poseStack.mulPose(Axis.YP.rotationDegrees(relativeYaw));
        poseStack.translate(0.0F, -0.92F, 0.0F);
    }

    public static void posePlayer(AbstractClientPlayer player, PlayerModel<?> model, float partialTick) {
        RollAnimation animation = active(player);
        if (animation == null) {
            return;
        }
        float envelope = Mth.sin(progress(player, animation, partialTick) * Mth.PI);
        model.crouching = false;
        model.swimAmount = 0.0F;
        model.body.xRot = Mth.lerp(envelope, model.body.xRot, 0.32F);
        model.head.xRot = Mth.lerp(envelope, model.head.xRot, -0.28F);
        model.rightArm.xRot = Mth.lerp(envelope, model.rightArm.xRot, -1.38F);
        model.leftArm.xRot = Mth.lerp(envelope, model.leftArm.xRot, -1.38F);
        model.rightArm.yRot = Mth.lerp(envelope, model.rightArm.yRot, -0.16F);
        model.leftArm.yRot = Mth.lerp(envelope, model.leftArm.yRot, 0.16F);
        model.rightArm.zRot = Mth.lerp(envelope, model.rightArm.zRot, 0.28F);
        model.leftArm.zRot = Mth.lerp(envelope, model.leftArm.zRot, -0.28F);
        model.rightLeg.xRot = Mth.lerp(envelope, model.rightLeg.xRot, -0.92F);
        model.leftLeg.xRot = Mth.lerp(envelope, model.leftLeg.xRot, -0.92F);
        model.rightLeg.yRot = Mth.lerp(envelope, model.rightLeg.yRot, -0.12F);
        model.leftLeg.yRot = Mth.lerp(envelope, model.leftLeg.yRot, 0.12F);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);
        model.jacket.copyFrom(model.body);
    }

    public static boolean isRolling(net.minecraft.world.entity.player.Player player) {
        return active(player) != null;
    }

    private static RollAnimation active(net.minecraft.world.entity.player.Player player) {
        RollAnimation animation = ACTIVE.get(player.getUUID());
        if (animation == null) {
            return null;
        }
        if (player.level().getGameTime() >= animation.startedAt() + animation.durationTicks()) {
            ACTIVE.remove(player.getUUID(), animation);
            return null;
        }
        return animation;
    }

    private static float progress(AbstractClientPlayer player, RollAnimation animation, float partialTick) {
        return Mth.clamp(
                (player.level().getGameTime() + partialTick - animation.startedAt()) / animation.durationTicks(),
                0.0F,
                1.0F
        );
    }

    private static float smootherStep(float value) {
        return value * value * value * (value * (value * 6.0F - 15.0F) + 10.0F);
    }

    private static void suppressActionKeys(Minecraft minecraft) {
        minecraft.options.keyAttack.setDown(false);
        minecraft.options.keyUse.setDown(false);
        minecraft.options.keyPickItem.setDown(false);
        while (minecraft.options.keyAttack.consumeClick()) {
        }
        while (minecraft.options.keyUse.consumeClick()) {
        }
        while (minecraft.options.keyPickItem.consumeClick()) {
        }
    }

    static float movementYawDegrees(float motionX, float motionZ) {
        return (float) Math.toDegrees(Math.atan2(-motionX, motionZ));
    }

    static float relativeYawDegrees(float movementYaw, float bodyYaw) {
        return Mth.wrapDegrees(movementYaw - bodyYaw);
    }

    private record RollAnimation(long startedAt, int durationTicks, float movementYaw) {
    }
}
