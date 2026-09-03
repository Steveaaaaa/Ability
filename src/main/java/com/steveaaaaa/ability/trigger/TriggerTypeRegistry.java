package com.steveaaaaa.ability.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.data.model.TypedConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

public final class TriggerTypeRegistry {
    private static final double MAX_DESTROY_TIME_MULTIPLIER = 64.0D;
    private static final Map<ResourceLocation, RegisteredType<?>> TYPES = new LinkedHashMap<>();

    public static final ResourceLocation BREAK_BLOCK = AbilityMod.id("break_block");
    public static final ResourceLocation KILL_ENTITY = AbilityMod.id("kill_entity");
    public static final ResourceLocation HARVEST_CROP = AbilityMod.id("harvest_crop");
    public static final ResourceLocation BREED_ANIMAL = AbilityMod.id("breed_animal");
    public static final ResourceLocation PLACE_BLOCK = AbilityMod.id("place_block");
    public static final ResourceLocation TRAVEL = AbilityMod.id("travel");
    public static final ResourceLocation RANGED_KILL = AbilityMod.id("ranged_kill");
    public static final ResourceLocation TAKE_DAMAGE = AbilityMod.id("take_damage");
    public static final ResourceLocation ENCHANT_ITEM = AbilityMod.id("enchant_item");

    static {
        register(BREAK_BLOCK, BreakBlockConfig.CODEC, TriggerTypeRegistry::matchBlockBreak);
        register(KILL_ENTITY, KillEntityConfig.CODEC, TriggerTypeRegistry::matchEntityKill);
        register(HARVEST_CROP, HarvestCropConfig.CODEC, TriggerTypeRegistry::matchCropHarvest);
        register(BREED_ANIMAL, BreedAnimalConfig.CODEC, TriggerTypeRegistry::matchAnimalBreed);
        register(PLACE_BLOCK, PlaceBlockConfig.CODEC, TriggerTypeRegistry::matchBlockPlace);
        register(TRAVEL, TravelConfig.CODEC, TriggerTypeRegistry::matchTravel);
        register(RANGED_KILL, RangedKillConfig.CODEC, TriggerTypeRegistry::matchRangedKill);
        register(TAKE_DAMAGE, TakeDamageConfig.CODEC, TriggerTypeRegistry::matchTakeDamage);
        register(ENCHANT_ITEM, EnchantItemConfig.CODEC, TriggerTypeRegistry::matchEnchantItem);
    }

    private TriggerTypeRegistry() {
    }

    private static synchronized <C> void register(
            ResourceLocation id,
            Codec<C> codec,
            TriggerMatcher<C> matcher
    ) {
        if (TYPES.putIfAbsent(id, new RegisteredType<>(codec, matcher)) != null) {
            throw new IllegalArgumentException("Duplicate experience trigger type: " + id);
        }
    }

    public static TriggerMatch match(ExperienceContext context, TypedConfig trigger) {
        RegisteredType<?> type = TYPES.get(trigger.type());
        return type == null
                ? TriggerMatch.invalid("Unknown experience trigger type: " + trigger.type())
                : type.match(context, trigger);
    }

    public static boolean isRegistered(ResourceLocation id) {
        return TYPES.containsKey(id);
    }

    public static Optional<String> validationError(TypedConfig trigger) {
        RegisteredType<?> type = TYPES.get(trigger.type());
        return type == null
                ? Optional.of("Unknown experience trigger type: " + trigger.type())
                : type.validationError(trigger);
    }

    private static TriggerMatch matchBlockBreak(ExperienceContext context, BreakBlockConfig config) {
        if (!(context instanceof ExperienceContext.BlockBreak blockBreak)) {
            return TriggerMatch.notMatched();
        }
        TagKey<Block> tag = TagKey.create(Registries.BLOCK, config.blockTag());
        if (!blockBreak.state().is(tag)) {
            return TriggerMatch.notMatched();
        }
        if (config.requireCorrectTool() && !blockBreak.player().hasCorrectToolForDrops(blockBreak.state())) {
            return TriggerMatch.notMatched();
        }
        if (!config.scaleByDestroyTime()) {
            return TriggerMatch.matched(1.0D);
        }
        float destroySpeed = blockBreak.state().getDestroySpeed(blockBreak.level(), blockBreak.pos());
        double multiplier = destroySpeed > 0.0F
                ? Math.clamp(destroySpeed, 1.0D, MAX_DESTROY_TIME_MULTIPLIER)
                : 1.0D;
        return TriggerMatch.matched(multiplier);
    }

