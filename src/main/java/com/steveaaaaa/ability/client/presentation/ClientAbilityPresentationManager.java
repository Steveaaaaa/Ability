package com.steveaaaaa.ability.client.presentation;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.network.ClientAbilityCueQueue;
import com.steveaaaaa.ability.network.ClientboundAbilityCuePayload;
import com.steveaaaaa.ability.presentation.AbilityCue;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class ClientAbilityPresentationManager {
    private static final Map<InstanceKey, ActiveCue> ACTIVE = new HashMap<>();
    private static final Set<String> REPORTED_MISSING = new HashSet<>();

    private ClientAbilityPresentationManager() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            ClientAbilityCueQueue.clear();
            clearActive();
            return;
        }

        ClientboundAbilityCuePayload payload;
        while ((payload = ClientAbilityCueQueue.poll()) != null) {
            accept(level, payload.cue());
        }

        long gameTime = level.getGameTime();
        ACTIVE.entrySet().removeIf(entry -> {
            ActiveCue active = entry.getValue();
            if (gameTime >= active.expiresAt()) {
                return true;
            }
            if (gameTime >= active.nextEmissionAt()) {
                emit(level, active.cue(), active.definition(), false, gameTime);
                entry.setValue(new ActiveCue(
                        active.cue(),
                        active.definition(),
                        active.startedAt(),
                        active.expiresAt(),
                        gameTime + active.definition().emissionIntervalTicks()
                ));
            }
            return false;
        });
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientAbilityCueQueue.clear();
        clearActive();
    }

    static void clearActive() {
        ACTIVE.clear();
        REPORTED_MISSING.clear();
    }

    private static void accept(ClientLevel level, AbilityCue cue) {
        ChargedLeapImpactRenderer.accept(level, cue);
        WeakPointMarkRenderer.accept(level, cue);
        EnchantedEdgeWeaponRenderer.accept(level, cue);
        WellPreparedSalvationRenderer.accept(level, cue);
        SurvivalCleanseParticle.accept(level, cue);
        SurvivorShieldRippleRenderer.accept(level, cue);
        EnergeticPresentation.accept(level, cue);
        FrugalityHud.accept(level, cue);
        InstanceKey key = InstanceKey.of(cue);
        if (cue.action() == AbilityCue.Action.STOP) {
            ACTIVE.remove(key);
            return;
        }

        AbilityPresentationDefinition.CueDefinition definition =
                AbilityPresentationResources.find(cue.abilityId(), cue.cueId());
        if (definition == null) {
            String missing = cue.abilityId() + "/" + cue.cueId();
            if (REPORTED_MISSING.add(missing)) {
                AbilityMod.LOGGER.warn("No client presentation definition for {}", missing);
            }
            return;
        }

        long gameTime = level.getGameTime();
        ActiveCue previous = ACTIVE.get(key);
        if (cue.action() != AbilityCue.Action.START || previous == null) {
            emit(level, cue, definition, true, gameTime);
        }
        int durationTicks = cue.durationTicks() == AbilityCue.USE_DEFINITION_DURATION
                ? definition.durationTicks()
                : cue.durationTicks();
        if (cue.action() == AbilityCue.Action.START && durationTicks > 0) {
            ACTIVE.put(key, new ActiveCue(
                    cue,
                    definition,
                    previous == null ? gameTime : previous.startedAt(),
                    gameTime + durationTicks,
                    previous == null
                            ? gameTime + definition.emissionIntervalTicks()
                            : previous.nextEmissionAt()
            ));
        }
    }

    private static void emit(
            ClientLevel level,
            AbilityCue cue,
            AbilityPresentationDefinition.CueDefinition definition,
            boolean initial,
            long gameTime
    ) {
        RandomSource random = RandomSource.create(cue.randomSeed() ^ gameTime);
        for (AbilityPresentationDefinition.ParticleBurst particle : definition.particles()) {
            emitParticles(level, cue, particle, random);
        }
        if (!initial) {
            return;
        }
        definition.sound().ifPresent(sound -> playSound(level, cue, sound, random));
        definition.animation().ifPresent(animation -> playAnimation(level, cue, animation));
    }

    private static void emitParticles(
            ClientLevel level,
            AbilityCue cue,
            AbilityPresentationDefinition.ParticleBurst definition,
            RandomSource random
    ) {
        ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.getOptional(definition.type()).orElse(null);
        if (!(type instanceof SimpleParticleType particle)) {
            reportMissing("particle:" + definition.type(), "Missing or non-simple particle type {}", definition.type());
            return;
        }
        Vec3 anchor = anchor(level, cue, definition.anchor()).add(definition.offset());
        Vec3 direction = cue.direction().lengthSqr() < 1.0E-8D ? Vec3.ZERO : cue.direction().normalize();
        Vec3 directedVelocity = direction.scale(definition.directionSpeed()).add(definition.velocity());
        for (int index = 0; index < definition.count(); index++) {
            Vec3 position = anchor.add(randomized(definition.spread(), random));
            level.addParticle(
                    particle,
                    definition.force(),
                    position.x,
                    position.y,
                    position.z,
                    directedVelocity.x,
                    directedVelocity.y,
                    directedVelocity.z
            );
        }
    }

    private static void playSound(
            ClientLevel level,
            AbilityCue cue,
            AbilityPresentationDefinition.SoundCue definition,
            RandomSource random
    ) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.getOptional(definition.event()).orElse(null);
        if (sound == null) {
            reportMissing("sound:" + definition.event(), "Missing sound event {}", definition.event());
            return;
        }
        Vec3 position = anchor(level, cue, definition.anchor());
        float pitch = Math.max(0.01F, definition.pitch()
                + (random.nextFloat() * 2.0F - 1.0F) * definition.pitchRandom());
        level.playLocalSound(
                position.x,
                position.y,
                position.z,
                sound,
                SoundSource.PLAYERS,
                definition.volume(),
                pitch,
                false
        );
    }

    private static void playAnimation(ClientLevel level, AbilityCue cue, ResourceLocation animation) {
        Entity entity = level.getEntity(cue.sourceEntityId());
        if (entity instanceof AbstractClientPlayer player
                && PlayerAnimationAccess.getPlayerAnimationLayer(player, PresentationAnimationSetup.LAYER)
                instanceof PlayerAnimationController controller) {
            controller.triggerAnimation(animation);
        }
    }

    private static Vec3 anchor(
            ClientLevel level,
            AbilityCue cue,
            AbilityPresentationDefinition.Anchor anchor
    ) {
        if (anchor == AbilityPresentationDefinition.Anchor.POSITION) {
            return cue.position();
        }
        int entityId = anchor == AbilityPresentationDefinition.Anchor.SOURCE
                ? cue.sourceEntityId()
                : cue.targetEntityId();
        Entity entity = level.getEntity(entityId);
        return entity == null
                ? cue.position()
                : entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
    }

    private static Vec3 randomized(Vec3 spread, RandomSource random) {
        return new Vec3(
                (random.nextDouble() * 2.0D - 1.0D) * spread.x,
                (random.nextDouble() * 2.0D - 1.0D) * spread.y,
                (random.nextDouble() * 2.0D - 1.0D) * spread.z
        );
    }

    private static void reportMissing(String key, String message, Object value) {
        if (REPORTED_MISSING.add(key)) {
            AbilityMod.LOGGER.warn(message, value);
        }
    }

    static List<ActivePresentation> activePresentations() {
        return ACTIVE.values().stream()
                .filter(active -> !active.definition().orbitingSprites().isEmpty())
                .map(active -> new ActivePresentation(
                        active.cue(), active.definition().orbitingSprites(), active.startedAt(), active.expiresAt()
                ))
                .toList();
    }

    private record InstanceKey(
            ResourceLocation abilityId,
            ResourceLocation cueId,
            int sourceEntityId,
            int targetEntityId,
            long instanceId
    ) {
        private static InstanceKey of(AbilityCue cue) {
            return new InstanceKey(
                    cue.abilityId(), cue.cueId(), cue.sourceEntityId(), cue.targetEntityId(), cue.instanceId()
            );
        }
    }

    private record ActiveCue(
            AbilityCue cue,
            AbilityPresentationDefinition.CueDefinition definition,
            long startedAt,
            long expiresAt,
            long nextEmissionAt
    ) {
    }

    record ActivePresentation(
            AbilityCue cue,
            List<AbilityPresentationDefinition.OrbitingSprite> orbitingSprites,
            long startedAt,
            long expiresAt
    ) {
    }
}
