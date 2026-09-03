package com.steveaaaaa.ability.condition;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.data.model.TypedConfig;
import com.steveaaaaa.ability.progress.ExperienceService;
import com.steveaaaaa.ability.progress.ModAttachments;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class ConditionTypeRegistry {
    private static final int MAX_NESTING_DEPTH = 64;
    private static final Map<ResourceLocation, RegisteredType<?>> TYPES = new LinkedHashMap<>();

    public static final ResourceLocation SKILL_LEVEL = AbilityMod.id("skill_level");
    public static final ResourceLocation ABILITY_PURCHASED = AbilityMod.id("ability_purchased");
    public static final ResourceLocation ADVANCEMENT = AbilityMod.id("advancement");
    public static final ResourceLocation ALL_OF = AbilityMod.id("all_of");
    public static final ResourceLocation ANY_OF = AbilityMod.id("any_of");
    public static final ResourceLocation NOT = AbilityMod.id("not");
    public static final ResourceLocation GAME_MODE = AbilityMod.id("game_mode");
    public static final ResourceLocation NOT_GAME_MODE = AbilityMod.id("not_game_mode");
    public static final ResourceLocation DIMENSION = AbilityMod.id("dimension");

    static {
        register(SKILL_LEVEL, SkillLevelConfig.CODEC, (context, config) -> {
            Optional<Integer> level = context.skillLevel(config.skill());
            if (level.isEmpty()) {
                return ConditionEvaluation.invalid("Unknown skill: " + config.skill());
            }
            return level.get() >= config.level()
                    ? ConditionEvaluation.satisfied()
                    : ConditionEvaluation.notSatisfied(
                            "Requires " + config.skill() + " level " + config.level()
                    );
        });
        register(ABILITY_PURCHASED, AbilityPurchasedConfig.CODEC, (context, config) ->
                context.player().getData(ModAttachments.PLAYER_PROGRESS).purchasedAbilities().contains(config.ability())
                        ? ConditionEvaluation.satisfied()
                        : ConditionEvaluation.notSatisfied("Requires purchased ability: " + config.ability())
        );
        register(ADVANCEMENT, AdvancementConfig.CODEC, (context, config) -> {
            AdvancementHolder advancement = context.player().server.getAdvancements().get(config.advancement());
            if (advancement == null) {
                return ConditionEvaluation.invalid("Unknown advancement: " + config.advancement());
            }
            return context.player().getAdvancements().getOrStartProgress(advancement).isDone()
                    ? ConditionEvaluation.satisfied()
                    : ConditionEvaluation.notSatisfied("Requires advancement: " + config.advancement());
        });
        register(ALL_OF, CompositeConfig.CODEC, (context, config) -> context.all(config.conditions()));
        register(ANY_OF, CompositeConfig.CODEC, (context, config) -> context.any(config.conditions()));
        register(NOT, NotConfig.CODEC, (context, config) -> context.negate(config.condition()));
        register(GAME_MODE, GameModeConfig.CODEC, (context, config) ->
                context.player().gameMode.getGameModeForPlayer().getName().equals(config.gameMode().getPath())
                        ? ConditionEvaluation.satisfied()
                        : ConditionEvaluation.notSatisfied("Requires game mode: " + config.gameMode())
        );
        register(NOT_GAME_MODE, GameModeConfig.CODEC, (context, config) ->
                context.player().gameMode.getGameModeForPlayer().getName().equals(config.gameMode().getPath())
                        ? ConditionEvaluation.notSatisfied("Disallowed game mode: " + config.gameMode())
                        : ConditionEvaluation.satisfied()
        );
        register(DIMENSION, DimensionConfig.CODEC, (context, config) ->
                context.player().level().dimension().location().equals(config.dimension())
                        ? ConditionEvaluation.satisfied()
                        : ConditionEvaluation.notSatisfied("Requires dimension: " + config.dimension())
        );
    }

    private ConditionTypeRegistry() {
    }

    private static synchronized <C> void register(
            ResourceLocation id,
            Codec<C> codec,
            ConditionEvaluator<C> evaluator
    ) {
        if (TYPES.putIfAbsent(id, new RegisteredType<>(codec, evaluator)) != null) {
            throw new IllegalArgumentException("Duplicate condition type: " + id);
        }
    }

    public static ConditionEvaluation evaluate(ServerPlayer player, TypedConfig condition) {
        return new EvaluationContext(player, 0).evaluate(condition);
    }

    public static boolean isRegistered(ResourceLocation id) {
        return TYPES.containsKey(id);
    }

    public static List<String> validateConfiguration(TypedConfig condition) {
        return validateConfiguration(condition, "condition");
    }

    public static List<String> validateConfiguration(TypedConfig condition, String path) {
        ArrayList<String> errors = new ArrayList<>();
        validateConfiguration(condition, path, 0, errors);
        return List.copyOf(errors);
    }

    private static void validateConfiguration(
            TypedConfig condition,
            String path,
            int depth,
            List<String> errors
    ) {
        if (depth >= MAX_NESTING_DEPTH) {
            errors.add(path + ": nesting exceeds " + MAX_NESTING_DEPTH + " levels");
            return;
        }
        RegisteredType<?> type = TYPES.get(condition.type());
        if (type == null) {
            errors.add(path + ": unknown condition type " + condition.type());
            return;
        }
        Optional<String> parseError = type.validationError(condition);
        if (parseError.isPresent()) {
            errors.add(path + ": " + parseError.get());
            return;
        }

        if (condition.type().equals(ALL_OF) || condition.type().equals(ANY_OF)) {
            CompositeConfig composite = CompositeConfig.CODEC.parse(condition.config()).result().orElseThrow();
            if (composite.conditions().isEmpty()) {
                errors.add(path + ".config.conditions: must contain at least one condition");
            }
            for (int index = 0; index < composite.conditions().size(); index++) {
                validateConfiguration(
                        composite.conditions().get(index),
                        path + ".config.conditions[" + index + "]",
                        depth + 1,
                        errors
                );
            }
        } else if (condition.type().equals(NOT)) {
            NotConfig not = NotConfig.CODEC.parse(condition.config()).result().orElseThrow();
            validateConfiguration(not.condition(), path + ".config.condition", depth + 1, errors);
        }
    }

    @FunctionalInterface
    public interface ConditionEvaluator<C> {
        ConditionEvaluation evaluate(EvaluationContext context, C config);
    }

    public record SkillLevelConfig(ResourceLocation skill, int level) {
        public static final Codec<SkillLevelConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("skill").forGetter(SkillLevelConfig::skill),
                Codec.intRange(0, 10_000).fieldOf("level").forGetter(SkillLevelConfig::level)
        ).apply(instance, SkillLevelConfig::new));
    }

    public record AbilityPurchasedConfig(ResourceLocation ability) {
        public static final Codec<AbilityPurchasedConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("ability").forGetter(AbilityPurchasedConfig::ability)
        ).apply(instance, AbilityPurchasedConfig::new));
    }

    public record AdvancementConfig(ResourceLocation advancement) {
        public static final Codec<AdvancementConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("advancement").forGetter(AdvancementConfig::advancement)
        ).apply(instance, AdvancementConfig::new));
    }

    public record CompositeConfig(List<TypedConfig> conditions) {
        public static final Codec<CompositeConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                TypedConfig.CODEC.listOf().fieldOf("conditions").forGetter(CompositeConfig::conditions)
        ).apply(instance, CompositeConfig::new));

        public CompositeConfig {
            conditions = List.copyOf(conditions);
        }
    }

    public record NotConfig(TypedConfig condition) {
        public static final Codec<NotConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                TypedConfig.CODEC.fieldOf("condition").forGetter(NotConfig::condition)
        ).apply(instance, NotConfig::new));
    }

    public record GameModeConfig(ResourceLocation gameMode) {
        public static final Codec<GameModeConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("game_mode").forGetter(GameModeConfig::gameMode)
        ).apply(instance, GameModeConfig::new));
    }

    public record DimensionConfig(ResourceLocation dimension) {
        public static final Codec<DimensionConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("dimension").forGetter(DimensionConfig::dimension)
        ).apply(instance, DimensionConfig::new));
    }

    public static final class EvaluationContext {
        private final ServerPlayer player;
        private final int depth;

        private EvaluationContext(ServerPlayer player, int depth) {
            this.player = player;
            this.depth = depth;
        }

        public ServerPlayer player() {
            return player;
        }

        public ConditionEvaluation evaluate(TypedConfig condition) {
            if (depth >= MAX_NESTING_DEPTH) {
                return ConditionEvaluation.invalid("Condition nesting exceeds " + MAX_NESTING_DEPTH + " levels");
            }
            RegisteredType<?> type = TYPES.get(condition.type());
            if (type == null) {
                return ConditionEvaluation.invalid("Unknown condition type: " + condition.type());
            }
            return type.evaluate(new EvaluationContext(player, depth + 1), condition);
        }

        private Optional<Integer> skillLevel(ResourceLocation skillId) {
            return ExperienceService.findSkill(player, skillId)
                    .map(definition -> definition.levelForExperience(
                            player.getData(ModAttachments.PLAYER_PROGRESS).skill(skillId).totalXp()
                    ));
        }

        private ConditionEvaluation all(List<TypedConfig> conditions) {
            if (conditions.isEmpty()) {
                return ConditionEvaluation.invalid("all_of requires at least one nested condition");
            }
            for (TypedConfig condition : conditions) {
                ConditionEvaluation result = evaluate(condition);
                if (!result.isSatisfied()) {
                    return result;
                }
            }
            return ConditionEvaluation.satisfied();
        }

        private ConditionEvaluation any(List<TypedConfig> conditions) {
            if (conditions.isEmpty()) {
                return ConditionEvaluation.invalid("any_of requires at least one nested condition");
            }
            ConditionEvaluation firstInvalid = null;
            for (TypedConfig condition : conditions) {
                ConditionEvaluation result = evaluate(condition);
                if (result.isSatisfied()) {
                    return result;
                }
                if (result.status() == ConditionEvaluation.Status.INVALID && firstInvalid == null) {
                    firstInvalid = result;
                }
            }
            return firstInvalid != null
                    ? firstInvalid
                    : ConditionEvaluation.notSatisfied("None of the any_of conditions are satisfied");
        }

        private ConditionEvaluation negate(TypedConfig condition) {
            ConditionEvaluation result = evaluate(condition);
            return switch (result.status()) {
                case SATISFIED -> ConditionEvaluation.notSatisfied("Negated condition is satisfied");
                case NOT_SATISFIED -> ConditionEvaluation.satisfied();
                case INVALID -> result;
            };
        }
    }

    private record RegisteredType<C>(Codec<C> codec, ConditionEvaluator<C> evaluator) {
        private Optional<String> validationError(TypedConfig condition) {
            StringBuilder error = new StringBuilder();
            Optional<C> parsed = codec.parse(condition.config()).resultOrPartial(message -> {
                if (!error.isEmpty()) {
                    error.append("; ");
                }
                error.append(message);
            });
            return parsed.isPresent()
                    ? Optional.empty()
                    : Optional.of("invalid config for " + condition.type() + ": " + error);
        }

        private ConditionEvaluation evaluate(EvaluationContext context, TypedConfig condition) {
            StringBuilder error = new StringBuilder();
            Optional<C> parsed = codec.parse(condition.config()).resultOrPartial(message -> {
                if (!error.isEmpty()) {
                    error.append("; ");
                }
                error.append(message);
            });
            if (parsed.isEmpty()) {
                return ConditionEvaluation.invalid(
                        "Invalid config for condition " + condition.type() + ": " + error
                );
            }
            return evaluator.evaluate(context, parsed.get());
        }
    }
}
