package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class ChorusTransmutationEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("chorus_transmutation");
    private static final Set<String> LOGGED_INVALID_DEFINITIONS = ConcurrentHashMap.newKeySet();

    private ChorusTransmutationEffect() {
    }

    public static void transmute(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Optional<Activation> selected = activeComponents(player).stream()
                .filter(component -> player.getMainHandItem().is(component.config().catalyst())
                        && player.getOffhandItem().is(component.config().catalyst()))
                .flatMap(component -> component.config().recipes().stream()
                        .filter(recipe -> recipe.minimumRank() <= component.rank())
                        .map(recipe -> resolveRecipe(level.getBlockState(event.getPos()), component, recipe))
                        .flatMap(Optional::stream))
                .min(Comparator.comparingInt(activation -> activation.recipe().minimumRank()));
        if (selected.isEmpty()) {
            return;
        }
        Activation activation = selected.get();
        int experienceCost = activation.recipe().experienceCostPerRank()
                * Math.max(0, activation.component().rank() - activation.recipe().experienceCostRankOffset());
        if (!player.getAbilities().instabuild && player.experienceLevel < experienceCost) {
            return;
        }
        if (!level.setBlockAndUpdate(event.getPos(), activation.output().defaultBlockState())) {
            return;
        }
        if (!player.getAbilities().instabuild) {
            player.getMainHandItem().shrink(1);
            player.getOffhandItem().shrink(1);
            if (experienceCost > 0) {
                player.giveExperienceLevels(-experienceCost);
            }
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    static Optional<Block> outputFor(Block input, int rank, Recipe recipe) {
        if (rank < recipe.minimumRank()) {
            return Optional.empty();
        }
        if (input == recipe.first()) {
            return Optional.of(recipe.second());
        }
        if (recipe.bidirectional() && input == recipe.second()) {
            return Optional.of(recipe.first());
        }
        return Optional.empty();
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        try {
            Config config = parse(Config.CODEC, definition.effect().config(), "effect.config");
            ArrayList<String> errors = new ArrayList<>();
            int ranks = definition.ranks().values().size();
            for (int index = 0; index < config.recipes().size(); index++) {
                if (config.recipes().get(index).minimumRank() > ranks) {
                    errors.add("effect.config.recipes[" + index + "].minimum_rank: exceeds ability rank count");
                }
            }
            return List.copyOf(errors);
        } catch (IllegalArgumentException exception) {
            return List.of(exception.getMessage());
        }
    }

    private static Optional<Activation> resolveRecipe(BlockState state, ActiveComponent component, Recipe recipe) {
        return outputFor(state.getBlock(), component.rank(), recipe)
                .map(output -> new Activation(component, recipe, output));
    }

    private static List<ActiveComponent> activeComponents(ServerPlayer player) {
        ArrayList<ActiveComponent> result = new ArrayList<>();
        Registry<AbilityDefinition> abilities = player.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
        for (Map.Entry<ResourceKey<AbilityDefinition>, AbilityDefinition> entry : abilities.entrySet()) {
            ResourceLocation id = entry.getKey().location();
            try {
                Optional<AbilityService.ActiveAbility> active = AbilityService.active(player, id);
                if (active.isEmpty()) continue;
                for (CompositeEffect.ComponentView component : CompositeEffect.componentsOfType(entry.getValue(), TYPE)) {
                    AbilityService.ActiveAbility projected = CompositeEffect.projectActive(active.get(), component);
                    result.add(new ActiveComponent(parse(Config.CODEC, component.config(), "effect.config"), projected.rank()));
                }
            } catch (RuntimeException exception) {
                logInvalidOnce(id, exception.getMessage());
            }
        }
        return List.copyOf(result);
    }

    private static void logInvalidOnce(ResourceLocation id, String detail) {
        if (LOGGED_INVALID_DEFINITIONS.add(id + "|" + detail)) {
            AbilityMod.LOGGER.error("Invalid chorus transmutation ability {}: {}", id, detail);
        }
    }

    private static <T> T parse(Codec<T> codec, Dynamic<?> input, String path) {
        StringBuilder error = new StringBuilder();
        Optional<T> parsed = codec.parse(input).resultOrPartial(error::append);
        return parsed.orElseThrow(() -> new IllegalArgumentException(path + ": " + error));
    }

    public record Config(Item catalyst, List<Recipe> recipes) {
        private static final Codec<Config> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("catalyst").forGetter(Config::catalyst),
                Recipe.CODEC.listOf().fieldOf("recipes").forGetter(Config::recipes)
        ).apply(instance, Config::new));
        public static final Codec<Config> CODEC = RAW_CODEC.flatXmap(Config::validate, Config::validate);

        public Config { recipes = List.copyOf(recipes); }

        private static DataResult<Config> validate(Config config) {
            return config.recipes().isEmpty()
                    ? DataResult.error(() -> "recipes must contain at least one entry")
                    : DataResult.success(config);
        }
    }

    public record Recipe(
            int minimumRank,
            Block first,
            Block second,
            boolean bidirectional,
            int experienceCostPerRank,
            int experienceCostRankOffset
    ) {
        private static final Codec<Recipe> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.intRange(1, 100).fieldOf("minimum_rank").forGetter(Recipe::minimumRank),
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("first").forGetter(Recipe::first),
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("second").forGetter(Recipe::second),
                Codec.BOOL.optionalFieldOf("bidirectional", true).forGetter(Recipe::bidirectional),
                Codec.intRange(0, 100).optionalFieldOf("experience_cost_per_rank", 0)
                        .forGetter(Recipe::experienceCostPerRank),
                Codec.intRange(0, 100).optionalFieldOf("experience_cost_rank_offset", 0)
                        .forGetter(Recipe::experienceCostRankOffset)
        ).apply(instance, Recipe::new));
        public static final Codec<Recipe> CODEC = RAW_CODEC.flatXmap(Recipe::validate, Recipe::validate);

        private static DataResult<Recipe> validate(Recipe recipe) {
            if (recipe.first() == recipe.second()) {
                return DataResult.error(() -> "recipe blocks must differ");
            }
            return DataResult.success(recipe);
        }
    }

    private record ActiveComponent(Config config, int rank) {
    }

    private record Activation(ActiveComponent component, Recipe recipe, Block output) {
    }
}
