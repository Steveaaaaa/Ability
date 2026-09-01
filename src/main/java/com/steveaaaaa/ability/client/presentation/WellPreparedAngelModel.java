package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
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
    private final ModelPart hairShadow;
    private final ModelPart hairHighlight;
    private final ModelPart hands;
    private final ModelPart eyeWhites;
    private final ModelPart irises;
    private final ModelPart pupils;
    private final ModelPart eyeHighlights;
    private final ModelPart eyebrows;
    private final ModelPart nose;
    private final ModelPart mouth;
    private final ModelPart blush;
    private final ModelPart robeTrim;
    private final ModelPart wingShadows;
    private final ModelPart wingHighlights;
    private final ModelPart halo;
    private final ModelPart haloHighlight;

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
        hairShadow = root.getChild("hair_shadow");
        hairHighlight = root.getChild("hair_highlight");
        hands = root.getChild("hands");
        eyeWhites = root.getChild("eye_whites");
        irises = root.getChild("irises");
        pupils = root.getChild("pupils");
        eyeHighlights = root.getChild("eye_highlights");
        eyebrows = root.getChild("eyebrows");
        nose = root.getChild("nose");
        mouth = root.getChild("mouth");
        blush = root.getChild("blush");
        robeTrim = root.getChild("robe_trim");
        wingShadows = root.getChild("wing_shadows");
        wingHighlights = root.getChild("wing_highlights");
        halo = root.getChild("halo");
        haloHighlight = root.getChild("halo_highlight");
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
                        .texOffs(32, 40).addBox(-3.5F, -28.5F, -3.5F, 7.0F, 5.5F, 7.0F)
                        .texOffs(32, 40).addBox(-2.75F, -23.0F, -3.25F, 5.5F, 2.0F, 6.5F),
                PartPose.ZERO);
        root.addOrReplaceChild("hair", CubeListBuilder.create()
                        .texOffs(32, 40).addBox(-3.9F, -29.15F, -3.9F, 7.8F, 1.9F, 7.8F)
                        .texOffs(54, 0).addBox(-3.9F, -27.4F, 2.7F, 7.8F, 6.5F, 1.4F)
                        .texOffs(54, 0).addBox(-4.15F, -27.8F, -3.15F, 1.25F, 10.8F, 6.4F)
                        .texOffs(54, 0).addBox(2.9F, -27.8F, -3.15F, 1.25F, 10.8F, 6.4F)
                        .texOffs(54, 0).addBox(-3.0F, -28.1F, -3.85F, 2.0F, 2.2F, 0.7F)
                        .texOffs(54, 0).addBox(1.0F, -28.1F, -3.85F, 2.0F, 2.2F, 0.7F),
                PartPose.ZERO);
        root.addOrReplaceChild("hair_shadow", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-4.2F, -22.0F, -2.9F, 1.35F, 5.5F, 5.7F)
                        .texOffs(0, 0).addBox(2.85F, -22.0F, -2.9F, 1.35F, 5.5F, 5.7F)
                        .texOffs(0, 0).addBox(-3.7F, -21.3F, 2.75F, 7.4F, 4.2F, 1.2F),
                PartPose.ZERO);
        root.addOrReplaceChild("hair_highlight", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.55F, -29.3F, -4.05F, 2.1F, 0.55F, 0.55F)
                        .texOffs(0, 0).addBox(-3.95F, -26.2F, -3.25F, 0.45F, 4.2F, 1.2F)
                        .texOffs(0, 0).addBox(3.5F, -25.2F, -3.25F, 0.45F, 2.9F, 1.2F),
                PartPose.ZERO);
        root.addOrReplaceChild("hands", CubeListBuilder.create()
                        .texOffs(48, 48).addBox(-1.75F, -13.5F, -4.15F, 1.65F, 3.0F, 1.6F)
                        .texOffs(48, 48).addBox(0.1F, -13.5F, -4.15F, 1.65F, 3.0F, 1.6F),
                PartPose.ZERO);
        root.addOrReplaceChild("eye_whites", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.55F, -25.65F, -3.69F, 1.8F, 0.9F, 0.24F)
                        .texOffs(0, 0).addBox(0.75F, -25.65F, -3.69F, 1.8F, 0.9F, 0.24F),
                PartPose.ZERO);
        root.addOrReplaceChild("irises", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-1.95F, -25.6F, -3.91F, 0.72F, 0.78F, 0.2F)
                        .texOffs(0, 0).addBox(1.23F, -25.6F, -3.91F, 0.72F, 0.78F, 0.2F),
                PartPose.ZERO);
        root.addOrReplaceChild("pupils", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-1.72F, -25.5F, -4.09F, 0.3F, 0.58F, 0.16F)
                        .texOffs(0, 0).addBox(1.42F, -25.5F, -4.09F, 0.3F, 0.58F, 0.16F),
                PartPose.ZERO);
        root.addOrReplaceChild("eye_highlights", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-1.87F, -25.59F, -4.23F, 0.18F, 0.2F, 0.12F)
                        .texOffs(0, 0).addBox(1.29F, -25.59F, -4.23F, 0.18F, 0.2F, 0.12F),
                PartPose.ZERO);
        root.addOrReplaceChild("eyebrows", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.6F, -26.35F, -3.78F, 1.75F, 0.28F, 0.22F)
                        .texOffs(0, 0).addBox(0.85F, -26.35F, -3.78F, 1.75F, 0.28F, 0.22F),
                PartPose.ZERO);
        root.addOrReplaceChild("nose", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-0.18F, -24.75F, -3.76F, 0.36F, 0.65F, 0.24F),
                PartPose.ZERO);
        root.addOrReplaceChild("mouth", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-0.72F, -23.55F, -3.54F, 1.44F, 0.28F, 0.22F),
                PartPose.ZERO);
        root.addOrReplaceChild("blush", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.75F, -24.15F, -3.62F, 0.85F, 0.3F, 0.18F)
                        .texOffs(0, 0).addBox(1.9F, -24.15F, -3.62F, 0.85F, 0.3F, 0.18F),
                PartPose.ZERO);
        root.addOrReplaceChild("robe_trim", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.5F, -20.9F, -2.5F, 7.0F, 0.65F, 0.3F)
                        .texOffs(0, 0).addBox(-4.55F, -12.15F, -2.7F, 9.1F, 0.65F, 0.35F)
                        .texOffs(0, 0).addBox(-6.75F, -1.0F, -3.7F, 13.5F, 0.7F, 0.35F),
                PartPose.ZERO);
        root.addOrReplaceChild("wing_shadows", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-14.0F, -20.0F, 4.15F, 4.0F, 9.0F, 0.45F)
                        .texOffs(0, 0).addBox(10.0F, -20.0F, 4.15F, 4.0F, 9.0F, 0.45F),
                PartPose.ZERO);
        root.addOrReplaceChild("wing_highlights", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-10.0F, -29.2F, 4.2F, 3.0F, 7.0F, 0.4F)
                        .texOffs(0, 0).addBox(7.0F, -29.2F, 4.2F, 3.0F, 7.0F, 0.4F),
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
        root.addOrReplaceChild("halo_highlight", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-2.0F, -32.18F, -6.15F, 4.0F, 0.38F, 0.38F)
                        .texOffs(0, 0).addBox(-6.15F, -32.18F, -2.0F, 0.38F, 0.38F, 4.0F),
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
        haloHighlight.yRot = halo.yRot;
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
                argb(alpha, 224, 164, 52));
        hairShadow.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha, 142, 82, 35));
        hairHighlight.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha, 255, 222, 105));
        eyeWhites.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha, 255, 250, 235));
        irises.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha, 82, 168, 188));
        pupils.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha, 24, 49, 62));
        eyeHighlights.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(Math.min(255, alpha + 45), 255, 255, 230));
        eyebrows.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha, 116, 67, 36));
        nose.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha, 218, 153, 135));
        mouth.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha, 174, 78, 88));
        blush.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha / 2, 231, 126, 134));
        robeTrim.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha, 116, 169, 193));
        wingShadows.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(alpha * 3 / 4, 154, 187, 202));
        wingHighlights.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(Math.min(255, alpha + 25), 255, 236, 174));
        halo.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(Math.min(255, alpha + 45), 255, 202, 62));
        haloHighlight.render(poseStack, white, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                argb(Math.min(255, alpha + 65), 255, 246, 170));
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return alpha << 24 | red << 16 | green << 8 | blue;
    }
}
