package com.steveaaaaa.ability.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/**
 * A small, self-contained articulated player mesh used only during a dodge.
 * Unlike the vanilla model, its arms and legs have real elbow, wrist, and knee
 * pivots, so the roll can curl instead of rotating six rigid cuboids.
 */
final class DodgeRollModel {
    private static final DodgeRollModel SKIN_WIDE = create(false, CubeDeformation.NONE, true);
    private static final DodgeRollModel SKIN_SLIM = create(true, CubeDeformation.NONE, true);
    private static final DodgeRollModel ARMOR_INNER = create(false, new CubeDeformation(0.5F), false);
    private static final DodgeRollModel ARMOR_OUTER = create(false, new CubeDeformation(1.0F), false);

    private final ModelPart root;
    private final ModelPart torso;
    private final ModelPart chest;
    private final ModelPart head;
    private final ModelPart hat;
    private final ModelPart rightArm;
    private final ModelPart rightForearm;
    private final ModelPart rightHand;
    private final ModelPart leftArm;
    private final ModelPart leftForearm;
    private final ModelPart leftHand;
    private final ModelPart rightLeg;
    private final ModelPart rightShin;
    private final ModelPart leftLeg;
    private final ModelPart leftShin;

    private DodgeRollModel(ModelPart root) {
        this.root = root;
        this.torso = root.getChild("torso");
        this.chest = torso.getChild("chest");
        this.head = chest.getChild("head");
        this.hat = head.getChild("hat");
        this.rightArm = chest.getChild("right_arm");
        this.rightForearm = rightArm.getChild("right_forearm");
        this.rightHand = rightForearm.getChild("right_hand");
        this.leftArm = chest.getChild("left_arm");
        this.leftForearm = leftArm.getChild("left_forearm");
        this.leftHand = leftForearm.getChild("left_hand");
        this.rightLeg = root.getChild("right_leg");
        this.rightShin = rightLeg.getChild("right_shin");
        this.leftLeg = root.getChild("left_leg");
        this.leftShin = leftLeg.getChild("left_shin");
    }

    static boolean renderSkin(
            boolean slim,
            HumanoidModel<?> source,
            DodgeAnimationEvents.RollPose pose,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            int color
    ) {
        DodgeRollModel model = slim ? SKIN_SLIM : SKIN_WIDE;
        model.apply(pose);
        model.copyVisibility(source);
        model.root.render(poseStack, consumer, packedLight, packedOverlay, color);
        return true;
    }

    static boolean renderArmor(
            HumanoidModel<?> source,
            DodgeAnimationEvents.RollPose pose,
            PoseStack poseStack,
            VertexConsumer consumer,
            int packedLight,
            int packedOverlay,
            int color
    ) {
        boolean leggings = source.body.visible
                && source.rightLeg.visible
                && source.leftLeg.visible
                && !source.rightArm.visible
                && !source.leftArm.visible;
        DodgeRollModel model = leggings ? ARMOR_INNER : ARMOR_OUTER;
        model.apply(pose);
        model.copyVisibility(source);
        model.hat.visible = source.head.visible || source.hat.visible;
        model.root.render(poseStack, consumer, packedLight, packedOverlay, color);
        return true;
    }

    static void translateToHand(
            boolean slim,
            HumanoidArm arm,
            DodgeAnimationEvents.RollPose pose,
            PoseStack poseStack
    ) {
        DodgeRollModel model = slim ? SKIN_SLIM : SKIN_WIDE;
        model.apply(pose);
        model.torso.translateAndRotate(poseStack);
        model.chest.translateAndRotate(poseStack);
        ModelPart upperArm = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        ModelPart forearm = arm == HumanoidArm.RIGHT ? model.rightForearm : model.leftForearm;
        ModelPart hand = arm == HumanoidArm.RIGHT ? model.rightHand : model.leftHand;
        upperArm.translateAndRotate(poseStack);
        forearm.translateAndRotate(poseStack);
        hand.translateAndRotate(poseStack);
        poseStack.translate(0.0F, 0.075F, 0.0F);
    }

