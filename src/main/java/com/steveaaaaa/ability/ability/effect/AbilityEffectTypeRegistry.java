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
        register(ConditionalMobEffect.TYPE, ConditionalMobEffect::validateDefinition);
        register(DamageResponseEffect.TYPE, DamageResponseEffect::validateDefinition);
        register(WeakPointEffect.TYPE, WeakPointEffect::validateDefinition);
        register(CounterSniperEffect.TYPE, CounterSniperEffect::validateDefinition);
        register(StealthEffect.TYPE, StealthEffect::validateDefinition);
        register(DodgeEffect.TYPE, DodgeEffect::validateDefinition);
        register(ChargedLeapEffect.TYPE, ChargedLeapEffect::validateDefinition);
        register(PrimerEffect.TYPE, PrimerEffect::validateDefinition);
        register(DangerousChargeEffect.TYPE, DangerousChargeEffect::validateDefinition);
        register(ExhaustionEffect.TYPE, ExhaustionEffect::validateDefinition);
        register(HarvestEffect.TYPE, HarvestEffect::validateDefinition);
        register(FrugalityEffect.TYPE, FrugalityEffect::validateDefinition);
        register(SurvivalSkillsEffect.TYPE, SurvivalSkillsEffect::validateDefinition);
        register(RetaliatoryFlameEffect.TYPE, RetaliatoryFlameEffect::validateDefinition);
        register(WellPreparedEffect.TYPE, WellPreparedEffect::validateDefinition);
        register(FineFeedEffect.TYPE, FineFeedEffect::validateDefinition);
        register(GreedEffect.TYPE, GreedEffect::validateDefinition);
        register(BlastExcavationEffect.TYPE, BlastExcavationEffect::validateDefinition);
        register(IronCavalryEffect.TYPE, IronCavalryEffect::validateDefinition);
        register(SupportAuraEffect.TYPE, SupportAuraEffect::validateDefinition);
        register(WolfPackEffect.TYPE, WolfPackEffect::validateDefinition);
        register(CompanionGiftEffect.TYPE, CompanionGiftEffect::validateDefinition);
        register(ChorusTransmutationEffect.TYPE, ChorusTransmutationEffect::validateDefinition);
        register(CeilingWireEffect.TYPE, CeilingWireEffect::validateDefinition);
        register(WorldTravelerEffect.TYPE, WorldTravelerEffect::validateDefinition);
        register(GolemEnhancementEffect.CRUSHING_BLOW, GolemEnhancementEffect::validateCrushingDefinition);
        register(GolemEnhancementEffect.OBSIDIAN_REINFORCEMENT, GolemEnhancementEffect::validateObsidianDefinition);
        register(GolemEnhancementEffect.COLD_CURRENT, GolemEnhancementEffect::validateColdDefinition);
        register(EnchantedEdgeEffect.TYPE, EnchantedEdgeEffect::validateDefinition);
        register(CompositeEffect.TYPE, CompositeEffect::validateDefinition);
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
