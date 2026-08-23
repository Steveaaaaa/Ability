package com.steveaaaaa.ability.trigger;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.data.model.TypedConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
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

    static {
        register(BREAK_BLOCK, BreakBlockConfig.CODEC, TriggerTypeRegistry::matchBlockBreak);
        register(KILL_ENTITY, KillEntityConfig.CODEC, TriggerTypeRegistry::matchEntityKill);
        register(HARVEST_CROP, HarvestCropConfig.CODEC, TriggerTypeRegistry::matchCropHarvest);
    }

    private TriggerTypeRegistry() {
    }

    public static synchronized <C> void register(
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
            boolean excludePlayers
    ) {
        public static final Codec<KillEntityConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("entity_tag").forGetter(KillEntityConfig::entityTag),
                Codec.BOOL.optionalFieldOf("hostile_only", false).forGetter(KillEntityConfig::hostileOnly),
                Codec.BOOL.optionalFieldOf("exclude_players", true).forGetter(KillEntityConfig::excludePlayers)
        ).apply(instance, KillEntityConfig::new));
    }

    public record HarvestCropConfig(Optional<ResourceLocation> blockTag, boolean requireMature) {
        public static final Codec<HarvestCropConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.optionalFieldOf("block_tag").forGetter(HarvestCropConfig::blockTag),
                Codec.BOOL.optionalFieldOf("require_mature", true).forGetter(HarvestCropConfig::requireMature)
        ).apply(instance, HarvestCropConfig::new));
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