    private void apply(DodgeAnimationEvents.RollPose pose) {
        root.getAllParts().forEach(ModelPart::resetPose);
        torso.xRot = radians(pose.bodyPitch());
        chest.xRot = radians(pose.chestPitch());
        head.xRot = radians(pose.headPitch() - pose.bodyPitch() - pose.chestPitch());

        rightArm.xRot = radians(pose.rightArmPitch() - pose.bodyPitch() - pose.chestPitch());
        leftArm.xRot = radians(pose.leftArmPitch() - pose.bodyPitch() - pose.chestPitch());
        rightArm.yRot = -0.12F;
        leftArm.yRot = 0.12F;
        rightArm.zRot = radians(pose.armSpread());
        leftArm.zRot = radians(-pose.armSpread());
        rightForearm.xRot = radians(pose.rightElbowPitch());
        leftForearm.xRot = radians(pose.leftElbowPitch());
        rightHand.xRot = radians(pose.rightWristPitch());
        leftHand.xRot = radians(pose.leftWristPitch());

        rightLeg.xRot = radians(pose.rightLegPitch());
        leftLeg.xRot = radians(pose.leftLegPitch());
        rightLeg.yRot = -0.08F;
        leftLeg.yRot = 0.08F;
        rightShin.xRot = radians(pose.rightKneePitch());
        leftShin.xRot = radians(pose.leftKneePitch());
    }

    private void copyVisibility(HumanoidModel<?> source) {
        head.visible = source.head.visible;
        hat.visible = source.hat.visible;
        // Torso and chest are also the transform parents for the head and arms.
        // Keep the joints active and hide only their cubes for head-only/armor passes.
        torso.visible = true;
        chest.visible = true;
        torso.skipDraw = !source.body.visible;
        chest.skipDraw = !source.body.visible;
        rightArm.visible = source.rightArm.visible;
        rightForearm.visible = source.rightArm.visible;
        rightHand.visible = source.rightArm.visible;
        leftArm.visible = source.leftArm.visible;
        leftForearm.visible = source.leftArm.visible;
        leftHand.visible = source.leftArm.visible;
        rightLeg.visible = source.rightLeg.visible;
        rightShin.visible = source.rightLeg.visible;
        leftLeg.visible = source.leftLeg.visible;
        leftShin.visible = source.leftLeg.visible;
    }

