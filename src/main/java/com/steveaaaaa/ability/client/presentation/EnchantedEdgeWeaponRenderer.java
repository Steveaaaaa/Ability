package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.effect.EnchantedEdgeEffect;
import com.steveaaaaa.ability.presentation.AbilityCue;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class EnchantedEdgeWeaponRenderer {
    private static final Map<Integer, Long> ACTIVE_PLAYERS = new HashMap<>();
    private static ClientLevel activeLevel;

    private EnchantedEdgeWeaponRenderer() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(EnchantedEdgeEffect.TYPE)
                || !cue.cueId().equals(AbilityMod.id("weapon_aura"))) return;
        if (level != activeLevel) clear(level);
        if (cue.action() == AbilityCue.Action.STOP) {
            ACTIVE_PLAYERS.remove(cue.targetEntityId());
        } else {
            ACTIVE_PLAYERS.put(cue.targetEntityId(), level.getGameTime() + Math.max(1, cue.durationTicks()));
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level != activeLevel) {
            clear(level);
            return;
        }
        long time = level.getGameTime();
        ACTIVE_PLAYERS.entrySet().removeIf(entry -> time >= entry.getValue());
    }

    @SubscribeEvent
    public static void renderFirstPerson(RenderHandEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !isActive(player) || !EnchantedEdgeEffect.isWeapon(event.getItemStack())) return;
        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND
                ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean leftHand = arm == HumanoidArm.LEFT;
        ItemDisplayContext context = leftHand
                ? ItemDisplayContext.FIRST_PERSON_LEFT_HAND : ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        applyFirstPersonHandTransform(poseStack, player, event, arm);
        applyItemModelTransform(poseStack, player, event.getItemStack(), context, leftHand);
        renderOrbit(poseStack, event.getMultiBufferSource(), event.getPartialTick(), 1.0F);
        poseStack.popPose();
    }

    static boolean isActive(AbstractClientPlayer player) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && ACTIVE_PLAYERS.getOrDefault(player.getId(), Long.MIN_VALUE) > level.getGameTime();
    }

    static void applyItemModelTransform(PoseStack poseStack, AbstractClientPlayer player, ItemStack stack,
            ItemDisplayContext context, boolean leftHand) {
        Minecraft minecraft = Minecraft.getInstance();
        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        BakedModel model = itemRenderer.getModel(stack, player.level(), player, player.getId() + context.ordinal());
        ClientHooks.handleCameraTransforms(poseStack, model, context, leftHand);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
    }

    static void renderOrbit(PoseStack poseStack, MultiBufferSource buffers, float partialTick, float scale) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        double time = level.getGameTime() + partialTick;
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
        for (int ring = 0; ring < 3; ring++) {
            double phase = time * (0.105D + ring * 0.018D) + ring * 2.05D;
            double centerY = -0.3D + ring * 0.3D;
            for (int point = 0; point < 9; point++) {
                double angle = phase + point * Mth.TWO_PI / 9.0D;
                float radius = (0.115F + ring * 0.012F) * scale;
                float x = (float) Math.cos(angle) * radius;
                float z = (float) Math.sin(angle) * radius;
                float y = (float) (centerY + Math.sin(angle * 1.7D + ring) * 0.045D) * scale;
                float size = (0.014F + (point % 3) * 0.003F) * scale;
                int alpha = 120 + (int) ((Math.sin(angle + time * 0.16D) * 0.5D + 0.5D) * 90.0D);
                renderPixelCube(poseStack, vertices, x, y, z, size,
                        202 + ring * 10, 154 + ring * 12, 255, alpha);
            }
        }
    }

    private static void applyFirstPersonHandTransform(PoseStack poseStack, LocalPlayer player,
            RenderHandEvent event, HumanoidArm arm) {
        if (IClientItemExtensions.of(event.getItemStack()).applyForgeHandTransform(
                poseStack, player, arm, event.getItemStack(), event.getPartialTick(),
                event.getEquipProgress(), event.getSwingProgress())) return;
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float swing = event.getSwingProgress();
        boolean using = player.isUsingItem() && player.getUsedItemHand() == event.getHand()
                && player.getUseItemRemainingTicks() > 0;
        poseStack.translate(side * 0.56F, -0.52F - event.getEquipProgress() * 0.6F, -0.72F);
        if (using && event.getItemStack().getUseAnimation() == UseAnim.SPEAR) {
            poseStack.translate(side * -0.5F, 0.7F, 0.1F);
            poseStack.mulPose(Axis.XP.rotationDegrees(-55.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * 35.3F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * -9.785F));
            return;
        }
        if (!using) {
            float root = Mth.sqrt(swing);
            poseStack.translate(side * -0.4F * Mth.sin(root * Mth.PI),
                    0.2F * Mth.sin(root * Mth.TWO_PI),
                    -0.2F * Mth.sin(swing * Mth.PI));
            float horizontalAttack = Mth.sin(swing * swing * Mth.PI);
            float verticalAttack = Mth.sin(root * Mth.PI);
            poseStack.mulPose(Axis.YP.rotationDegrees(side * (45.0F + horizontalAttack * -20.0F)));
            poseStack.mulPose(Axis.ZP.rotationDegrees(side * verticalAttack * -20.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(verticalAttack * -80.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(side * -45.0F));
        }
    }

    private static void renderPixelCube(PoseStack poseStack, VertexConsumer vertices,
            float x, float y, float z, float size, int red, int green, int blue, int alpha) {
        float x0 = x - size;
        float x1 = x + size;
        float y0 = y - size;
        float y1 = y + size;
        float z0 = z - size;
        float z1 = z + size;
        face(poseStack, vertices, red, green, blue, alpha, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        face(poseStack, vertices, red, green, blue, alpha, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1);
        face(poseStack, vertices, red, green, blue, alpha, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1);
        face(poseStack, vertices, red, green, blue, alpha, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
        face(poseStack, vertices, red, green, blue, alpha, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        face(poseStack, vertices, red, green, blue, alpha, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
    }

    private static void face(PoseStack poseStack, VertexConsumer vertices, int red, int green, int blue, int alpha,
            float x0, float y0, float z0, float x1, float y1, float z1,
            float x2, float y2, float z2, float x3, float y3, float z3) {
        vertices.addVertex(poseStack.last(), x0, y0, z0).setColor(red, green, blue, alpha);
        vertices.addVertex(poseStack.last(), x1, y1, z1).setColor(red, green, blue, alpha);
        vertices.addVertex(poseStack.last(), x2, y2, z2).setColor(red, green, blue, alpha);
        vertices.addVertex(poseStack.last(), x3, y3, z3).setColor(red, green, blue, alpha);
    }

    private static void clear(ClientLevel level) {
        ACTIVE_PLAYERS.clear();
        activeLevel = level;
    }
}
