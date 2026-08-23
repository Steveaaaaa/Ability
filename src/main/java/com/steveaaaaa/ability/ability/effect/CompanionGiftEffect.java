package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.sniffer.Sniffer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootContext;

public final class CompanionGiftEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("companion_gift");
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    private CompanionGiftEffect() {
    }

    public static ObjectArrayList<ItemStack> addLoot(
            ObjectArrayList<ItemStack> generatedLoot,
            LootContext context,
            Entity sourceEntity
    ) {
        Source source = sourceFor(context.getQueriedLootTableId(), sourceEntity);
        if (source == null || !(context.getLevel() instanceof ServerLevel level)) {
            return generatedLoot;
        }
        Optional<ActiveComponent> selected = switch (source) {
            case CAT_MORNING_GIFT -> catOwnerComponent(sourceEntity, source);
            case SNIFFER_DIGGING -> nearestSnifferComponent(level, sourceEntity, source);
        };
        if (selected.isEmpty()) {
            return generatedLoot;
        }
        Config config = selected.get().config();
        RandomSource random = context.getRandom();
        for (int roll = 0; roll < config.rolls(); roll++) {
            if (random.nextDouble() > config.chance()) {
                continue;
            }
            WeightedEntry entry = choose(config.entries(), random);
            int count = entry.minimumCount() == entry.maximumCount()
                    ? entry.minimumCount()
                    : entry.minimumCount() + random.nextInt(entry.maximumCount() - entry.minimumCount() + 1);
            generatedLoot.add(new ItemStack(entry.item(), count));
        }
        return generatedLoot;
    }

    static WeightedEntry choose(List<WeightedEntry> entries, RandomSource random) {
        int totalWeight = entries.stream().mapToInt(WeightedEntry::weight).sum();
        int selected = random.nextInt(totalWeight);
        for (WeightedEntry entry : entries) {
            selected -= entry.weight();
            if (selected < 0) {
                return entry;
            }
        }
        return entries.getLast();
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        try {
            parse(Config.CODEC, definition.effect().config(), "effect.config");
            return List.of();
        } catch (IllegalArgumentException exception) {
            return List.of(exception.getMessage());
        }
    }

    private static Source sourceFor(ResourceLocation lootTable, Entity entity) {
        if (entity instanceof Cat && lootTable.equals(BuiltInLootTables.CAT_MORNING_GIFT.location())) {
            return Source.CAT_MORNING_GIFT;
        }
        if (entity instanceof Sniffer && lootTable.equals(BuiltInLootTables.SNIFFER_DIGGING.location())) {
            return Source.SNIFFER_DIGGING;
        }
        return null;
    }

    private static Optional<ActiveComponent> catOwnerComponent(Entity entity, Source source) {
        if (!(entity instanceof Cat cat) || !(cat.getOwner() instanceof ServerPlayer owner)) {
            return Optional.empty();
        }
        return activeComponents(owner, source).stream().findFirst();
    }

    private static Optional<ActiveComponent> nearestSnifferComponent(
            ServerLevel level,
            Entity sniffer,
            Source source
    ) {
        return level.players().stream()
                .filter(player -> !player.isSpectator())
                .map(player -> activeComponents(player, source).stream()
                        .filter(component -> player.distanceToSqr(sniffer)
                                <= component.config().playerRadius() * component.config().playerRadius())
                        .findFirst()
                        .map(component -> new PlayerComponent(player.distanceToSqr(sniffer), component)))
                .flatMap(Optional::stream)
                .min(Comparator.comparingDouble(PlayerComponent::distanceSquared))
                .map(PlayerComponent::component);
    }

    private static List<ActiveComponent> activeComponents(ServerPlayer player, Source source) {
        ArrayList<ActiveComponent> result = new ArrayList<>();
        Registry<AbilityDefinition> abilities = player.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
        List<Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition>> sorted = abilities.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().location())).toList();
        for (Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition> entry : sorted) {
            ResourceLocation abilityId = entry.getKey().location();
            try {
                Optional<AbilityService.ActiveAbility> active = AbilityService.active(player, abilityId);
                if (active.isEmpty()) continue;
                for (CompositeEffect.ComponentView component : CompositeEffect.componentsOfType(entry.getValue(), TYPE)) {
                    Config config = parse(Config.CODEC, component.config(), "effect.config");
                    if (config.source() == source) {
                        result.add(new ActiveComponent(config));
                    }
                }
            } catch (RuntimeException exception) {
                logInvalidOnce(abilityId, exception.getMessage());
            }
        }
        return List.copyOf(result);
    }

    private static void logInvalidOnce(ResourceLocation id, String detail) {
        if (LOGGED_INVALID_DEFINITIONS.add(id + "|" + detail)) {
            AbilityMod.LOGGER.error("Invalid companion gift ability {}: {}", id, detail);
        }
    }

    private static <T> T parse(Codec<T> codec, Dynamic<?> input, String path) {
        StringBuilder error = new StringBuilder();
        Optional<T> parsed = codec.parse(input).resultOrPartial(error::append);
        return parsed.orElseThrow(() -> new IllegalArgumentException(path + ": " + error));
    }

    public record Config(Source source, double playerRadius, double chance, int rolls, List<WeightedEntry> entries) {
        private static final Codec<Config> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Source.CODEC.fieldOf("source").forGetter(Config::source),
                Codec.doubleRange(0.0D, 128.0D).optionalFieldOf("player_radius", 16.0D)
                        .forGetter(Config::playerRadius),
                Codec.doubleRange(0.0D, 1.0D).optionalFieldOf("chance", 0.25D).forGetter(Config::chance),
                Codec.intRange(1, 64).optionalFieldOf("rolls", 1).forGetter(Config::rolls),
                WeightedEntry.CODEC.listOf().fieldOf("entries").forGetter(Config::entries)
        ).apply(instance, Config::new));
        public static final Codec<Config> CODEC = RAW_CODEC.flatXmap(Config::validate, Config::validate);

        public Config { entries = List.copyOf(entries); }

        private static DataResult<Config> validate(Config config) {
            if (config.entries().isEmpty()) {
                return DataResult.error(() -> "entries must contain at least one entry");
            }
            long totalWeight = config.entries().stream().mapToLong(WeightedEntry::weight).sum();
            return totalWeight <= Integer.MAX_VALUE
                    ? DataResult.success(config)
                    : DataResult.error(() -> "total entry weight exceeds integer range");
        }
    }

    public record WeightedEntry(Item item, int weight, int minimumCount, int maximumCount) {
        private static final Codec<WeightedEntry> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(WeightedEntry::item),
                Codec.intRange(1, 1_000_000).optionalFieldOf("weight", 1).forGetter(WeightedEntry::weight),
                Codec.intRange(1, 64).optionalFieldOf("minimum_count", 1).forGetter(WeightedEntry::minimumCount),
                Codec.intRange(1, 64).optionalFieldOf("maximum_count", 1).forGetter(WeightedEntry::maximumCount)
        ).apply(instance, WeightedEntry::new));
        public static final Codec<WeightedEntry> CODEC = RAW_CODEC.flatXmap(WeightedEntry::validate, WeightedEntry::validate);

        private static DataResult<WeightedEntry> validate(WeightedEntry entry) {
            return entry.minimumCount() <= entry.maximumCount()
                    ? DataResult.success(entry)
                    : DataResult.error(() -> "minimum_count must not exceed maximum_count");
        }
    }

    public enum Source implements StringRepresentable {
        SNIFFER_DIGGING("sniffer_digging"),
        CAT_MORNING_GIFT("cat_morning_gift");

        public static final Codec<Source> CODEC = StringRepresentable.fromEnum(Source::values);
        private final String serializedName;

        Source(String serializedName) { this.serializedName = serializedName; }

        @Override
        public String getSerializedName() { return serializedName; }
    }

    private record ActiveComponent(Config config) {
    }

    private record PlayerComponent(double distanceSquared, ActiveComponent component) {
    }
}
