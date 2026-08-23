package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import com.steveaaaaa.ability.data.model.TypedConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

public final class CompositeEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("composite");

    private CompositeEffect() {
    }

    public static List<ComponentView> componentsOfType(AbilityDefinition definition, ResourceLocation type) {
        return components(definition).stream().filter(component -> component.type().equals(type)).toList();
    }

    public static List<ComponentView> components(AbilityDefinition definition) {
        if (!definition.effect().type().equals(TYPE)) {
            return List.of(new ComponentView(
                    -1,
                    definition.effect().type(),
                    definition.effect().config(),
                    definition.ranks().values()
            ));
        }
        Config config = parse(Config.CODEC, definition.effect().config(), "effect.config");
        ArrayList<ComponentView> components = new ArrayList<>();
        for (int index = 0; index < config.effects().size(); index++) {
            Component component = config.effects().get(index);
            components.add(new ComponentView(index, component.type(), component.config(), component.rankValues()));
        }
        return List.copyOf(components);
    }

    public static AbilityDefinition projectDefinition(AbilityDefinition definition, ComponentView component) {
        return new AbilityDefinition(
                definition.schemaVersion(),
                definition.skill(),
                definition.display(),
                definition.purchase(),
                new AbilityDefinition.Ranks(definition.ranks().unlockSkillLevels(), component.rankValues()),
                new TypedConfig(component.type(), component.config())
        );
    }

    public static AbilityService.ActiveAbility projectActive(
            AbilityService.ActiveAbility active,
            ComponentView component
    ) {
        AbilityDefinition projected = projectDefinition(active.definition(), component);
        return new AbilityService.ActiveAbility(
                active.abilityId(),
                projected,
                active.rank(),
                component.rankValues().subList(0, active.rank())
        );
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        ArrayList<String> errors = new ArrayList<>();
        Config config;
        try {
            config = parse(Config.CODEC, definition.effect().config(), "effect.config");
        } catch (IllegalArgumentException exception) {
            return List.of(exception.getMessage());
        }

        for (int index = 0; index < config.effects().size(); index++) {
            Component component = config.effects().get(index);
            int componentIndex = index;
            if (component.type().equals(TYPE)) {
                errors.add("effect.config.effects[" + index + "].type: nested composites are not supported");
                continue;
            }
            if (component.rankValues().size() != definition.ranks().values().size()) {
                errors.add(
                        "effect.config.effects[" + index + "].rank_values: expected "
                                + definition.ranks().values().size() + " entries but found "
                                + component.rankValues().size()
                );
                continue;
            }
            ComponentView view = new ComponentView(
                    index,
                    component.type(),
                    component.config(),
                    component.rankValues()
            );
            AbilityEffectTypeRegistry.validateDefinition(projectDefinition(definition, view)).stream()
                    .map(error -> "effect.config.effects[" + componentIndex + "]." + error)
                    .forEach(errors::add);
        }
        return List.copyOf(errors);
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

    public record Config(List<Component> effects) {
        private static final Codec<Config> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Component.CODEC.listOf().fieldOf("effects").forGetter(Config::effects)
        ).apply(instance, Config::new));
        public static final Codec<Config> CODEC = RAW_CODEC.flatXmap(Config::validate, Config::validate);

        public Config {
            effects = List.copyOf(effects);
        }

        private static DataResult<Config> validate(Config config) {
            if (config.effects().isEmpty()) {
                return DataResult.error(() -> "effects must contain at least one component");
            }
            if (config.effects().size() > 64) {
                return DataResult.error(() -> "effects cannot contain more than 64 components");
            }
            return DataResult.success(config);
        }
    }

    public record Component(ResourceLocation type, Dynamic<?> config, List<Dynamic<?>> rankValues) {
        public static final Codec<Component> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.fieldOf("type").forGetter(Component::type),
                Codec.PASSTHROUGH.fieldOf("config").forGetter(Component::config),
                Codec.PASSTHROUGH.listOf().fieldOf("rank_values").forGetter(Component::rankValues)
        ).apply(instance, Component::new));

        public Component {
            rankValues = List.copyOf(rankValues);
        }
    }

    public record ComponentView(
            int index,
            ResourceLocation type,
            Dynamic<?> config,
            List<Dynamic<?>> rankValues
    ) {
        public ComponentView {
            rankValues = List.copyOf(rankValues);
        }
    }
}