    private static TriggerMatch matchEntityKill(ExperienceContext context, KillEntityConfig config) {
        if (!(context instanceof ExperienceContext.EntityKill entityKill)) {
            return TriggerMatch.notMatched();
        }
        if (config.excludePlayers() && entityKill.target() instanceof Player) {
            return TriggerMatch.notMatched();
        }
        if (config.hostileOnly()
                && (!(entityKill.target() instanceof Mob) || !(entityKill.target() instanceof Enemy))) {
            return TriggerMatch.notMatched();
        }
        if (config.entityTag().isPresent()) {
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, config.entityTag().get());
            if (!entityKill.target().getType().is(tag)) {
                return TriggerMatch.notMatched();
            }
        }
        if (config.excludedDamageTypeTag().isPresent()
                && entityKill.source().is(TagKey.create(
                Registries.DAMAGE_TYPE,
                config.excludedDamageTypeTag().get()
        ))) {
            return TriggerMatch.notMatched();
        }
        return TriggerMatch.matched(1.0D);
    }

    private static TriggerMatch matchCropHarvest(ExperienceContext context, HarvestCropConfig config) {
        if (!(context instanceof ExperienceContext.BlockBreak blockBreak)) {
            return TriggerMatch.notMatched();
        }
        if (config.blockTag().isPresent()) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, config.blockTag().get());
            if (!blockBreak.state().is(tag)) {
                return TriggerMatch.notMatched();
            }
        }
        CropAge cropAge = cropAge(blockBreak.state());
        if (cropAge == null) {
            return TriggerMatch.notMatched();
        }
        if (config.requireMature() && cropAge.age() < cropAge.maxAge()) {
            return TriggerMatch.notMatched();
        }
        return TriggerMatch.matched(1.0D);
    }

    private static TriggerMatch matchAnimalBreed(ExperienceContext context, BreedAnimalConfig config) {
        if (!(context instanceof ExperienceContext.AnimalBreed breed)) {
            return TriggerMatch.notMatched();
        }
        if (config.entityTag().isEmpty()) {
            return TriggerMatch.matched(1.0D);
        }
        AgeableMob target = breed.child();
        EntityType<?> type = target == null ? breed.parentA().getType() : target.getType();
        TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, config.entityTag().get());
        return type.is(tag) ? TriggerMatch.matched(1.0D) : TriggerMatch.notMatched();
    }

    private static TriggerMatch matchBlockPlace(ExperienceContext context, PlaceBlockConfig config) {
        if (!(context instanceof ExperienceContext.BlockPlace blockPlace)) {
            return TriggerMatch.notMatched();
        }
        if (config.blockTag().isPresent()
                && !blockPlace.state().is(TagKey.create(Registries.BLOCK, config.blockTag().get()))) {
            return TriggerMatch.notMatched();
        }
        float destroyTime = blockPlace.state().getDestroySpeed(blockPlace.level(), blockPlace.pos());
        if (destroyTime < config.minimumDestroyTime()) {
            return TriggerMatch.notMatched();
        }
        if (!config.scaleByDestroyTime()) {
            return TriggerMatch.matched(1.0D);
        }
        double multiplier = Math.clamp(
                destroyTime / Math.max(0.1D, config.minimumDestroyTime()),
                1.0D,
                16.0D
        );
        return TriggerMatch.matched(multiplier);
    }

    private static TriggerMatch matchTravel(ExperienceContext context, TravelConfig config) {
        if (!(context instanceof ExperienceContext.Movement movement)
                || !config.modes().contains(movement.mode())
                || movement.distance() < config.minimumDistance()) {
            return TriggerMatch.notMatched();
        }
        double countedDistance = Math.min(movement.distance(), config.maximumDistance());
        return TriggerMatch.matched(countedDistance / config.minimumDistance());
    }

    private static TriggerMatch matchRangedKill(ExperienceContext context, RangedKillConfig config) {
        if (!(context instanceof ExperienceContext.EntityKill entityKill)) {
            return TriggerMatch.notMatched();
        }
        if (config.excludePlayers() && entityKill.target() instanceof Player) {
            return TriggerMatch.notMatched();
        }
        if (config.hostileOnly()
                && (!(entityKill.target() instanceof Mob) || !(entityKill.target() instanceof Enemy))) {
            return TriggerMatch.notMatched();
        }
        if (config.entityTag().isPresent()
                && !entityKill.target().getType().is(TagKey.create(
                Registries.ENTITY_TYPE,
                config.entityTag().get()
        ))) {
            return TriggerMatch.notMatched();
        }
        TagKey<DamageType> damageTag = TagKey.create(Registries.DAMAGE_TYPE, config.damageTypeTag());
        return entityKill.source().is(damageTag) ? TriggerMatch.matched(1.0D) : TriggerMatch.notMatched();
    }

    private static TriggerMatch matchTakeDamage(ExperienceContext context, TakeDamageConfig config) {
        if (!(context instanceof ExperienceContext.DamageTaken damageTaken)
                || damageTaken.damage() < config.minimumDamage()) {
            return TriggerMatch.notMatched();
        }
        if (config.excludePlayerAttackers() && damageTaken.source().getEntity() instanceof Player) {
            return TriggerMatch.notMatched();
        }
        for (ResourceLocation tagId : config.excludedDamageTypeTags()) {
            if (damageTaken.source().is(TagKey.create(Registries.DAMAGE_TYPE, tagId))) {
                return TriggerMatch.notMatched();
            }
        }
        return TriggerMatch.matched(calculateDamageMultiplier(damageTaken.damage(), config));
    }

    private static TriggerMatch matchEnchantItem(ExperienceContext context, EnchantItemConfig config) {
        if (!(context instanceof ExperienceContext.ItemEnchanted enchanted)
                || enchanted.enchantmentCount() < config.minimumEnchantments()
                || enchanted.totalLevels() < config.minimumTotalLevels()) {
            return TriggerMatch.notMatched();
        }
        if (config.itemTag().isPresent()
                && !enchanted.item().is(TagKey.create(Registries.ITEM, config.itemTag().get()))) {
            return TriggerMatch.notMatched();
        }
        return TriggerMatch.matched(calculateEnchantmentMultiplier(enchanted.totalLevels(), config));
    }

    static double calculateDamageMultiplier(float finalDamage, TakeDamageConfig config) {
        double counted = Math.min(Math.max(0.0D, finalDamage), config.maximumCountedDamage());
        return counted / config.damagePerMultiplier();
    }

    static double calculateEnchantmentMultiplier(int totalLevels, EnchantItemConfig config) {
        return config.scaleByTotalLevels()
                ? Math.clamp(totalLevels, 1.0D, config.maximumMultiplier())
                : 1.0D;
    }

    private static CropAge cropAge(BlockState state) {
        for (Property<?> property : state.getProperties()) {
            if (property instanceof IntegerProperty ageProperty && property.getName().equals("age")) {
                int age = state.getValue(ageProperty);
                int maxAge = ageProperty.getPossibleValues().stream().mapToInt(Integer::intValue).max().orElse(age);
                return new CropAge(age, maxAge);
            }
        }
        return null;
    }

    @FunctionalInterface
    public interface TriggerMatcher<C> {
        TriggerMatch match(ExperienceContext context, C config);
    }

    public record BreakBlockConfig(
            ResourceLocation blockTag,
            boolean requireCorrectTool,
            boolean scaleByDestroyTime
    ) {
        public static final Codec<BreakBlockConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("block_tag").forGetter(BreakBlockConfig::blockTag),
                Codec.BOOL.optionalFieldOf("require_correct_tool", false)
                        .forGetter(BreakBlockConfig::requireCorrectTool),
                Codec.BOOL.optionalFieldOf("scale_by_destroy_time", false)
                        .forGetter(BreakBlockConfig::scaleByDestroyTime)
        ).apply(instance, BreakBlockConfig::new));
    }

    public record KillEntityConfig(
            Optional<ResourceLocation> entityTag,
            boolean hostileOnly,
            boolean excludePlayers,
            Optional<ResourceLocation> excludedDamageTypeTag
    ) {
        public static final Codec<KillEntityConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("entity_tag").forGetter(KillEntityConfig::entityTag),
                Codec.BOOL.optionalFieldOf("hostile_only", false).forGetter(KillEntityConfig::hostileOnly),
                Codec.BOOL.optionalFieldOf("exclude_players", true).forGetter(KillEntityConfig::excludePlayers),
                ResourceLocation.CODEC.optionalFieldOf("excluded_damage_type_tag")
                        .forGetter(KillEntityConfig::excludedDamageTypeTag)
        ).apply(instance, KillEntityConfig::new));
    }

    public record HarvestCropConfig(Optional<ResourceLocation> blockTag, boolean requireMature) {
        public static final Codec<HarvestCropConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("block_tag").forGetter(HarvestCropConfig::blockTag),
                Codec.BOOL.optionalFieldOf("require_mature", true).forGetter(HarvestCropConfig::requireMature)
        ).apply(instance, HarvestCropConfig::new));
    }

    public record BreedAnimalConfig(Optional<ResourceLocation> entityTag) {
        public static final Codec<BreedAnimalConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("entity_tag").forGetter(BreedAnimalConfig::entityTag)
        ).apply(instance, BreedAnimalConfig::new));
    }

    public record PlaceBlockConfig(
            Optional<ResourceLocation> blockTag,
            double minimumDestroyTime,
            boolean scaleByDestroyTime
    ) {
        public static final Codec<PlaceBlockConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("block_tag").forGetter(PlaceBlockConfig::blockTag),
                Codec.doubleRange(0.0D, 64.0D).optionalFieldOf("minimum_destroy_time", 0.5D)
                        .forGetter(PlaceBlockConfig::minimumDestroyTime),
                Codec.BOOL.optionalFieldOf("scale_by_destroy_time", false)
                        .forGetter(PlaceBlockConfig::scaleByDestroyTime)
        ).apply(instance, PlaceBlockConfig::new));
    }

    public record TravelConfig(
            java.util.List<ExperienceContext.MovementMode> modes,
            double minimumDistance,
            double maximumDistance
    ) {
        private static final Codec<TravelConfig> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                StringRepresentable.fromEnum(ExperienceContext.MovementMode::values).listOf()
                        .optionalFieldOf("modes", java.util.List.of(ExperienceContext.MovementMode.ON_FOOT))
                        .forGetter(TravelConfig::modes),
                Codec.doubleRange(0.1D, 1_000.0D).optionalFieldOf("minimum_distance", 2.0D)
                        .forGetter(TravelConfig::minimumDistance),
                Codec.doubleRange(0.1D, 1_000.0D).optionalFieldOf("maximum_distance", 16.0D)
                        .forGetter(TravelConfig::maximumDistance)
        ).apply(instance, TravelConfig::new));
        public static final Codec<TravelConfig> CODEC = RAW_CODEC.flatXmap(TravelConfig::validate, TravelConfig::validate);

        public TravelConfig {
            modes = java.util.List.copyOf(modes);
        }

        private static DataResult<TravelConfig> validate(TravelConfig config) {
            if (config.modes().isEmpty()) {
                return DataResult.error(() -> "modes must contain at least one movement mode");
            }
            if (config.maximumDistance() < config.minimumDistance()) {
                return DataResult.error(() -> "maximum_distance must be at least minimum_distance");
            }
            return DataResult.success(config);
        }
    }

    public record RangedKillConfig(
            Optional<ResourceLocation> entityTag,
            boolean hostileOnly,
            boolean excludePlayers,
            ResourceLocation damageTypeTag
    ) {
        public static final Codec<RangedKillConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("entity_tag").forGetter(RangedKillConfig::entityTag),
                Codec.BOOL.optionalFieldOf("hostile_only", false).forGetter(RangedKillConfig::hostileOnly),
                Codec.BOOL.optionalFieldOf("exclude_players", true).forGetter(RangedKillConfig::excludePlayers),
                ResourceLocation.CODEC.optionalFieldOf(
                        "damage_type_tag",
                        ResourceLocation.fromNamespaceAndPath("minecraft", "is_projectile")
                ).forGetter(RangedKillConfig::damageTypeTag)
        ).apply(instance, RangedKillConfig::new));
    }

    public record TakeDamageConfig(
            double minimumDamage,
            double damagePerMultiplier,
            double maximumCountedDamage,
            boolean excludePlayerAttackers,
            java.util.List<ResourceLocation> excludedDamageTypeTags
    ) {
        private static final Codec<TakeDamageConfig> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.doubleRange(0.01D, 1_000_000.0D).optionalFieldOf("minimum_damage", 1.0D)
                        .forGetter(TakeDamageConfig::minimumDamage),
                Codec.doubleRange(0.01D, 1_000_000.0D).optionalFieldOf("damage_per_multiplier", 4.0D)
                        .forGetter(TakeDamageConfig::damagePerMultiplier),
                Codec.doubleRange(0.01D, 1_000_000.0D).optionalFieldOf("maximum_counted_damage", 20.0D)
                        .forGetter(TakeDamageConfig::maximumCountedDamage),
                Codec.BOOL.optionalFieldOf("exclude_player_attackers", true)
                        .forGetter(TakeDamageConfig::excludePlayerAttackers),
                ResourceLocation.CODEC.listOf().optionalFieldOf("excluded_damage_type_tags", java.util.List.of())
                        .forGetter(TakeDamageConfig::excludedDamageTypeTags)
        ).apply(instance, TakeDamageConfig::new));
        public static final Codec<TakeDamageConfig> CODEC = RAW_CODEC.flatXmap(
                TakeDamageConfig::validate,
                TakeDamageConfig::validate
        );

        public TakeDamageConfig {
            excludedDamageTypeTags = java.util.List.copyOf(excludedDamageTypeTags);
        }

        private static DataResult<TakeDamageConfig> validate(TakeDamageConfig config) {
            return config.maximumCountedDamage() < config.minimumDamage()
                    ? DataResult.error(() -> "maximum_counted_damage must be at least minimum_damage")
                    : DataResult.success(config);
        }
    }

    public record EnchantItemConfig(
            Optional<ResourceLocation> itemTag,
            int minimumEnchantments,
            int minimumTotalLevels,
            boolean scaleByTotalLevels,
            double maximumMultiplier
    ) {
        public static final Codec<EnchantItemConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("item_tag").forGetter(EnchantItemConfig::itemTag),
                Codec.intRange(1, 256).optionalFieldOf("minimum_enchantments", 1)
                        .forGetter(EnchantItemConfig::minimumEnchantments),
                Codec.intRange(1, 10_000).optionalFieldOf("minimum_total_levels", 1)
                        .forGetter(EnchantItemConfig::minimumTotalLevels),
                Codec.BOOL.optionalFieldOf("scale_by_total_levels", true)
                        .forGetter(EnchantItemConfig::scaleByTotalLevels),
                Codec.doubleRange(1.0D, 1_000.0D).optionalFieldOf("maximum_multiplier", 10.0D)
                        .forGetter(EnchantItemConfig::maximumMultiplier)
        ).apply(instance, EnchantItemConfig::new));
    }

    private record RegisteredType<C>(Codec<C> codec, TriggerMatcher<C> matcher) {
        private Optional<String> validationError(TypedConfig trigger) {
            StringBuilder error = new StringBuilder();
            Optional<C> parsed = codec.parse(trigger.config()).resultOrPartial(message -> {
                if (!error.isEmpty()) {
                    error.append("; ");
                }
                error.append(message);
            });
            return parsed.isPresent()
                    ? Optional.empty()
                    : Optional.of("Invalid config for trigger " + trigger.type() + ": " + error);
        }

        private TriggerMatch match(ExperienceContext context, TypedConfig trigger) {
            StringBuilder error = new StringBuilder();
            Optional<C> parsed = codec.parse(trigger.config()).resultOrPartial(message -> {
                if (!error.isEmpty()) {
                    error.append("; ");
                }
                error.append(message);
            });
            return parsed
                    .map(config -> matcher.match(context, config))
                    .orElseGet(() -> TriggerMatch.invalid(
                            "Invalid config for trigger " + trigger.type() + ": " + error
                    ));
        }
    }

    private record CropAge(int age, int maxAge) {
    }
}
