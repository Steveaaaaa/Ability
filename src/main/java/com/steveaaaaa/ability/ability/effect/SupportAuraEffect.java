package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import com.steveaaaaa.ability.presentation.AbilityCue;
import com.steveaaaaa.ability.presentation.AbilityPresentationService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class SupportAuraEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("support_aura");
    private static final ResourceLocation ACTIVATE_CUE = AbilityMod.id("activate");
    private static final ResourceLocation LINK_CUE = AbilityMod.id("support_link");
    private static final ResourceLocation HEAL_PULSE_CUE = AbilityMod.id("heal_pulse");
    private static final ResourceLocation GOLDEN_SHIELD_CUE = AbilityMod.id("golden_shield");
    private static final Set<String> RANK_KEYS = Set.of("total_healing_percent");
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Map<ResourceLocation, HealingSession>> SESSIONS = new ConcurrentHashMap<>();

    private SupportAuraEffect() {
    }

    public static void activate(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player && activate(player, event.getItemStack())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    public static void activate(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && activate(player, event.getItemStack())) {
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
        }
    }

    public static void processTick(ServerPlayer player) {
        Map<ResourceLocation, HealingSession> sessions = SESSIONS.get(player.getUUID());
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        long now = player.level().getGameTime();
        LinkedHashMap<ResourceLocation, HealingSession> remaining = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, HealingSession> entry : sessions.entrySet()) {
            HealingSession session = entry.getValue();
            if (now < session.nextPulseAt()) {
                remaining.put(entry.getKey(), session);
                continue;
            }
            for (UUID targetId : session.targets()) {
                Entity entity = player.serverLevel().getEntity(targetId);
                if (entity instanceof LivingEntity living && living.isAlive()) {
                    living.heal(session.healingPerPulse());
                    AbilityPresentationService.sendTracking(living, AbilityCue.pulse(
                            session.abilityId(), HEAL_PULSE_CUE,
                            player.getId(), living.getId(), living.getBoundingBox().getCenter(),
                            Vec3.ZERO, session.rank(), player.getRandom().nextLong()
                    ));
                }
            }
            if (session.pulsesRemaining() > 1) {
                remaining.put(entry.getKey(), new HealingSession(
                        session.targets(),
                        session.healingPerPulse(),
                        now + session.pulseIntervalTicks(),
                        session.pulseIntervalTicks(),
                        session.pulsesRemaining() - 1,
                        session.abilityId(),
                        session.rank()
                ));
            }
        }
        if (remaining.isEmpty()) {
            SESSIONS.remove(player.getUUID());
        } else {
            SESSIONS.put(player.getUUID(), Map.copyOf(remaining));
        }
    }

    public static void forget(UUID playerId) {
        SESSIONS.remove(playerId);
    }

    static double healingPerPulse(double playerMaximumHealth, double totalPercent, int pulseCount) {
        if (pulseCount <= 0) {
            return 0.0D;
        }
        return Math.max(0.0D, playerMaximumHealth) * Math.max(0.0D, totalPercent) / 100.0D / pulseCount;
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> values = new LinkedHashMap<>(earlier.values());
        values.putAll(later.values());
        return new RankValues(values);
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        ArrayList<String> errors = new ArrayList<>();
        Config config;
        try {
            config = parse(Config.CODEC, definition.effect().config(), "effect.config");
            if (BuiltInRegistries.MOB_EFFECT.getHolder(config.absorptionEffect()).isEmpty()) {
                errors.add("effect.config.absorption_effect: unknown mob effect " + config.absorptionEffect());
            }
            int rankCount = definition.ranks().values().size();
            for (int index = 0; index < config.triggers().size(); index++) {
                if (config.triggers().get(index).minimumRank() > rankCount) {
                    errors.add("effect.config.triggers[" + index + "].minimum_rank: exceeds ability rank count");
                }
            }
        } catch (IllegalArgumentException exception) {
            return List.of(exception.getMessage());
        }
        RankValues merged = new RankValues(Map.of());
        for (int index = 0; index < definition.ranks().values().size(); index++) {
            int rankIndex = index;
            try {
                RankValues current = parse(RankValues.CODEC, definition.ranks().values().get(index),
                        "ranks.values[" + index + "]");
                current.values().keySet().stream().filter(key -> !RANK_KEYS.contains(key)).forEach(key ->
                        errors.add("ranks.values[" + rankIndex + "]." + key + ": unsupported support aura parameter"));
                merged = merge(merged, current);
                requiredHealing(merged.values().get("total_healing_percent"));
            } catch (IllegalArgumentException exception) {
                errors.add("ranks.values[" + index + "]: " + exception.getMessage());
            }
        }
        return List.copyOf(errors);
    }

    private static boolean activate(ServerPlayer player, ItemStack stack) {
        Optional<Activation> selected = activeComponents(player).stream()
                .flatMap(component -> component.config().triggers().stream()
                        .filter(trigger -> trigger.minimumRank() <= component.rankNumber())
                        .filter(trigger -> stack.is(trigger.item()))
                        .map(trigger -> new Activation(component, trigger)))
                .max(Comparator.comparingDouble(activation -> activation.component().totalHealingPercent()));
        if (selected.isEmpty()) {
            return false;
        }
        Activation activation = selected.get();
        ActiveComponent component = activation.component();
        Trigger trigger = activation.trigger();
        Map<ResourceLocation, HealingSession> activeSessions = SESSIONS.getOrDefault(player.getUUID(), Map.of());
        if (!canStartSession(activeSessions.keySet(), trigger.targetEntityTypeTag())) {
            return false;
        }
        TagKey<EntityType<?>> targetTag = TagKey.create(Registries.ENTITY_TYPE, trigger.targetEntityTypeTag());
        List<UUID> targets = player.level().getEntitiesOfClass(
                        LivingEntity.class,
                        player.getBoundingBox().inflate(component.config().radius()),
                        entity -> entity.isAlive() && entity.getType().is(targetTag)
                ).stream()
                .map(Entity::getUUID)
                .toList();
        if (targets.isEmpty()) {
            return false;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
            trigger.remainderItem().ifPresent(item -> {
                ItemStack remainder = new ItemStack(item);
                if (!player.getInventory().add(remainder)) {
                    player.drop(remainder, false);
                }
            });
        }
        Vec3 presentationColor = presentationColor(trigger.item());
        AbilityPresentationService.sendTracking(player, AbilityCue.pulse(
                component.abilityId(), ACTIVATE_CUE,
                player.getId(), -1, player.getBoundingBox().getCenter(),
                presentationColor, component.rankNumber(), player.getRandom().nextLong()
        ));
        if (trigger.grantAbsorption()) {
            Holder<MobEffect> absorption = BuiltInRegistries.MOB_EFFECT.getHolder(component.config().absorptionEffect())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown mob effect: " + component.config().absorptionEffect()
                    ));
            for (UUID targetId : targets) {
                Entity entity = player.serverLevel().getEntity(targetId);
                if (entity instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(
                            absorption,
                            component.config().absorptionDurationTicks(),
                            component.config().absorptionAmplifier(),
                            false,
                            false,
                            true
                    ));
                    AbilityPresentationService.sendTracking(living, AbilityCue.start(
                            component.abilityId(), GOLDEN_SHIELD_CUE,
                            player.getId(), living.getId(), living.getBoundingBox().getCenter(),
                            Vec3.ZERO, component.rankNumber(),
                            component.config().absorptionDurationTicks(),
                            living.getUUID().getLeastSignificantBits(),
                            player.getRandom().nextLong()
                    ));
                }
            }
        }
        for (UUID targetId : targets) {
            Entity entity = player.serverLevel().getEntity(targetId);
            if (entity instanceof LivingEntity living) {
                AbilityPresentationService.sendTracking(living, AbilityCue.pulse(
                        component.abilityId(), LINK_CUE,
                        player.getId(), living.getId(), living.getBoundingBox().getCenter(),
                        presentationColor, component.rankNumber(), player.getRandom().nextLong()
                ));
            }
        }
        float amount = (float) healingPerPulse(
                player.getMaxHealth(),
                component.totalHealingPercent(),
                component.config().pulseCount()
        );
        HealingSession session = new HealingSession(
                targets,
                amount,
                player.level().getGameTime() + component.config().pulseIntervalTicks(),
                component.config().pulseIntervalTicks(),
                component.config().pulseCount(),
                component.abilityId(),
                component.rankNumber()
        );
        LinkedHashMap<ResourceLocation, HealingSession> updated = new LinkedHashMap<>(activeSessions);
        updated.put(trigger.targetEntityTypeTag(), session);
        SESSIONS.put(player.getUUID(), Map.copyOf(updated));
        return true;
    }

    static boolean canStartSession(Set<ResourceLocation> activeSessionKeys, ResourceLocation requestedKey) {
        return !activeSessionKeys.contains(requestedKey);
    }

    private static List<ActiveComponent> activeComponents(ServerPlayer player) {
        ArrayList<ActiveComponent> result = new ArrayList<>();
        Registry<AbilityDefinition> abilities = player.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
        for (Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition> entry : abilities.entrySet()) {
            ResourceLocation abilityId = entry.getKey().location();
            try {
                Optional<AbilityService.ActiveAbility> active = AbilityService.active(player, abilityId);
                if (active.isEmpty()) continue;
                for (CompositeEffect.ComponentView component : CompositeEffect.componentsOfType(entry.getValue(), TYPE)) {
                    AbilityService.ActiveAbility projected = CompositeEffect.projectActive(active.get(), component);
                    RankValues values = mergeRanks(projected);
                    result.add(new ActiveComponent(
                            abilityId,
                            parse(Config.CODEC, component.config(), "effect.config"),
                            projected.rank(),
                            requiredHealing(values.values().get("total_healing_percent"))
                    ));
                }
            } catch (RuntimeException exception) {
                logInvalidOnce(abilityId, exception.getMessage());
            }
        }
        return List.copyOf(result);
    }

    private static RankValues mergeRanks(AbilityService.ActiveAbility active) {
        RankValues merged = new RankValues(Map.of());
        for (int index = 0; index < active.unlockedRankValues().size(); index++) {
            merged = merge(merged, parse(RankValues.CODEC, active.unlockedRankValues().get(index),
                    "ranks.values[" + index + "]"));
        }
        return merged;
    }

    private static double requiredHealing(Double value) {
        if (value == null || !Double.isFinite(value) || value < 0.0D || value > 10000.0D) {
            throw new IllegalArgumentException("total_healing_percent must be finite and between 0 and 10000");
        }
        return value;
    }

    private static Vec3 presentationColor(Item item) {
        String path = BuiltInRegistries.ITEM.getKey(item).getPath();
        return switch (path) {
            case "snow_block" -> new Vec3(0.82D, 0.94D, 1.0D);
            case "iron_ingot" -> new Vec3(0.68D, 0.70D, 0.67D);
            case "rabbit_stew" -> new Vec3(0.72D, 0.39D, 0.18D);
            case "golden_apple" -> new Vec3(1.0D, 0.68D, 0.18D);
            case "hay_block" -> new Vec3(0.88D, 0.70D, 0.20D);
            case "golden_carrot" -> new Vec3(1.0D, 0.50D, 0.10D);
            default -> new Vec3(0.80D, 0.84D, 0.72D);
        };
    }

    private static void logInvalidOnce(ResourceLocation id, String detail) {
        if (LOGGED_INVALID_DEFINITIONS.add(id + "|" + detail)) {
            AbilityMod.LOGGER.error("Invalid support aura ability {}: {}", id, detail);
        }
    }

    private static <T> T parse(Codec<T> codec, Dynamic<?> input, String path) {
        StringBuilder error = new StringBuilder();
        Optional<T> parsed = codec.parse(input).resultOrPartial(error::append);
        return parsed.orElseThrow(() -> new IllegalArgumentException(path + ": " + error));
    }

    public record Config(
            double radius,
            int pulseIntervalTicks,
            int pulseCount,
            ResourceLocation absorptionEffect,
            int absorptionAmplifier,
            int absorptionDurationTicks,
            List<Trigger> triggers
    ) {
        private static final Codec<Config> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.doubleRange(0.0D, 128.0D).optionalFieldOf("radius", 12.0D).forGetter(Config::radius),
                Codec.intRange(1, 12000).optionalFieldOf("pulse_interval_ticks", 100)
                        .forGetter(Config::pulseIntervalTicks),
                Codec.intRange(1, 100).optionalFieldOf("pulse_count", 5).forGetter(Config::pulseCount),
                ResourceLocation.CODEC.optionalFieldOf(
                        "absorption_effect",
                        ResourceLocation.withDefaultNamespace("absorption")
                ).forGetter(Config::absorptionEffect),
                Codec.intRange(0, 255).optionalFieldOf("absorption_amplifier", 49)
                        .forGetter(Config::absorptionAmplifier),
                Codec.intRange(1, 12000).optionalFieldOf("absorption_duration_ticks", 500)
                        .forGetter(Config::absorptionDurationTicks),
                Trigger.CODEC.listOf().fieldOf("triggers").forGetter(Config::triggers)
        ).apply(instance, Config::new));
        public static final Codec<Config> CODEC = RAW_CODEC.flatXmap(Config::validate, Config::validate);

        public Config { triggers = List.copyOf(triggers); }

        private static DataResult<Config> validate(Config config) {
            if (config.triggers().isEmpty()) {
                return DataResult.error(() -> "triggers must contain at least one entry");
            }
            long distinctItems = config.triggers().stream().map(Trigger::item).distinct().count();
            return distinctItems == config.triggers().size()
                    ? DataResult.success(config)
                    : DataResult.error(() -> "trigger items must be unique");
        }
    }

    public record Trigger(
            int minimumRank,
            Item item,
            ResourceLocation targetEntityTypeTag,
            boolean grantAbsorption,
            Optional<Item> remainderItem
    ) {
        public static final Codec<Trigger> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 100).fieldOf("minimum_rank").forGetter(Trigger::minimumRank),
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Trigger::item),
                ResourceLocation.CODEC.fieldOf("target_entity_type_tag").forGetter(Trigger::targetEntityTypeTag),
                Codec.BOOL.optionalFieldOf("grant_absorption", false).forGetter(Trigger::grantAbsorption),
                BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("remainder_item").forGetter(Trigger::remainderItem)
        ).apply(instance, Trigger::new));
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);
        public RankValues { values = Map.copyOf(values); }
    }

    private record ActiveComponent(
            ResourceLocation abilityId,
            Config config,
            int rankNumber,
            double totalHealingPercent
    ) {
    }

    private record Activation(ActiveComponent component, Trigger trigger) {
    }

    private record HealingSession(
            List<UUID> targets,
            float healingPerPulse,
            long nextPulseAt,
            int pulseIntervalTicks,
            int pulsesRemaining,
            ResourceLocation abilityId,
            int rank
    ) {
        private HealingSession { targets = List.copyOf(targets); }
    }
}
