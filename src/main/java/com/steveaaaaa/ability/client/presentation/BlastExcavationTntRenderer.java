package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.network.ClientProgressCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Adds render-only foil to a qualifying offhand TNT stack without changing its item data. */
public final class BlastExcavationTntRenderer {
    private static final ThreadLocal<Boolean> HELD_OFFHAND_TNT = ThreadLocal.withInitial(() -> false);

    private BlastExcavationTntRenderer() {
    }

    public static void beginHeldItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext) {
        HELD_OFFHAND_TNT.set(entity instanceof AbstractClientPlayer player
                && isHeldContext(displayContext)
                && stack == player.getOffhandItem()
                && qualifies(player, stack));
    }

    public static void endHeldItem() {
        HELD_OFFHAND_TNT.remove();
    }

    public static void renderCurrentItem(ItemRenderer itemRenderer, PoseStack poseStack,
            MultiBufferSource buffers, ItemStack stack, BakedModel model, int overlay) {
        if (model.isCustomRenderer() || (!HELD_OFFHAND_TNT.get() && !isLocalOffhandStack(stack))) {
            return;
        }
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            itemRenderer.renderModelLists(pass, stack, 0x00F000F0, overlay, poseStack,
                    buffers.getBuffer(RenderType.glint()));
        }
    }

    private static boolean isLocalOffhandStack(ItemStack stack) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.player != null
                && stack == minecraft.player.getOffhandItem()
                && qualifies(minecraft.player, stack);
    }

    private static boolean qualifies(AbstractClientPlayer player, ItemStack stack) {
        return stack.is(Items.TNT)
                && player.getMainHandItem().is(ItemTags.PICKAXES)
                && ClientProgressCache.snapshot().abilityRank(AbilityMod.id("blast_excavation")) > 0;
    }

    private static boolean isHeldContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }
}
