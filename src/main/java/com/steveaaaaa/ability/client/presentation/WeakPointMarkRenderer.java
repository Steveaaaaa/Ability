package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class WeakPointMarkRenderer {
    private static final ResourceLocation[] WOUNDS = {
            AbilityMod.id("textures/particle/weak_point_wound_sword.png"),
            AbilityMod.id("textures/particle/weak_point_wound_axe.png"),
            AbilityMod.id("textures/particle/weak_point_wound_puncture.png"),
            AbilityMod.id("textures/particle/weak_point_wound_blunt.png")
    };
    private static final ResourceLocation BLOOD_POOL =
            AbilityMod.id("textures/particle/weak_point_blood_pool.png");
    private static final Map<MarkKey, MarkVisual> MARKS = new HashMap<>();
    private static final List<BloodDecal> BLOOD_DECALS = new ArrayList<>();
    private static ClientLevel activeLevel;

    private WeakPointMarkRenderer() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(AbilityMod.id("weak_point"))) return;
        if (activeLevel != level) clear(level);
        MarkKey key = new MarkKey(cue.sourceEntityId(), cue.targetEntityId(), cue.instanceId());
        if (cue.cueId().equals(AbilityMod.id("marks"))) {
            if (cue.action() == AbilityCue.Action.STOP) {
                MARKS.remove(key);
            } else if (cue.action() == AbilityCue.Action.START) {
                int count = Mth.clamp(cue.rank(), 1, 32);
                int newStyle = weaponStyle(cue.randomSeed());
                MarkVisual previous = MARKS.get(key);
                ArrayList<Integer> styles = new ArrayList<>();
                if (previous != null) styles.addAll(previous.styles());
                if (styles.size() > count) styles.subList(count, styles.size()).clear();
                while (styles.size() < count) styles.add(newStyle);
                long duration = cue.durationTicks() == AbilityCue.USE_DEFINITION_DURATION
                        ? AbilityCue.MAX_DURATION_TICKS : cue.durationTicks();
                MARKS.put(key, new MarkVisual(cue, List.copyOf(styles),
                        level.getGameTime() + Math.max(1L, duration), level.getGameTime()));
            }
            return;
        }
        if (cue.action() == AbilityCue.Action.PULSE && cue.cueId().equals(AbilityMod.id("trigger"))) {
            MARKS.remove(key);
            createBloodPools(level, cue);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || activeLevel != level) {
            clear(level);
            return;
        }
        long time = level.getGameTime();
        MARKS.entrySet().removeIf(entry -> time >= entry.getValue().expiresAt()
                || missing(level, entry.getValue().cue().targetEntityId()));
        BLOOD_DECALS.removeIf(decal -> time >= decal.expiresAt());
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES
                || MARKS.isEmpty() && BLOOD_DECALS.isEmpty()) return;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || level != activeLevel) return;
        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        double visualTime = level.getGameTime() + partialTick;
        Vec3 cameraPosition = event.getCamera().getPosition();
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        Set<RenderType> usedTypes = new HashSet<>();
        for (MarkVisual mark : MARKS.values()) {
            renderWounds(level, mark, visualTime, partialTick, cameraPosition, poseStack, buffers, usedTypes);
        }
        for (BloodDecal decal : BLOOD_DECALS) {
            renderBloodPool(level, decal, visualTime, cameraPosition, poseStack, buffers, usedTypes);
        }
        usedTypes.forEach(buffers::endBatch);
    }

    private static void renderWounds(ClientLevel level, MarkVisual visual, double visualTime, float partialTick,
            Vec3 cameraPosition, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
            Set<RenderType> usedTypes) {
        Entity target = level.getEntity(visual.cue().targetEntityId());
        if (target == null) return;
        double x = Mth.lerp(partialTick, target.xo, target.getX());
        double y = Mth.lerp(partialTick, target.yo, target.getY());
        double z = Mth.lerp(partialTick, target.zo, target.getZ());
        float yawDegrees = target instanceof LivingEntity living
                ? Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot) : target.getYRot();
        double yaw = yawDegrees * Mth.DEG_TO_RAD;
        Vec3 front = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        double halfWidth = Math.max(0.1D, target.getBbWidth() * 0.5D);
        double height = Math.max(0.25D, target.getBbHeight());
        long seed = visual.cue().randomSeed();
        for (int index = 0; index < visual.styles().size(); index++) {
            int side = Math.floorMod(index + (int) (seed >>> 5), 4);
            Vec3 normal = switch (side) {
                case 1 -> right;
                case 2 -> front.scale(-1.0D);
                case 3 -> right.scale(-1.0D);
                default -> front;
            };
            Vec3 tangent = new Vec3(normal.z, 0.0D, -normal.x);
            double verticalFraction = 0.34D + Math.floorMod(seed + index * 37L, 47L) / 100.0D;
            double horizontal = (Math.floorMod(seed >>> (index % 12), 101L) / 100.0D - 0.5D)
                    * halfWidth * 0.72D;
            Vec3 center = new Vec3(x, y + height * verticalFraction, z)
                    .add(normal.scale(halfWidth + 0.018D)).add(tangent.scale(horizontal));
            int style = visual.styles().get(index);
            float baseSize = switch (style) {
                case 1 -> 0.42F;
                case 2 -> 0.28F;
                case 3 -> 0.32F;
                default -> 0.37F;
            };
            float size = Mth.clamp(baseSize * Math.max(0.72F, target.getBbWidth()), 0.17F, 0.58F);
            double highlightAge = visualTime - visual.lastChangedAt();
            if (index == visual.styles().size() - 1 && highlightAge < 7.0D) {
                size *= 1.0F + Mth.sin((float) (highlightAge / 7.0D * Math.PI)) * 0.18F;
            }
            float rotation = (Math.floorMod(seed + index * 19L, 5L) - 2L) * 0.12F;
            ResourceLocation texture = WOUNDS[Mth.clamp(style, 0, WOUNDS.length - 1)];
            RenderType type = RenderType.entityTranslucent(texture);
            renderSurfaceQuad(poseStack, buffers.getBuffer(type), center.subtract(cameraPosition),
                    tangent, new Vec3(0.0D, 1.0D, 0.0D), normal, size, rotation, 255,
                    LevelRenderer.getLightColor(level, target.blockPosition()));
            usedTypes.add(type);
        }
    }

    private static void renderBloodPool(ClientLevel level, BloodDecal decal, double visualTime,
            Vec3 cameraPosition, PoseStack poseStack, MultiBufferSource.BufferSource buffers,
            Set<RenderType> usedTypes) {
        if (visualTime < decal.appearsAt()) return;
        double remaining = decal.expiresAt() - visualTime;
        int alpha = remaining >= 20.0D ? 225 : Mth.clamp((int) (remaining / 20.0D * 225.0D), 0, 225);
        RenderType type = RenderType.entityTranslucent(BLOOD_POOL);
        Vec3 right = new Vec3(Math.cos(decal.rotation()), 0.0D, Math.sin(decal.rotation()));
        Vec3 forward = new Vec3(-Math.sin(decal.rotation()), 0.0D, Math.cos(decal.rotation()));
        renderSurfaceQuad(poseStack, buffers.getBuffer(type), decal.position().subtract(cameraPosition),
                right, forward, new Vec3(0.0D, 1.0D, 0.0D), decal.size(), 0.0F, alpha,
                LevelRenderer.getLightColor(level, BlockPos.containing(decal.position())));
        usedTypes.add(type);
    }

    private static void renderSurfaceQuad(PoseStack poseStack, VertexConsumer vertices, Vec3 center,
            Vec3 horizontal, Vec3 vertical, Vec3 normal, float size, float rotation, int alpha, int light) {
        float cos = Mth.cos(rotation);
        float sin = Mth.sin(rotation);
        Vec3 axisX = horizontal.scale(cos).add(vertical.scale(sin)).scale(size * 0.5D);
        Vec3 axisY = vertical.scale(cos).subtract(horizontal.scale(sin)).scale(size * 0.5D);
        poseStack.pushPose();
        poseStack.translate(center.x, center.y, center.z);
        vertex(poseStack, vertices, axisX.scale(-1).add(axisY), 0.0F, 0.0F, normal, alpha, light);
        vertex(poseStack, vertices, axisX.add(axisY), 1.0F, 0.0F, normal, alpha, light);
        vertex(poseStack, vertices, axisX.subtract(axisY), 1.0F, 1.0F, normal, alpha, light);
        vertex(poseStack, vertices, axisX.scale(-1).subtract(axisY), 0.0F, 1.0F, normal, alpha, light);
        poseStack.popPose();
    }

    private static void vertex(PoseStack poseStack, VertexConsumer vertices, Vec3 point,
            float u, float v, Vec3 normal, int alpha, int light) {
        vertices.addVertex(poseStack.last(), (float) point.x, (float) point.y, (float) point.z)
                .setColor(255, 255, 255, alpha).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light).setNormal(poseStack.last(), (float) normal.x, (float) normal.y, (float) normal.z);
    }

    private static void createBloodPools(ClientLevel level, AbilityCue cue) {
        RandomSource random = RandomSource.create(cue.randomSeed() ^ 0x5EEDB100DL);
        for (int index = 0; index < 15; index++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double radius = 0.25D + Math.sqrt(random.nextDouble()) * 2.35D;
            double x = cue.position().x + Math.cos(angle) * radius;
            double z = cue.position().z + Math.sin(angle) * radius;
            Vec3 surface = findGround(level, x, cue.position().y, z);
            if (surface == null) continue;
            long delay = 3L + random.nextInt(11);
            BLOOD_DECALS.add(new BloodDecal(surface, random.nextFloat() * Mth.TWO_PI,
                    0.32F + random.nextFloat() * 0.6F, level.getGameTime() + delay,
                    level.getGameTime() + delay + 100L));
        }
    }

    private static Vec3 findGround(ClientLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        for (int blockY = Mth.floor(y) + 2; blockY >= Mth.floor(y) - 6; blockY--) {
            cursor.set(blockX, blockY, blockZ);
            if (!level.hasChunkAt(cursor)) continue;
            BlockState state = level.getBlockState(cursor);
            BlockPos above = cursor.above();
            if (!state.getCollisionShape(level, cursor).isEmpty() && state.getFluidState().isEmpty()
                    && level.getBlockState(above).getCollisionShape(level, above).isEmpty()) {
                double top = state.getCollisionShape(level, cursor).max(net.minecraft.core.Direction.Axis.Y);
                return new Vec3(x, blockY + top + 0.012D, z);
            }
        }
        return null;
    }

    private static int weaponStyle(long seed) {
        return (int) (seed & 3L);
    }

    private static boolean missing(ClientLevel level, int entityId) {
        Entity entity = level.getEntity(entityId);
        return entity == null || entity.isRemoved();
    }

    private static void clear(ClientLevel level) {
        MARKS.clear();
        BLOOD_DECALS.clear();
        activeLevel = level;
    }

    private record MarkKey(int sourceEntityId, int targetEntityId, long instanceId) {
    }

    private record MarkVisual(AbilityCue cue, List<Integer> styles, long expiresAt, long lastChangedAt) {
    }

    private record BloodDecal(Vec3 position, float rotation, float size, long appearsAt, long expiresAt) {
    }
}
