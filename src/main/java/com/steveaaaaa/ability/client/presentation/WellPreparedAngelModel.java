package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;

final class WellPreparedAngelModel {
    private final ModelPart root;
    private final ModelPart robe;
    private final ModelPart torso;
    private final ModelPart leftSleeve;
    private final ModelPart rightSleeve;
    private final ModelPart leftWing;
    private final ModelPart rightWing;
    private final ModelPart head;
    private final ModelPart hair;
    private final ModelPart hands;
    private final ModelPart eyes;
    private final ModelPart halo;

    WellPreparedAngelModel(ModelPart root) {
        this.root = root;
        robe = root.getChild("robe");
        torso = root.getChild("torso");
        leftSleeve = root.getChild("left_sleeve");
        rightSleeve = root.getChild("right_sleeve");
        leftWing = root.getChild("left_wing");
        rightWing = root.getChild("right_wing");
        head = root.getChild("head");
        hair = root.getChild("hair");
        hands = root.getChild("hands");
        eyes = root.getChild("eyes");
        halo = root.getChild("halo");
    }

    static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("robe", CubeListBuilder.create()
                        .texOffs(0, 32).addBox(-4.5F, -12.0F, -2.5F, 9.0F, 5.0F, 5.0F)
                        .texOffs(0, 42).addBox(-5.5F, -7.0F, -3.0F, 11.0F, 4.0F, 6.0F)
                        .texOffs(0, 52).addBox(-7.0F, -3.0F, -3.5F, 14.0F, 3.0F, 7.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("torso", CubeListBuilder.create()
                        .texOffs(24, 20).addBox(-4.0F, -21.0F, -2.25F, 8.0F, 9.0F, 4.5F),
                PartPose.ZERO);
        root.addOrReplaceChild("left_sleeve", CubeListBuilder.create()
                        .texOffs(44, 28).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F),
                PartPose.offset(4.0F, -19.5F, -0.5F));
        root.addOrReplaceChild("right_sleeve", CubeListBuilder.create()
                        .texOffs(44, 28).addBox(-1.5F, 0.0F, -1.5F, 3.0F, 8.0F, 3.0F),
                PartPose.offset(-4.0F, -19.5F, -0.5F));

        root.addOrReplaceChild("left_wing", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.0F, -12.0F, 0.0F, 4.0F, 15.0F, 2.0F)
                        .texOffs(12, 0).addBox(-8.0F, -9.0F, 0.0F, 5.0F, 14.0F, 2.0F)
                        .texOffs(26, 0).addBox(-11.0F, -5.0F, 0.0F, 4.0F, 12.0F, 2.0F)
                        .texOffs(38, 0).addBox(-7.0F, 3.0F, 0.0F, 3.0F, 11.0F, 2.0F),
                PartPose.offset(-3.0F, -18.0F, 2.0F));
        root.addOrReplaceChild("right_wing", CubeListBuilder.create().mirror()
                        .texOffs(0, 0).addBox(0.0F, -12.0F, 0.0F, 4.0F, 15.0F, 2.0F)
                        .texOffs(12, 0).addBox(3.0F, -9.0F, 0.0F, 5.0F, 14.0F, 2.0F)
                        .texOffs(26, 0).addBox(7.0F, -5.0F, 0.0F, 4.0F, 12.0F, 2.0F)
                        .texOffs(38, 0).addBox(4.0F, 3.0F, 0.0F, 3.0F, 11.0F, 2.0F),
                PartPose.offset(3.0F, -18.0F, 2.0F));

        root.addOrReplaceChild("head", CubeListBuilder.create()
                        .texOffs(32, 40).addBox(-4.0F, -29.0F, -4.0F, 8.0F, 8.0F, 8.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("hair", CubeListBuilder.create()
                        .texOffs(32, 40).addBox(-4.0F, -29.0F, -4.0F, 8.0F, 8.0F, 8.0F,
                                new CubeDeformation(0.38F))
                        .texOffs(54, 0).addBox(-4.8F, -25.0F, -2.2F, 2.0F, 11.0F, 4.0F)
                        .texOffs(54, 0).addBox(2.8F, -25.0F, -2.2F, 2.0F, 11.0F, 4.0F),
                PartPose.ZERO);
        root.addOrReplaceChild("hands", CubeListBuilder.create()
                        .texOffs(48, 48).addBox(-1.75F, -13.5F, -4.15F, 1.65F, 3.0F, 1.6F)
                        .texOffs(48, 48).addBox(0.1F, -13.5F, -4.15F, 1.65F, 3.0F, 1.6F),
                PartPose.ZERO);
        root.addOrReplaceChild("eyes", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.5F, -25.2F, -4.18F, 1.5F, 0.65F, 0.35F)
                        .texOffs(0, 0).addBox(1.0F, -25.2F, -4.18F, 1.5F, 0.65F, 0.35F),
                PartPose.ZERO);
        root.addOrReplaceChild("halo", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0F, -32.0F, -6.0F, 6.0F, 1.0F, 1.0F)
                        .texOffs(0, 0).addBox(-3.0F, -32.0F, 5.0F, 6.0F, 1.0F, 1.0F)
                        .texOffs(0, 0).addBox(-6.0F, -32.0F, -3.0F, 1.0F, 1.0F, 6.0F)
                        .texOffs(0, 0).addBox(5.0F, -32.0F, -3.0F, 1.0F, 1.0F, 6.0F)
                        .texOffs(0, 0).addBox(-5.0F, -32.0F, -5.0F, 2.0F, 1.0F, 2.0F)
                        .texOffs(0, 0).addBox(3.0F, -32.0F, -5.0F, 2.0F, 1.0F, 2.0F)
                        .texOffs(0, 0).addBox(-5.0F, -32.0F, 3.0F, 2.0F, 1.0F, 2.0F)
                        .texOffs(0, 0).addBox(3.0F, -32.0F, 3.0F, 2.0F, 1.0F, 2.0F),
                PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    void setupAnimation(float age) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float wingBreath = Mth.sin(age * 0.11F) * 0.065F;
        leftWing.zRot = -0.20F - wingBreath;
        leftWing.yRot = 0.18F + wingBreath * 0.35F;
        rightWing.zRot = 0.20F + wingBreath;
        rightWing.yRot = -0.18F - wingBreath * 0.35F;

        leftSleeve.xRot = -0.58F;
        leftSleeve.yRot = -0.12F;
        leftSleeve.zRot = 0.56F + Mth.sin(age * 0.08F) * 0.018F;
        rightSleeve.xRot = -0.58F;
        rightSleeve.yRot = 0.12F;
        rightSleeve.zRot = -0.56F - Mth.sin(age * 0.08F) * 0.018F;
        halo.yRot = age * 0.018F;
    }

    void renderMaterial(PoseStack poseStack, VertexConsumer material, int alpha) {
        int robeColor = argb(alpha, 255, 247, 220);
        robe.render(poseStack, material, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, robeColor);
        torso.render(poseStack, material, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, robeColor);
        leftSleeve.render(poseStack, material, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, robeColor);
        rightSleeve.render(poseStack, material, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, robeColor);
        leftWing.render(poseStack, material, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha, 255, 252, 236));
        rightWing.render(poseStack, material, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha, 255, 252, 236));
    }

    void renderColors(PoseStack poseStack, VertexConsumer white, int alpha) {
        head.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha, 250, 210, 184));
        hands.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha, 250, 210, 184));
        hair.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha, 231, 178, 62));
        eyes.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha, 112, 72, 42));
        halo.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(Math.min(255, alpha + 45), 255, 202, 62));
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
