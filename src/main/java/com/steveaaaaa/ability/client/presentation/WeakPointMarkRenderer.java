package com.steveaaaaa.ability.client.presentation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
import com.steveaaaaa.ability.registry.ModParticles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class WeakPointMarkRenderer {
    private static final ResourceLocation[] WOUNDS = {
            AbilityMod.id("textures/particle/weak_point_wound_sword.png"),
            AbilityMod.id("textures/particle/weak_point_wound_axe.png"),
            AbilityMod.id("textures/particle/weak_point_wound_puncture.png"),
            AbilityMod.id("textures/particle/weak_point_wound_blunt.png")
    };
    private static final Map<MarkKey, MarkVisual> MARKS = new HashMap<>();
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
                ArrayList<Wound> wounds = new ArrayList<>();
                if (previous != null) wounds.addAll(previous.wounds());
                if (wounds.size() > count) wounds.subList(count, wounds.size()).clear();
                while (wounds.size() < count) wounds.add(createWound(level, cue, newStyle, wounds.size()));
                long duration = cue.durationTicks() == AbilityCue.USE_DEFINITION_DURATION
                        ? AbilityCue.MAX_DURATION_TICKS : cue.durationTicks();
                MARKS.put(key, new MarkVisual(cue, List.copyOf(wounds),
                        level.getGameTime() + Math.max(1L, duration), level.getGameTime()));
            }
            return;
        }
        if (cue.action() == AbilityCue.Action.PULSE && cue.cueId().equals(AbilityMod.id("trigger"))) {
            MARKS.remove(key);
            emitLingeringBlood(level, cue);
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
    }

    static <T extends LivingEntity> void renderWounds(EntityModel<T> model, PoseStack poseStack,
            MultiBufferSource buffers, int packedLight, T target, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level != activeLevel || target.isInvisible()) return;
        List<MarkVisual> visuals = MARKS.values().stream()
                .filter(mark -> mark.cue().targetEntityId() == target.getId()).toList();
        if (visuals.isEmpty()) return;

        double visualTime = level.getGameTime() + partialTick;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        Vec3 origin = new Vec3(
                Mth.lerp(partialTick, target.xo, target.getX()),
                Mth.lerp(partialTick, target.yo, target.getY()),
                Mth.lerp(partialTick, target.zo, target.getZ())
        ).subtract(camera);
        float bodyYaw = Mth.rotLerp(partialTick, target.yBodyRotO, target.yBodyRot) * Mth.DEG_TO_RAD;
        double height = Math.max(0.25D, target.getBbHeight());
        double width = Math.max(0.2D, target.getBbWidth());

        for (MarkVisual visual : visuals) {
            for (int index = 0; index < visual.wounds().size(); index++) {
                Wound wound = visual.wounds().get(index);
                double angle = bodyYaw + wound.localAngle();
                Vec3 outward = new Vec3(-Math.sin(angle), 0.0D, Math.cos(angle));
                Vec3 tangent = new Vec3(outward.z, 0.0D, -outward.x);
                Vec3 center = origin.add(0.0D, height * wound.verticalFraction(), 0.0D)
                        .add(tangent.scale(width * wound.horizontalFraction()));
                float baseSize = switch (wound.style()) {
                    case 1 -> 0.43F;
                    case 2 -> 0.29F;
                    case 3 -> 0.33F;
                    default -> 0.38F;
                };
                float size = Mth.clamp(baseSize * Math.max(0.72F, target.getBbWidth()), 0.17F, 0.6F);
                double age = visualTime - visual.lastChangedAt();
                if (index == visual.wounds().size() - 1 && age < 7.0D) {
                    size *= 1.0F + Mth.sin((float) (age / 7.0D * Math.PI)) * 0.13F;
                }
                VertexConsumer base = buffers.getBuffer(
                        RenderType.entityTranslucent(WOUNDS[Mth.clamp(wound.style(), 0, WOUNDS.length - 1)]));
                VertexConsumer projected = new ProjectedWoundConsumer(base, center, tangent,
                        new Vec3(0.0D, 1.0D, 0.0D), outward, size);
                model.renderToBuffer(poseStack, projected, packedLight, OverlayTexture.NO_OVERLAY, -1);
            }
        }
    }

    private static Wound createWound(ClientLevel level, AbilityCue cue, int style, int index) {
        Entity entity = level.getEntity(cue.targetEntityId());
        float bodyYaw = entity instanceof LivingEntity living ? living.yBodyRot * Mth.DEG_TO_RAD : 0.0F;
        Vec3 direction = cue.direction();
        double worldAngle = direction.horizontalDistanceSqr() > 1.0E-6D
                ? Math.atan2(-direction.x, direction.z) : bodyYaw;
        float localAngle = Mth.wrapDegrees((float) ((worldAngle - bodyYaw) * Mth.RAD_TO_DEG)) * Mth.DEG_TO_RAD;
        long seed = cue.randomSeed() + index * 0x9E3779B97F4A7C15L;
        float vertical = 0.32F + Math.floorMod(seed >>> 11, 49L) / 100.0F;
        float horizontal = (Math.floorMod(seed >>> 23, 101L) / 100.0F - 0.5F) * 0.5F;
        return new Wound(style, localAngle, vertical, horizontal);
    }

    private static void emitLingeringBlood(ClientLevel level, AbilityCue cue) {
        RandomSource random = RandomSource.create(cue.randomSeed() ^ 0xB100D5EEDL);
        Vec3 direction = cue.direction().normalize();
        for (int index = 0; index < 220; index++) {
            double speed = 0.035D + random.nextDouble() * 0.17D;
            level.addParticle(ModParticles.WEAK_POINT_BLOOD_DROP.get(),
                    cue.position().x + random.nextGaussian() * 0.14D,
                    cue.position().y + random.nextGaussian() * 0.18D,
                    cue.position().z + random.nextGaussian() * 0.14D,
                    direction.x * speed + random.nextGaussian() * 0.105D,
                    0.035D + random.nextDouble() * 0.26D,
                    direction.z * speed + random.nextGaussian() * 0.105D);
        }
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
        activeLevel = level;
    }

    private static final class ProjectedWoundConsumer implements VertexConsumer {
        private final VertexConsumer delegate;
        private final Vec3 center;
        private final Vec3 horizontal;
        private final Vec3 vertical;
        private final Vec3 outward;
        private final float size;
        private float x;
        private float y;
        private float z;

        private ProjectedWoundConsumer(VertexConsumer delegate, Vec3 center, Vec3 horizontal,
                Vec3 vertical, Vec3 outward, float size) {
            this.delegate = delegate;
            this.center = center;
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.outward = outward;
            this.size = size;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            delegate.setColor(255, 255, 255, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            Vec3 relative = new Vec3(x, y, z).subtract(center);
            delegate.setUv(0.5F + (float) (relative.dot(horizontal) / size),
                    0.5F - (float) (relative.dot(vertical) / size));
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            if (new Vec3(x, y, z).dot(outward) < 0.2D) delegate.setColor(255, 255, 255, 0);
            delegate.setNormal(x, y, z);
            return this;
        }
    }

    private record MarkKey(int sourceEntityId, int targetEntityId, long instanceId) {
    }

    private record MarkVisual(AbilityCue cue, List<Wound> wounds, long expiresAt, long lastChangedAt) {
    }

    private record Wound(int style, float localAngle, float verticalFraction, float horizontalFraction) {
    }
}
