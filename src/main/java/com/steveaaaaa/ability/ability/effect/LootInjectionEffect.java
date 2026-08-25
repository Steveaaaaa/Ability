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
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

public final class LootInjectionEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("loot_injection");
    private static final Set<String> RANK_KEYS = Set.of("chance", "rolls", "min_count", "max_count");
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    private LootInjectionEffect() {
    }

    public static void processBlock(BlockDropsEvent event, ServerPlayer player) {
        process(player, Context.BLOCK_DROPS, (abilityId, config, values, rank) -> {
            if (!matchesBlock(event, config)) {
                return;
            }
            int availableRequiredDrops = config.requiredDrop()
                    .map(itemId -> countDrops(event.getDrops(), itemId))
                    .orElse(Integer.MAX_VALUE);
            if (availableRequiredDrops == 0) {
                return;
            }
            List<Entry> eligibleEntries = eligibleEntries(config.entries(), rank);
            if (eligibleEntries.isEmpty()) {
                return;
            }
            ResolvedRank effectiveValues = config.consumeRequiredDrop()
                    ? new ResolvedRank(
                            values.chance(),
                            Math.min(values.rolls(), availableRequiredDrops),
                            values.minCount(),
                            values.maxCount()
                    )
                    : values;
            RollResult result = rollResult(eligibleEntries, effectiveValues, event.getLevel().getRandom());
            if (config.consumeRequiredDrop() && result.successfulRolls() > 0) {
                consumeDrops(event.getDrops(), config.requiredDrop().orElseThrow(), result.successfulRolls());
            }
            List<ItemStack> drops = result.drops();
            drops.forEach(stack -> {
                ItemEntity entity = new ItemEntity(
                        event.getLevel(),
                        event.getPos().getX() + 0.5D,
                        event.getPos().getY() + 0.5D,
                        event.getPos().getZ() + 0.5D,
                        stack
                );
                entity.setDefaultPickUpDelay();
                event.getDrops().add(entity);
            });
            if (!drops.isEmpty()) {
                config.successCue().ifPresent(cueId -> emitSuccessCue(
                        player,
                        abilityId,
                        cueId,
                        event.getPos().getCenter(),
                        rank,
                        event.getLevel().getGameTime() ^ event.getPos().asLong()
                ));
            }
        });
    }

    public static void processEntity(LivingDropsEvent event, ServerPlayer player) {
        if (!(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        process(player, Context.ENTITY_DROPS, (abilityId, config, values, rank) -> {
            if (!matchesEntity(event, config)) {
                return;
            }
            List<Entry> eligibleEntries = eligibleEntries(config.entries(), rank);
            if (eligibleEntries.isEmpty()) {
                return;
            }
            List<ItemStack> drops = roll(eligibleEntries, values, level.getRandom());
            drops.forEach(stack -> {
                ItemEntity entity = new ItemEntity(
                        level,
                        event.getEntity().getX(),
                        event.getEntity().getY(),
                        event.getEntity().getZ(),
                        stack
                );
                entity.setDefaultPickUpDelay();
                event.getDrops().add(entity);
            });
            if (!drops.isEmpty()) {
                config.successCue().ifPresent(cueId -> emitSuccessCue(
                        player,
                        abilityId,
                        cueId,
                        event.getEntity().position(),
                        rank,
                        level.getGameTime() ^ event.getEntity().getId()
                ));
            }
        });
    }

    static List<ItemStack> roll(List<Entry> entries, ResolvedRank values, RandomSource random) {
        return rollResult(entries, values, random).drops();
    }

    static RollResult rollResult(List<Entry> entries, ResolvedRank values, RandomSource random) {
        ArrayList<ItemStack> result = new ArrayList<>();
        int successfulRolls = 0;
        int totalWeight = entries.stream().mapToInt(Entry::weight).sum();
        for (int roll = 0; roll < values.rolls(); roll++) {
            if (random.nextDouble() >= values.chance()) {
                continue;
            }
            successfulRolls++;
            Entry selected = select(entries, random.nextInt(totalWeight));
            Item item = BuiltInRegistries.ITEM.getOptional(selected.item())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown loot item: " + selected.item()));
            int remaining = values.minCount() == values.maxCount()
                    ? values.minCount()
                    : random.nextInt(values.minCount(), values.maxCount() + 1);
            while (remaining > 0) {
                int count = Math.min(remaining, item.getDefaultMaxStackSize());
                result.add(new ItemStack(item, count));
                remaining -= count;
            }
        }
        return new RollResult(List.copyOf(result), successfulRolls);
    }

    static List<Entry> eligibleEntries(List<Entry> entries, int rank) {
        return entries.stream().filter(entry -> entry.minimumRank() <= rank).toList();
    }

    static Entry select(List<Entry> entries, int weightedRoll) {
        int cursor = weightedRoll;
        for (Entry entry : entries) {
            if (cursor < entry.weight()) {
                return entry;
            }
            cursor -= entry.weight();
        }
        throw new IllegalArgumentException("Weighted loot roll is outside the configured pool");
    }

    static RankValues merge(RankValues earlier, RankValues later) {
        LinkedHashMap<String, Double> merged = new LinkedHashMap<>(earlier.values());
        merged.putAll(later.values());
        return new RankValues(merged);
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        ArrayList<String> errors = new ArrayList<>();
        Config config;
        try {
            config = parse(Config.CODEC, definition.effect().config(), "effect.config");
        } catch (IllegalArgumentException exception) {
            return List.of(exception.getMessage());
        }

        if (config.context() == Context.BLOCK_DROPS && !config.entityTypeTags().isEmpty()) {
            errors.add("effect.config.entity_type_tags: only valid for entity_drops context");
        }
        if (config.context() == Context.ENTITY_DROPS
                && (!config.blockTags().isEmpty()
                || !config.toolTags().isEmpty()
                || config.requiredDrop().isPresent()
                || config.consumeRequiredDrop())) {
            errors.add(
                    "effect.config: block_tags, tool_tags, required_drop and consume_required_drop "
                            + "are only valid for block_drops context"
            );
        }
        if (config.consumeRequiredDrop() && config.requiredDrop().isEmpty()) {
            errors.add("effect.config.consume_required_drop: requires required_drop");
        }
        config.requiredDrop().ifPresent(itemId -> {
            Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            if (item == null || item == Items.AIR) {
                errors.add("effect.config.required_drop: unknown or empty item " + itemId);
            }
        });
        int maximumRank = definition.ranks().values().size();
        for (int index = 0; index < config.entries().size(); index++) {
            Entry entry = config.entries().get(index);
            if (entry.minimumRank() > maximumRank) {
                errors.add(
                        "effect.config.entries[" + index + "].minimum_rank: exceeds maximum ability rank "
                                + maximumRank
                );
            }
        }
        for (int index = 0; index < config.entries().size(); index++) {
            ResourceLocation itemId = config.entries().get(index).item();
            Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
            if (item == null || item == Items.AIR) {
                errors.add("effect.config.entries[" + index + "].item: unknown or empty item " + itemId);
            }
        }

        RankValues merged = new RankValues(Map.of());
        for (int index = 0; index < definition.ranks().values().size(); index++) {
            try {
                RankValues current = parse(
                        RankValues.CODEC,
                        definition.ranks().values().get(index),
                        "ranks.values[" + index + "]"
                );
                if (current.values().isEmpty()) {
                    errors.add("ranks.values[" + index + "]: must define at least one loot parameter");
                }
                int rankIndex = index;
                current.values().keySet().stream()
                        .filter(key -> !RANK_KEYS.contains(key))
                        .forEach(key -> errors.add(
                                "ranks.values[" + rankIndex + "]." + key + ": unsupported loot parameter"
                        ));
                merged = merge(merged, current);
                try {
                    resolve(merged);
                } catch (IllegalArgumentException exception) {
                    errors.add("ranks.values[" + index + "]: " + exception.getMessage());
                }
            } catch (IllegalArgumentException exception) {
                errors.add(exception.getMessage());
            }
        }
        return List.copyOf(errors);
    }

    private static void process(ServerPlayer player, Context context, LootConsumer consumer) {
        Registry<AbilityDefinition> abilities = player.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
        List<Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition>> sorted = abilities.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location()))
                .toList();
        for (Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition> entry : sorted) {
            ResourceLocation abilityId = entry.getKey().location();
            try {
                List<CompositeEffect.ComponentView> components =
                        CompositeEffect.componentsOfType(entry.getValue(), TYPE);
                if (components.isEmpty()) {
                    continue;
                }
                Optional<AbilityService.ActiveAbility> active = AbilityService.active(player, abilityId);
                if (active.isEmpty()) {
                    continue;
                }
                for (CompositeEffect.ComponentView component : components) {
                    Config config = parse(Config.CODEC, component.config(), "effect.config");
                    if (config.context() != context) {
                        continue;
                    }
                    AbilityService.ActiveAbility projected = CompositeEffect.projectActive(active.get(), component);
                    RankValues merged = new RankValues(Map.of());
                    for (int index = 0; index < projected.unlockedRankValues().size(); index++) {
                        merged = merge(merged, parse(
                                RankValues.CODEC,
                                projected.unlockedRankValues().get(index),
                                "ranks.values[" + index + "]"
                        ));
                    }
                    consumer.apply(abilityId, config, resolve(merged), projected.rank());
                }
            } catch (RuntimeException exception) {
                logInvalidOnce(abilityId, exception.getMessage());
            }
        }
    }

    private static boolean matchesBlock(BlockDropsEvent event, Config config) {
        boolean blockMatches = config.blockTags().isEmpty() || config.blockTags().stream().anyMatch(tag ->
                event.getState().is(TagKey.create(Registries.BLOCK, tag)));
        boolean toolMatches = config.toolTags().isEmpty() || config.toolTags().stream().anyMatch(tag ->
                event.getTool().is(TagKey.create(Registries.ITEM, tag)));
        return blockMatches && toolMatches;
    }

    private static boolean matchesEntity(LivingDropsEvent event, Config config) {
        return config.entityTypeTags().isEmpty() || config.entityTypeTags().stream().anyMatch(tag ->
                event.getEntity().getType().is(TagKey.create(Registries.ENTITY_TYPE, tag)));
    }

    private static int countDrops(List<ItemEntity> drops, ResourceLocation itemId) {
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElse(null);
        if (item == null || item == Items.AIR) {
            return 0;
        }
        return drops.stream()
                .filter(drop -> drop.getItem().is(item))
                .mapToInt(drop -> drop.getItem().getCount())
                .sum();
    }

    private static void consumeDrops(List<ItemEntity> drops, ResourceLocation itemId, int count) {
        Item item = BuiltInRegistries.ITEM.getOptional(itemId).orElseThrow();
        int remaining = count;
        for (int index = drops.size() - 1; index >= 0 && remaining > 0; index--) {
            ItemEntity drop = drops.get(index);
            if (!drop.getItem().is(item)) {
                continue;
            }
            int consumed = Math.min(remaining, drop.getItem().getCount());
            drop.getItem().shrink(consumed);
            remaining -= consumed;
            if (drop.getItem().isEmpty()) {
                drops.remove(index);
            }
        }
    }

    static ResolvedRank resolve(RankValues values) {
        double chance = values.values().getOrDefault("chance", 1.0D);
        int rolls = exactInt(values.values().getOrDefault("rolls", 1.0D), "rolls", 0, 64);
        int minCount = exactInt(values.values().getOrDefault("min_count", 1.0D), "min_count", 1, 64);
        int maxCount = exactInt(values.values().getOrDefault("max_count", (double) minCount), "max_count", 1, 64);
        if (!Double.isFinite(chance) || chance < 0.0D || chance > 1.0D) {
            throw new IllegalArgumentException("chance must be finite and between 0 and 1");
        }
        if (maxCount < minCount) {
            throw new IllegalArgumentException("max_count must be greater than or equal to min_count");
        }
        return new ResolvedRank(chance, rolls, minCount, maxCount);
    }

    private static int exactInt(double value, String name, int minimum, int maximum) {
        if (!Double.isFinite(value) || value != Math.rint(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(
                    name + " must be a whole number between " + minimum + " and " + maximum
            );
        }
        return (int) value;
    }

    private static void logInvalidOnce(ResourceLocation abilityId, String detail) {
        String message = detail == null ? "Unknown loot injection error" : detail;
        if (LOGGED_INVALID_DEFINITIONS.add(abilityId + "|" + message)) {
            AbilityMod.LOGGER.error("Invalid loot injection ability {}: {}", abilityId, message);
        }
    }

    private static void emitSuccessCue(
            ServerPlayer player,
            ResourceLocation abilityId,
            ResourceLocation cueId,
            net.minecraft.world.phys.Vec3 position,
            int rank,
            long seed
    ) {
        AbilityPresentationService.sendTracking(player, AbilityCue.pulse(
                abilityId,
                cueId,
                player.getId(),
                -1,
                position,
                net.minecraft.world.phys.Vec3.ZERO,
                rank,
                seed
        ));
    }

    private static <T> T parse(Codec<T> codec, Dynamic<?> input, String path) {
        StringBuilder error = new StringBuilder();
        Optional<T> parsed = codec.parse(input).resultOrPartial(message -> {
            if (!error.isEmpty()) {
                error.append("; ");
            }
            error.append(message);
        });
        return parsed.orElseThrow(() -> new IllegalArgumentException(path + ": " + error));
    }

    public record Config(
            Context context,
            List<ResourceLocation> blockTags,
            List<ResourceLocation> entityTypeTags,
            List<ResourceLocation> toolTags,
            Optional<ResourceLocation> requiredDrop,
            boolean consumeRequiredDrop,
            Optional<ResourceLocation> successCue,
            List<Entry> entries
    ) {
        private static final Codec<Config> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Context.CODEC.fieldOf("context").forGetter(Config::context),
                ResourceLocation.CODEC.listOf().optionalFieldOf("block_tags", List.of()).forGetter(Config::blockTags),
                ResourceLocation.CODEC.listOf().optionalFieldOf("entity_type_tags", List.of())
                        .forGetter(Config::entityTypeTags),
                ResourceLocation.CODEC.listOf().optionalFieldOf("tool_tags", List.of()).forGetter(Config::toolTags),
                ResourceLocation.CODEC.optionalFieldOf("required_drop").forGetter(Config::requiredDrop),
                Codec.BOOL.optionalFieldOf("consume_required_drop", false).forGetter(Config::consumeRequiredDrop),
                ResourceLocation.CODEC.optionalFieldOf("success_cue").forGetter(Config::successCue),
                Entry.CODEC.listOf().fieldOf("entries").forGetter(Config::entries)
        ).apply(instance, Config::new));
        public static final Codec<Config> CODEC = RAW_CODEC.flatXmap(Config::validate, Config::validate);

        public Config {
            blockTags = List.copyOf(blockTags);
            entityTypeTags = List.copyOf(entityTypeTags);
            toolTags = List.copyOf(toolTags);
            entries = List.copyOf(entries);
        }

        private static DataResult<Config> validate(Config config) {
            if (config.entries().isEmpty()) {
                return DataResult.error(() -> "entries must contain at least one item");
            }
            if (config.entries().size() > 1024) {
                return DataResult.error(() -> "entries cannot contain more than 1024 items");
            }
            long totalWeight = config.entries().stream().mapToLong(Entry::weight).sum();
            if (totalWeight > Integer.MAX_VALUE) {
                return DataResult.error(() -> "combined entry weight exceeds integer range");
            }
            return DataResult.success(config);
        }
    }

    public record Entry(ResourceLocation item, int weight, int minimumRank) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.intRange(1, 1_000_000).optionalFieldOf("weight", 1).forGetter(Entry::weight),
                Codec.intRange(1, 1024).optionalFieldOf("minimum_rank", 1).forGetter(Entry::minimumRank)
        ).apply(instance, Entry::new));
    }

    public enum Context implements StringRepresentable {
        BLOCK_DROPS("block_drops"),
        ENTITY_DROPS("entity_drops");

        public static final Codec<Context> CODEC = StringRepresentable.fromEnum(Context::values);
        private final String serializedName;

        Context(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }

    public record RankValues(Map<String, Double> values) {
        public static final Codec<RankValues> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE)
                .xmap(RankValues::new, RankValues::values);

        public RankValues {
            values = Map.copyOf(values);
        }
    }

    public record ResolvedRank(double chance, int rolls, int minCount, int maxCount) {
    }

    public record RollResult(List<ItemStack> drops, int successfulRolls) {
        public RollResult {
            drops = List.copyOf(drops);
        }
    }

    @FunctionalInterface
    private interface LootConsumer {
        void apply(ResourceLocation abilityId, Config config, ResolvedRank values, int rank);
    }
}
