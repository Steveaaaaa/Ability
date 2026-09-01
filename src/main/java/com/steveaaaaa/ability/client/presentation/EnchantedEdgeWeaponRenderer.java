package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.effect.EnchantedEdgeEffect;
import com.steveaaaaa.ability.presentation.AbilityCue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class EnchantedEdgeWeaponRenderer {
    private static final Map<Integer, Long> ACTIVE_PLAYERS = new HashMap<>();
    private static final Map<BakedModel, List<SurfacePoint>> SURFACE_CACHE = new WeakHashMap<>();
    private static final ThreadLocal<RenderContext> CURRENT_ITEM = new ThreadLocal<>();
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

    public static void beginHeldItem(LivingEntity entity, ItemStack stack, ItemDisplayContext displayContext) {
        CURRENT_ITEM.remove();
        if (!(entity instanceof AbstractClientPlayer player)
                || !isHeldContext(displayContext)
                || !isActive(player)
                || !EnchantedEdgeEffect.isWeapon(stack)) return;
        float partialTick = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        CURRENT_ITEM.set(new RenderContext(Mth.clamp(player.getAttackAnim(partialTick), 0.0F, 1.0F)));
    }

    public static void endHeldItem() {
        CURRENT_ITEM.remove();
    }

    public static void renderCurrentItem(ItemRenderer itemRenderer, PoseStack poseStack,
            MultiBufferSource buffers, ItemStack stack, BakedModel model, int overlay) {
        RenderContext context = CURRENT_ITEM.get();
        if (context == null || model.isCustomRenderer()) return;

        float time = (float) (System.nanoTime() / 1_000_000_000.0D);
        float attack = context.attackProgress();
        renderModelOverlay(itemRenderer, poseStack, buffers.getBuffer(RenderType.glint()), stack, model, overlay);
        renderSurfaceSparks(poseStack, buffers.getBuffer(RenderType.lightning()), model, stack, time, attack);
    }

    private static void renderModelOverlay(ItemRenderer itemRenderer, PoseStack poseStack,
            VertexConsumer target, ItemStack stack, BakedModel model, int overlay) {
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            itemRenderer.renderModelLists(pass, stack, LightTexture.FULL_BRIGHT, overlay, poseStack, target);
        }
    }

    private static void renderSurfaceSparks(PoseStack poseStack, VertexConsumer vertices,
            BakedModel model, ItemStack stack, float time, float attack) {
        List<SurfacePoint> surface = SURFACE_CACHE.computeIfAbsent(model,
                ignored -> collectSurfacePoints(model, stack));
        if (surface.isEmpty()) return;
        int count = 3 + (attack > 0.08F ? 4 : 0);
        int frame = Mth.floor(time * 8.0F);
        for (int i = 0; i < count; i++) {
            int index = Math.floorMod(frame * 7 + i * 13, surface.size());
            SurfacePoint point = surface.get(index);
            float flicker = 0.5F + 0.5F * Mth.sin(time * 9.0F + i * 2.1F);
            Vec3 position = point.position().add(point.normal().scale(0.010D + flicker * 0.008D));
            float size = 0.007F + flicker * 0.004F + attack * 0.003F;
            renderPixelCube(poseStack, vertices, (float) position.x, (float) position.y,
                    (float) position.z, size, 210, 164, 255, 105 + (int) (flicker * 105.0F));
            if (attack > 0.08F) {
                float trail = Mth.sin(attack * Mth.PI);
                Vec3 afterimage = position.add(-0.020D * trail, -0.014D * trail, 0.008D * trail);
                renderPixelCube(poseStack, vertices, (float) afterimage.x, (float) afterimage.y,
                        (float) afterimage.z, size * 0.75F, 184, 126, 246,
                        55 + (int) (trail * 85.0F));
            }
        }
    }

    private static List<SurfacePoint> collectSurfacePoints(BakedModel model, ItemStack stack) {
        List<SurfacePoint> result = new ArrayList<>();
        RandomSource random = RandomSource.create(42L);
        for (BakedModel pass : model.getRenderPasses(stack, true)) {
            random.setSeed(42L);
            addQuadPoints(result, pass.getQuads(null, null, random));
            for (Direction direction : Direction.values()) {
                random.setSeed(42L);
                addQuadPoints(result, pass.getQuads(null, direction, random));
            }
        }
        return result;
    }

    private static void addQuadPoints(List<SurfacePoint> result, List<BakedQuad> quads) {
        for (BakedQuad quad : quads) {
            int[] data = quad.getVertices();
            int stride = data.length / 4;
            Vec3 normal = Vec3.atLowerCornerOf(quad.getDirection().getNormal());
            for (int vertex = 0; vertex < 4; vertex++) {
                int offset = vertex * stride;
                result.add(new SurfacePoint(new Vec3(
                        Float.intBitsToFloat(data[offset]),
                        Float.intBitsToFloat(data[offset + 1]),
                        Float.intBitsToFloat(data[offset + 2])), normal));
            }
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

    private static boolean isHeldContext(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }

    private static boolean isActive(AbstractClientPlayer player) {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && ACTIVE_PLAYERS.getOrDefault(player.getId(), Long.MIN_VALUE) > level.getGameTime();
    }

    private static void clear(ClientLevel level) {
        ACTIVE_PLAYERS.clear();
        SURFACE_CACHE.clear();
        CURRENT_ITEM.remove();
        activeLevel = level;
    }

    private record RenderContext(float attackProgress) {
    }

    private record SurfacePoint(Vec3 position, Vec3 normal) {
    }

}
