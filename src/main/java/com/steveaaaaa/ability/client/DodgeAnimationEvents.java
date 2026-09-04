package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.effect.DodgeEffect;
import com.steveaaaaa.ability.network.ClientDodgeAnimationQueue;
import com.steveaaaaa.ability.network.ClientboundDodgeAnimationPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class DodgeAnimationEvents {
    private static final Map<UUID, RollAnimation> ACTIVE = new HashMap<>();
    private static final ThreadLocal<RenderContext> RENDER_CONTEXT = new ThreadLocal<>();
    private static final int TRANSITION_TICKS = 2;
    private static final float[] KEY_TIMES = {
            0.0F, 0.105F, 0.211F, 0.368F, 0.553F, 0.684F, 0.789F, 0.895F, 1.0F
    };
    private static final RollClip FORWARD_CLIP = new RollClip(
            new float[]{0.0F, 28.0F, 72.0F, 142.0F, 232.0F, 298.0F, 342.0F, 370.0F, 360.0F},
            new float[]{0.0F, 0.11F, 0.19F, 0.35F, 0.48F, 0.27F, -0.08F, -0.04F, 0.0F},
            new float[]{8.0F, 20.0F, 28.0F, 18.0F, -4.0F, 8.0F, 18.0F, 12.0F, 0.0F},
            new float[]{2.0F, 10.0F, 18.0F, 24.0F, 15.0F, 0.0F, -8.0F, -4.0F, 0.0F},
            new float[]{-5.0F, -20.0F, -32.0F, -20.0F, 10.0F, -12.0F, -18.0F, -8.0F, 0.0F},
            new float[]{-35.0F, -75.0F, -125.0F, -150.0F, -135.0F, -95.0F, -55.0F, -25.0F, 0.0F},
            new float[]{-10.0F, -55.0F, -115.0F, -150.0F, -145.0F, -110.0F, -65.0F, -30.0F, 0.0F},
            new float[]{10.0F, 45.0F, 90.0F, 125.0F, 140.0F, 115.0F, 75.0F, 32.0F, 0.0F},
            new float[]{5.0F, 35.0F, 82.0F, 120.0F, 138.0F, 120.0F, 80.0F, 35.0F, 0.0F},
            new float[]{0.0F, 10.0F, 20.0F, 18.0F, 5.0F, -10.0F, -8.0F, 0.0F, 0.0F},
            new float[]{0.0F, 6.0F, 16.0F, 20.0F, 8.0F, -8.0F, -6.0F, 0.0F, 0.0F},
            new float[]{20.0F, -15.0F, -65.0F, -110.0F, -95.0F, -55.0F, -20.0F, 12.0F, 0.0F},
            new float[]{-10.0F, -50.0F, -115.0F, -95.0F, -55.0F, -10.0F, 20.0F, 5.0F, 0.0F},
            new float[]{5.0F, 30.0F, 75.0F, 120.0F, 135.0F, 105.0F, 60.0F, 25.0F, 0.0F},
            new float[]{10.0F, 45.0F, 95.0F, 130.0F, 120.0F, 90.0F, 50.0F, 20.0F, 0.0F},
            new float[]{15.0F, 25.0F, 16.0F, 5.0F, -10.0F, -18.0F, -12.0F, -5.0F, 0.0F}
    );
    private static final RollClip BACKWARD_CLIP = new RollClip(
            new float[]{0.0F, 34.0F, 78.0F, 158.0F, 244.0F, 304.0F, 346.0F, 368.0F, 360.0F},
            new float[]{0.0F, 0.12F, 0.20F, 0.55F, 0.47F, 0.30F, -0.03F, -0.06F, 0.0F},
            new float[]{-7.0F, -18.0F, -24.0F, -8.0F, 12.0F, 16.0F, 10.0F, 5.0F, 0.0F},
            new float[]{-2.0F, -8.0F, -16.0F, -22.0F, -10.0F, 5.0F, 9.0F, 4.0F, 0.0F},
            new float[]{4.0F, 18.0F, 28.0F, 18.0F, -8.0F, -15.0F, -10.0F, -5.0F, 0.0F},
            new float[]{-15.0F, -55.0F, -118.0F, -152.0F, -142.0F, -105.0F, -58.0F, -22.0F, 0.0F},
            new float[]{-38.0F, -82.0F, -132.0F, -148.0F, -126.0F, -86.0F, -42.0F, -18.0F, 0.0F},
            new float[]{8.0F, 38.0F, 85.0F, 124.0F, 142.0F, 118.0F, 72.0F, 28.0F, 0.0F},
            new float[]{12.0F, 48.0F, 96.0F, 128.0F, 136.0F, 108.0F, 65.0F, 25.0F, 0.0F},
            new float[]{0.0F, -8.0F, -18.0F, -20.0F, -6.0F, 10.0F, 7.0F, 0.0F, 0.0F},
            new float[]{0.0F, -10.0F, -20.0F, -16.0F, -4.0F, 8.0F, 6.0F, 0.0F, 0.0F},
            new float[]{-15.0F, -48.0F, -110.0F, -98.0F, -62.0F, -22.0F, 18.0F, 8.0F, 0.0F},
            new float[]{18.0F, -10.0F, -58.0F, -112.0F, -98.0F, -52.0F, -16.0F, 12.0F, 0.0F},
            new float[]{10.0F, 42.0F, 92.0F, 132.0F, 125.0F, 92.0F, 52.0F, 20.0F, 0.0F},
            new float[]{5.0F, 28.0F, 70.0F, 118.0F, 138.0F, 110.0F, 58.0F, 22.0F, 0.0F},
            new float[]{-16.0F, -27.0F, -18.0F, -6.0F, 12.0F, 18.0F, 11.0F, 4.0F, 0.0F}
    );

    private DodgeAnimationEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            ClientDodgeAnimationQueue.clear();
            ACTIVE.clear();
            RENDER_CONTEXT.remove();
            return;
        }
        ClientboundDodgeAnimationPayload payload;
        while ((payload = ClientDodgeAnimationQueue.poll()) != null) {
            if (minecraft.level.getEntity(payload.playerEntityId()) instanceof AbstractClientPlayer player) {
                ACTIVE.put(player.getUUID(), new RollAnimation(
                        payload.startedAt(),
                        payload.durationTicks(),
                        movementYawDegrees(payload.motionX(), payload.motionZ()),
                        payload.motionX(),
                        payload.motionZ(),
                        payload.totalDistance(),
                        payload.backward()
                ));
            }
        }
        long gameTime = minecraft.level.getGameTime();
        if (minecraft.player != null) {
            RollAnimation localRoll = ACTIVE.get(minecraft.player.getUUID());
            if (localRoll != null) {
                applyLocalRootMotion(minecraft.player, localRoll, gameTime);
            }
        }
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
        RENDER_CONTEXT.remove();
    }

    public static void transformPlayer(AbstractClientPlayer player, PoseStack poseStack, float partialTick) {
        RollAnimation animation = active(player);
        if (animation == null) {
            return;
        }
        RollPose pose = sampledPose(player, animation, partialTick);
        float bodyYaw = Mth.rotLerp(partialTick, player.yBodyRotO, player.yBodyRot);
        // Epic-style directional dodges turn the rendered model toward the
        // requested direction before the local roll is applied. A backward
        // dodge keeps the model facing away from its travel direction and
        // uses the dedicated backward clip.
        float modelYaw = animation.backward()
                ? Mth.wrapDegrees(animation.movementYaw() + 180.0F)
                : animation.movementYaw();
        float relativeYaw = relativeYawDegrees(modelYaw, bodyYaw);

        poseStack.translate(0.0F, pose.verticalOffset(), 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(-relativeYaw));
        poseStack.translate(0.0F, 0.82F, 0.0F);
        poseStack.mulPose(Axis.XN.rotationDegrees(pose.rootPitch()));
        poseStack.translate(0.0F, -0.82F, 0.0F);
    }

    public static void posePlayer(AbstractClientPlayer player, PlayerModel<?> model, float partialTick) {
        RollAnimation animation = active(player);
        if (animation == null) {
            RENDER_CONTEXT.remove();
            return;
        }
        RollPose pose = sampledPose(player, animation, partialTick);
        RENDER_CONTEXT.set(new RenderContext(
                pose,
                player.getSkin().model() == PlayerSkin.Model.SLIM
        ));
        model.crouching = false;
        model.swimAmount = 0.0F;
        model.body.xRot = radians(pose.bodyPitch());
        model.head.xRot = radians(pose.headPitch());
        model.head.yRot = 0.0F;
        model.head.zRot = 0.0F;
        model.hat.copyFrom(model.head);
        model.rightArm.xRot = radians(pose.rightArmPitch());
        model.leftArm.xRot = radians(pose.leftArmPitch());
        model.rightArm.yRot = -0.12F;
        model.leftArm.yRot = 0.12F;
        model.rightArm.zRot = radians(pose.armSpread());
        model.leftArm.zRot = radians(-pose.armSpread());
        model.rightLeg.xRot = radians(pose.rightLegPitch());
        model.leftLeg.xRot = radians(pose.leftLegPitch());
        model.rightLeg.yRot = -0.08F;
        model.leftLeg.yRot = 0.08F;
        model.rightLeg.zRot = 0.0F;
        model.leftLeg.zRot = 0.0F;
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

    private static RollPose sampledPose(
            AbstractClientPlayer player,
            RollAnimation animation,
            float partialTick
    ) {
        float elapsed = progress(player, animation, partialTick) * animation.durationTicks();
        float mainDuration = Math.max(1.0F, animation.durationTicks() - TRANSITION_TICKS);
        float clipProgress = Mth.clamp((elapsed - TRANSITION_TICKS) / mainDuration, 0.0F, 1.0F);
        float transition = Mth.clamp(elapsed / TRANSITION_TICKS, 0.0F, 1.0F);
        return animation.clip().sample(clipProgress).scale(transition);
    }

    private static void applyLocalRootMotion(
            AbstractClientPlayer player,
            RollAnimation animation,
            long gameTime
    ) {
        long finalMotionTick = animation.startedAt() + animation.durationTicks() - 1L;
        long nextMotionTick = Math.max(animation.startedAt(), animation.lastMotionTick + 1L);
        long targetTick = Math.min(gameTime, finalMotionTick);
        if (nextMotionTick > targetTick) {
            return;
        }

        player.setSprinting(false);
        player.setDeltaMovement(0.0D, player.getDeltaMovement().y, 0.0D);
        for (long tick = nextMotionTick; tick <= targetTick; tick++) {
            int elapsed = Math.toIntExact(tick - animation.startedAt());
            double distance = DodgeEffect.motionForTick(
                    animation.totalDistance,
                    animation.durationTicks(),
                    elapsed,
                    animation.backward()
            );
            Vec3 direction = animation.blocked
                    ? Vec3.ZERO
                    : new Vec3(animation.directionX, 0.0D, animation.directionZ);
            Vec3 before = player.position();
            player.move(MoverType.SELF, direction.scale(distance));
            double requestedSqr = distance * distance;
            double movedSqr = player.position().subtract(before).horizontalDistanceSqr();
            if (requestedSqr > 1.0E-8D && movedSqr < requestedSqr * 0.36D) {
                animation.blocked = true;
            }
            animation.lastMotionTick = tick;
        }
    }

    public static boolean renderArticulatedModel(
            AgeableListModel<?> model,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            int color
    ) {
        RenderContext context = RENDER_CONTEXT.get();
        if (context == null || !(model instanceof HumanoidModel<?> humanoidModel)) {
            return false;
        }
        if (model instanceof PlayerModel<?>) {
            return DodgeRollModel.renderSkin(
                    context.slim(),
                    humanoidModel,
                    context.pose(),
                    poseStack,
                    consumer,
                    packedLight,
                    packedOverlay,
                    color
            );
        }
        return DodgeRollModel.renderArmor(
                humanoidModel,
                context.pose(),
                poseStack,
                consumer,
                packedLight,
                packedOverlay,
                color
        );
    }

    public static boolean translateArticulatedHand(HumanoidArm arm, PoseStack poseStack) {
        RenderContext context = RENDER_CONTEXT.get();
        if (context == null) {
            return false;
        }
        DodgeRollModel.translateToHand(context.slim(), arm, context.pose(), poseStack);
        return true;
    }

    public static void clearRenderContext() {
        RENDER_CONTEXT.remove();
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

    private static float radians(float degrees) {
        return degrees * Mth.DEG_TO_RAD;
    }

    private static float sample(float[] values, float progress) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        for (int index = 1; index < KEY_TIMES.length; index++) {
            if (clamped <= KEY_TIMES[index]) {
                float local = (clamped - KEY_TIMES[index - 1])
                        / (KEY_TIMES[index] - KEY_TIMES[index - 1]);
                float eased = local * local * (3.0F - 2.0F * local);
                return Mth.lerp(eased, values[index - 1], values[index]);
            }
        }
        return values[values.length - 1];
    }

    private record RollClip(
            float[] rootPitch,
            float[] verticalOffset,
            float[] bodyPitch,
            float[] chestPitch,
            float[] headPitch,
            float[] rightArmPitch,
            float[] leftArmPitch,
            float[] rightElbowPitch,
            float[] leftElbowPitch,
            float[] rightWristPitch,
            float[] leftWristPitch,
            float[] rightLegPitch,
            float[] leftLegPitch,
            float[] rightKneePitch,
            float[] leftKneePitch,
            float[] armSpread
    ) {
        private RollPose sample(float progress) {
            return new RollPose(
                    DodgeAnimationEvents.sample(rootPitch, progress),
                    DodgeAnimationEvents.sample(verticalOffset, progress),
                    DodgeAnimationEvents.sample(bodyPitch, progress),
                    DodgeAnimationEvents.sample(chestPitch, progress),
                    DodgeAnimationEvents.sample(headPitch, progress),
                    DodgeAnimationEvents.sample(rightArmPitch, progress),
                    DodgeAnimationEvents.sample(leftArmPitch, progress),
                    DodgeAnimationEvents.sample(rightElbowPitch, progress),
                    DodgeAnimationEvents.sample(leftElbowPitch, progress),
                    DodgeAnimationEvents.sample(rightWristPitch, progress),
                    DodgeAnimationEvents.sample(leftWristPitch, progress),
                    DodgeAnimationEvents.sample(rightLegPitch, progress),
                    DodgeAnimationEvents.sample(leftLegPitch, progress),
                    DodgeAnimationEvents.sample(rightKneePitch, progress),
                    DodgeAnimationEvents.sample(leftKneePitch, progress),
                    DodgeAnimationEvents.sample(armSpread, progress)
            );
        }
    }

    public record RollPose(
            float rootPitch,
            float verticalOffset,
            float bodyPitch,
            float chestPitch,
            float headPitch,
            float rightArmPitch,
            float leftArmPitch,
            float rightElbowPitch,
            float leftElbowPitch,
            float rightWristPitch,
            float leftWristPitch,
            float rightLegPitch,
            float leftLegPitch,
            float rightKneePitch,
            float leftKneePitch,
            float armSpread
    ) {
        private RollPose scale(float amount) {
            return new RollPose(
                    rootPitch * amount,
                    verticalOffset * amount,
                    bodyPitch * amount,
                    chestPitch * amount,
                    headPitch * amount,
                    rightArmPitch * amount,
                    leftArmPitch * amount,
                    rightElbowPitch * amount,
                    leftElbowPitch * amount,
                    rightWristPitch * amount,
                    leftWristPitch * amount,
                    rightLegPitch * amount,
                    leftLegPitch * amount,
                    rightKneePitch * amount,
                    leftKneePitch * amount,
                    armSpread * amount
            );
        }
    }

    private record RenderContext(RollPose pose, boolean slim) {
    }

    private static final class RollAnimation {
        private final long startedAt;
        private final int durationTicks;
        private final float movementYaw;
        private final float directionX;
        private final float directionZ;
        private final float totalDistance;
        private final boolean backward;
        private long lastMotionTick;
        private boolean blocked;

        private RollAnimation(
                long startedAt,
                int durationTicks,
                float movementYaw,
                float directionX,
                float directionZ,
                float totalDistance,
                boolean backward
        ) {
            this.startedAt = startedAt;
            this.durationTicks = durationTicks;
            this.movementYaw = movementYaw;
            this.directionX = directionX;
            this.directionZ = directionZ;
            this.totalDistance = totalDistance;
            this.backward = backward;
            this.lastMotionTick = startedAt - 1L;
        }

        private long startedAt() {
            return startedAt;
        }

        private int durationTicks() {
            return durationTicks;
        }

        private float movementYaw() {
            return movementYaw;
        }

        private boolean backward() {
            return backward;
        }

        private RollClip clip() {
            return backward ? BACKWARD_CLIP : FORWARD_CLIP;
        }
    }
}
