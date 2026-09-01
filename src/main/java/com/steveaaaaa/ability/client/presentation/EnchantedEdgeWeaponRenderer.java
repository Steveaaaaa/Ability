package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.effect.EnchantedEdgeEffect;
import com.steveaaaaa.ability.presentation.AbilityCue;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class EnchantedEdgeWeaponRenderer {
    private static final TagKey<Item> COMMON_SPEARS = commonTag("tools/spears");
    private static final TagKey<Item> COMMON_HAMMERS = commonTag("tools/hammers");
    private static final TagKey<Item> COMMON_MACES = commonTag("tools/maces");
    private static final Map<Integer, Long> ACTIVE_PLAYERS = new HashMap<>();
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
        CURRENT_ITEM.set(new RenderContext(weaponShape(stack)));
    }

    public static void endHeldItem() {
        CURRENT_ITEM.remove();
    }

    public static void renderCurrentItem(PoseStack poseStack, MultiBufferSource buffers) {
        RenderContext context = CURRENT_ITEM.get();
        if (context == null) return;
        double time = System.nanoTime() / 50_000_000.0D;
        VertexConsumer vertices = buffers.getBuffer(RenderType.lightning());
        if (context.shape() == WeaponShape.AXE_HEAD) {
            renderHeadEnvelope(poseStack, vertices, time, new Vec3(-0.2D, 0.2D, 0.0D), 0.22D, 0.16D);
        } else if (context.shape() == WeaponShape.HEAVY_HEAD) {
            renderHeadEnvelope(poseStack, vertices, time, new Vec3(0.0D, 0.27D, 0.0D), 0.21D, 0.14D);
        } else {
            renderBladeRings(poseStack, vertices, time, context.shape() == WeaponShape.SPEAR);
        }
    }

    private static void renderBladeRings(PoseStack poseStack, VertexConsumer vertices,
            double time, boolean narrow) {
        Vec3 axis = new Vec3(1.0D, 1.0D, 0.0D).normalize();
        Vec3 across = new Vec3(1.0D, -1.0D, 0.0D).normalize();
        Vec3 depth = new Vec3(0.0D, 0.0D, 1.0D);
        Vec3 center = narrow ? new Vec3(0.2D, 0.2D, 0.0D) : new Vec3(0.14D, 0.14D, 0.0D);
        double[] positions = narrow ? new double[]{-0.05D, 0.14D, 0.31D}
                : new double[]{-0.16D, 0.06D, 0.27D};
        for (int ring = 0; ring < positions.length; ring++) {
            double phase = time * (0.12D + ring * 0.017D) + ring * 1.85D;
            double radius = (narrow ? 0.075D : 0.095D) + ring * 0.006D;
            for (int point = 0; point < 10; point++) {
                double angle = phase + point * Mth.TWO_PI / 10.0D;
                Vec3 position = center.add(axis.scale(positions[ring]))
                        .add(across.scale(Math.cos(angle) * radius))
                        .add(depth.scale(Math.sin(angle) * radius));
                renderMote(poseStack, vertices, position, point, ring, angle, time);
            }
        }
    }

    private static void renderHeadEnvelope(PoseStack poseStack, VertexConsumer vertices, double time,
            Vec3 center, double horizontalRadius, double verticalRadius) {
        Vec3[][] planes = {
                {new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D)},
                {new Vec3(0.0D, 1.0D, 0.0D), new Vec3(0.0D, 0.0D, 1.0D)},
                {new Vec3(1.0D, 0.0D, 0.0D), new Vec3(0.0D, 1.0D, 0.0D)}
        };
        for (int ring = 0; ring < planes.length; ring++) {
            double phase = time * (0.105D + ring * 0.021D) + ring * 2.1D;
            for (int point = 0; point < 11; point++) {
                double angle = phase + point * Mth.TWO_PI / 11.0D;
                double firstRadius = ring == 2 ? horizontalRadius : horizontalRadius * 0.88D;
                double secondRadius = ring == 2 ? verticalRadius : verticalRadius * 0.78D;
                Vec3 position = center.add(planes[ring][0].scale(Math.cos(angle) * firstRadius))
                        .add(planes[ring][1].scale(Math.sin(angle) * secondRadius));
                renderMote(poseStack, vertices, position, point, ring, angle, time);
            }
        }
    }

    private static void renderMote(PoseStack poseStack, VertexConsumer vertices, Vec3 position,
            int point, int ring, double angle, double time) {
        float size = 0.012F + (point % 3) * 0.0035F;
        int alpha = 125 + (int) ((Math.sin(angle + time * 0.18D) * 0.5D + 0.5D) * 100.0D);
        renderPixelCube(poseStack, vertices, (float) position.x, (float) position.y, (float) position.z,
                size, 202 + ring * 10, 154 + ring * 12, 255, alpha);
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

    private static WeaponShape weaponShape(ItemStack stack) {
        if (stack.is(ItemTags.AXES)) return WeaponShape.AXE_HEAD;
        if (stack.is(ItemTags.MACE_ENCHANTABLE)
                || stack.is(COMMON_HAMMERS) || stack.is(COMMON_MACES)) return WeaponShape.HEAVY_HEAD;
        if (stack.is(ItemTags.TRIDENT_ENCHANTABLE) || stack.is(COMMON_SPEARS)) return WeaponShape.SPEAR;
        return WeaponShape.BLADE;
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

    private static TagKey<Item> commonTag(String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private static void clear(ClientLevel level) {
        ACTIVE_PLAYERS.clear();
        CURRENT_ITEM.remove();
        activeLevel = level;
    }

    private enum WeaponShape {
        BLADE,
        SPEAR,
        AXE_HEAD,
        HEAVY_HEAD
    }

    private record RenderContext(WeaponShape shape) {
    }
}