    private static DodgeRollModel create(boolean slim, CubeDeformation deformation, boolean skin) {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        CubeDeformation overlay = deformation.extend(0.25F);

        CubeListBuilder lowerBody = CubeListBuilder.create()
                .texOffs(16, 22).addBox(-4.0F, -6.0F, -2.0F, 8.0F, 6.0F, 4.0F, deformation);
        if (skin) {
            lowerBody.texOffs(16, 38).addBox(-4.0F, -6.0F, -2.0F, 8.0F, 6.0F, 4.0F, overlay);
        }
        PartDefinition torso = root.addOrReplaceChild("torso", lowerBody, PartPose.offset(0.0F, 12.0F, 0.0F));

        CubeListBuilder upperBody = CubeListBuilder.create()
                .texOffs(16, 16).addBox(-4.0F, -6.0F, -2.0F, 8.0F, 6.0F, 4.0F, deformation);
        if (skin) {
            upperBody.texOffs(16, 32).addBox(-4.0F, -6.0F, -2.0F, 8.0F, 6.0F, 4.0F, overlay);
        }
        PartDefinition chest = torso.addOrReplaceChild("chest", upperBody, PartPose.offset(0.0F, -6.0F, 0.0F));

        CubeListBuilder headBuilder = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, deformation);
        PartDefinition head = chest.addOrReplaceChild("head", headBuilder, PartPose.offset(0.0F, -6.0F, 0.0F));
        CubeListBuilder hatBuilder = skin
                ? CubeListBuilder.create().texOffs(32, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, overlay)
                : CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, deformation.extend(0.25F));
        head.addOrReplaceChild("hat", hatBuilder, PartPose.ZERO);

        int armWidth = slim ? 3 : 4;
        float armMinX = -armWidth * 0.5F;
        float armX = slim ? 4.5F : 5.0F;
        addArm(chest, "right", armX, armMinX, armWidth, deformation, overlay, skin,
                40, 16, 40, 32, false);
        addArm(chest, "left", -armX, armMinX, armWidth, deformation, overlay, skin,
                skin ? 32 : 40, skin ? 48 : 16, 48, 48, !skin);

        addLeg(root, "right", -1.9F, deformation, overlay, skin, 0, 16, 0, 32, false);
        addLeg(root, "left", 1.9F, deformation, overlay, skin,
                skin ? 16 : 0, skin ? 48 : 16, 0, 48, !skin);

        int textureHeight = skin ? 64 : 32;
        return new DodgeRollModel(LayerDefinition.create(mesh, 64, textureHeight).bakeRoot());
    }

    private static void addArm(
            PartDefinition chest,
            String side,
            float pivotX,
            float minX,
            int width,
            CubeDeformation deformation,
            CubeDeformation overlay,
            boolean skin,
            int textureU,
            int textureV,
            int overlayU,
            int overlayV,
            boolean mirror
    ) {
        CubeListBuilder upper = CubeListBuilder.create().mirror(mirror)
                .texOffs(textureU, textureV).addBox(minX, -2.0F, -2.0F, width, 5.0F, 4.0F, deformation);
        if (skin) {
            upper.texOffs(overlayU, overlayV).addBox(minX, -2.0F, -2.0F, width, 5.0F, 4.0F, overlay);
        }
        PartDefinition arm = chest.addOrReplaceChild(side + "_arm", upper, PartPose.offset(pivotX, -4.0F, 0.0F));

        CubeListBuilder forearmBuilder = CubeListBuilder.create().mirror(mirror)
                .texOffs(textureU, textureV + 5).addBox(minX, 0.0F, -2.0F, width, 5.0F, 4.0F, deformation);
        if (skin) {
            forearmBuilder.texOffs(overlayU, overlayV + 5)
                    .addBox(minX, 0.0F, -2.0F, width, 5.0F, 4.0F, overlay);
        }
        PartDefinition forearm = arm.addOrReplaceChild(
                side + "_forearm",
                forearmBuilder,
                PartPose.offset(0.0F, 3.0F, 0.0F)
        );

        CubeListBuilder handBuilder = CubeListBuilder.create().mirror(mirror)
                .texOffs(textureU, textureV + 10).addBox(minX, 0.0F, -2.0F, width, 2.0F, 4.0F, deformation);
        if (skin) {
            handBuilder.texOffs(overlayU, overlayV + 10)
                    .addBox(minX, 0.0F, -2.0F, width, 2.0F, 4.0F, overlay);
        }
        forearm.addOrReplaceChild(side + "_hand", handBuilder, PartPose.offset(0.0F, 5.0F, 0.0F));
    }

    private static void addLeg(
            PartDefinition root,
            String side,
            float pivotX,
            CubeDeformation deformation,
            CubeDeformation overlay,
            boolean skin,
            int textureU,
            int textureV,
            int overlayU,
            int overlayV,
            boolean mirror
    ) {
        CubeListBuilder thigh = CubeListBuilder.create().mirror(mirror)
                .texOffs(textureU, textureV).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, deformation);
        if (skin) {
            thigh.texOffs(overlayU, overlayV).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, overlay);
        }
        PartDefinition leg = root.addOrReplaceChild(side + "_leg", thigh, PartPose.offset(pivotX, 12.0F, 0.0F));

        CubeListBuilder shin = CubeListBuilder.create().mirror(mirror)
                .texOffs(textureU, textureV + 6).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, deformation);
        if (skin) {
            shin.texOffs(overlayU, overlayV + 6).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, overlay);
        }
        leg.addOrReplaceChild(side + "_shin", shin, PartPose.offset(0.0F, 6.0F, 0.0F));
    }

    private static float radians(float degrees) {
        return degrees * Mth.DEG_TO_RAD;
    }
}
