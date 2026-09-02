package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.network.ClientProgressCache;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class GreedPresentation {
    private static final ResourceLocation ABILITY = AbilityMod.id("greed");
    private static final double EXTRA_RANGE = 3.0D;
    private static final List<ToolTier> TOOL_TIERS = List.of(
            new ToolTier(1, AbilityMod.id("greed_axes")),
            new ToolTier(2, AbilityMod.id("greed_shears")),
            new ToolTier(3, AbilityMod.id("greed_hoes")),
            new ToolTier(4, AbilityMod.id("greed_shovels")),
            new ToolTier(5, AbilityMod.id("greed_pickaxes"))
    );
    private static long lastBurstTick = Long.MIN_VALUE;
    private static BlockPos lastBurstPos = BlockPos.ZERO;

    private GreedPresentation() {
    }

    @SubscribeEvent
    public static void renderExtendedHighlight(RenderHighlightEvent.Block event) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null || !qualifies(player)) {
            return;
        }
        Vec3 hit = event.getTarget().getLocation();
        if (!isBeyondNormalReach(player, hit)) {
            return;
        }
        BlockPos pos = event.getTarget().getBlockPos();
        BlockState state = minecraft.level.getBlockState(pos);
        VoxelShape shape = state.getShape(minecraft.level, pos, CollisionContext.of(player));
        if (shape.isEmpty()) {
            return;
        }

        event.setCanceled(true);
        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float pulse = 0.72F + Mth.sin((minecraft.level.getGameTime() + partialTick) * 0.14F) * 0.18F;
        Vec3 camera = event.getCamera().getPosition();
        double x = pos.getX() - camera.x;
        double y = pos.getY() - camera.y;
        double z = pos.getZ() - camera.z;
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        LevelRenderer.renderVoxelShape(event.getPoseStack(), lines, shape, x, y, z,
                0.92F, 0.61F, 0.16F, pulse, false);
        renderCornerMarks(event.getPoseStack(), lines, shape.bounds().move(x, y, z), pulse);
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getAction() == PlayerInteractEvent.LeftClickBlock.Action.START) {
            emitPullBurst(event.getEntity(), event.getPos(), Vec3.atCenterOf(event.getPos()));
        }
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        emitPullBurst(event.getEntity(), event.getPos(), event.getHitVec().getLocation());
    }

    private static void emitPullBurst(Player player, BlockPos pos, Vec3 start) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player != minecraft.player || !(player.level() instanceof ClientLevel level)
                || !qualifies(player) || !isBeyondNormalReach(player, start)
                || (lastBurstTick == level.getGameTime() && lastBurstPos.equals(pos))) {
            return;
        }
        lastBurstTick = level.getGameTime();
        lastBurstPos = pos.immutable();
        Vec3 hand = player.getEyePosition().add(player.getLookAngle().scale(0.28D)).add(0.0D, -0.34D, 0.0D);
        Vec3 travel = hand.subtract(start);
        Vec3 velocity = travel.lengthSqr() < 1.0E-6D ? Vec3.ZERO : travel.normalize().scale(0.075D);
        for (int index = 0; index < 3; index++) {
            double progress = index * 0.055D;
            SupportAuraParticle.addMote(level,
                    start.x + velocity.x * progress,
                    start.y + velocity.y * progress,
                    start.z + velocity.z * progress,
                    velocity.x, velocity.y, velocity.z,
                    0.92F, 0.62F, 0.18F);
        }
    }

    private static boolean qualifies(Player player) {
        int rank = ClientProgressCache.snapshot().abilityRank(ABILITY);
        return rank > 0 && TOOL_TIERS.stream().anyMatch(tier ->
                tier.minimumRank() <= rank
                        && player.getMainHandItem().is(TagKey.create(Registries.ITEM, tier.itemTag())));
    }

    private static boolean isBeyondNormalReach(Player player, Vec3 target) {
        double normalReach = Math.max(0.0D, player.blockInteractionRange() - EXTRA_RANGE);
        double distance = player.getEyePosition().distanceTo(target);
        return distance > normalReach + 0.05D && distance <= player.blockInteractionRange() + 0.15D;
    }

    private static void renderCornerMarks(PoseStack poseStack, VertexConsumer lines, AABB box, float alpha) {
        double length = Math.min(0.18D, Math.min(box.getXsize(), Math.min(box.getYsize(), box.getZsize())) * 0.24D);
        for (int xi = 0; xi < 2; xi++) {
            for (int yi = 0; yi < 2; yi++) {
                for (int zi = 0; zi < 2; zi++) {
                    double x = xi == 0 ? box.minX : box.maxX;
                    double y = yi == 0 ? box.minY : box.maxY;
                    double z = zi == 0 ? box.minZ : box.maxZ;
                    line(poseStack, lines, x, y, z, x + (xi == 0 ? length : -length), y, z, alpha);
                    line(poseStack, lines, x, y, z, x, y + (yi == 0 ? length : -length), z, alpha);
                    line(poseStack, lines, x, y, z, x, y, z + (zi == 0 ? length : -length), alpha);
                }
            }
        }
    }

    private static void line(PoseStack poseStack, VertexConsumer lines,
            double x0, double y0, double z0, double x1, double y1, double z1, float alpha) {
        float nx = (float) (x1 - x0);
        float ny = (float) (y1 - y0);
        float nz = (float) (z1 - z0);
        lines.addVertex(poseStack.last(), (float) x0, (float) y0, (float) z0)
                .setColor(255, 194, 62, (int) (alpha * 255.0F)).setNormal(poseStack.last(), nx, ny, nz);
        lines.addVertex(poseStack.last(), (float) x1, (float) y1, (float) z1)
                .setColor(255, 194, 62, (int) (alpha * 255.0F)).setNormal(poseStack.last(), nx, ny, nz);
    }

    private record ToolTier(int minimumRank, ResourceLocation itemTag) {
    }
}
