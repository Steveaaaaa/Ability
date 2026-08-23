package com.steveaaaaa.ability.ability.effect;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

public final class AbilityEffectTypeRegistry {
    private static final Map<ResourceLocation, Function<AbilityDefinition, List<String>>> VALIDATORS =
            new LinkedHashMap<>();

    static {
        register(AbilityMod.id("associated_ore"), BlockDropEffectTypeRegistry::validateDefinition);
        register(AttributeModifierEffect.TYPE, AttributeModifierEffect::validateDefinition);
        register(DamageModifierEffect.DAMAGE_MODIFIER, DamageModifierEffect::validateDefinition);
        register(DamageModifierEffect.DAMAGE_REDUCTION, DamageModifierEffect::validateDefinition);
        register(LootInjectionEffect.TYPE, LootInjectionEffect::validateDefinition);
    }

    private AbilityEffectTypeRegistry() {
    }

    public static synchronized void register(
            ResourceLocation id,
            Function<AbilityDefinition, List<String>> validator
    ) {
        if (VALIDATORS.putIfAbsent(id, validator) != null) {
            throw new IllegalArgumentException("Duplicate ability effect type: " + id);
        }
    }

    public static boolean isRegistered(ResourceLocation id) {
        return VALIDATORS.containsKey(id);
    }

    public static List<String> validateDefinition(AbilityDefinition definition) {
        Function<AbilityDefinition, List<String>> validator = VALIDATORS.get(definition.effect().type());
        if (validator == null) {
            return List.of("effect.type: unknown ability effect type " + definition.effect().type());
        }
        return validator.apply(definition);
    }
}
