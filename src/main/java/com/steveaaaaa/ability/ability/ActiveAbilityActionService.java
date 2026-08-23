package com.steveaaaaa.ability.ability;

import com.steveaaaaa.ability.ability.effect.DodgeEffect;
import com.steveaaaaa.ability.ability.effect.ChargedLeapEffect;
import com.steveaaaaa.ability.ability.effect.PrimerEffect;
import com.steveaaaaa.ability.ability.effect.CeilingWireEffect;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class ActiveAbilityActionService {
    private ActiveAbilityActionService() {
    }

    public static ActivationResult activate(
            ServerPlayer player,
            ResourceLocation abilityId,
            ActiveAbilityInput input
    ) {
        AbilityService.ActiveAbility active = AbilityService.active(player, abilityId).orElse(null);
        if (active == null) {
            return ActivationResult.INACTIVE;
        }
        try {
            if (DodgeEffect.supports(active.definition())) {
                return DodgeEffect.activate(player, active, input);
            }
            if (ChargedLeapEffect.supports(active.definition())) {
                return ChargedLeapEffect.activate(player, active, input);
            }
            if (PrimerEffect.supports(active.definition())) {
                return PrimerEffect.activate(player, active, input);
            }
            if (CeilingWireEffect.supports(active.definition())) {
                return CeilingWireEffect.activate(player, active, input);
            }
        } catch (RuntimeException exception) {
            return ActivationResult.INVALID_DEFINITION;
        }
        return ActivationResult.UNSUPPORTED_ACTION;
    }

    public enum ActivationResult {
        SUCCESS,
        INACTIVE,
        UNSUPPORTED_ACTION,
        INVALID_STATE,
        COOLDOWN,
        INVALID_DEFINITION
    }
}
