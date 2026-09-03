package com.steveaaaaa.ability.client;

import com.steveaaaaa.ability.network.ClientboundPurchaseResultPayload;
import net.minecraft.network.chat.Component;

final class PurchaseFeedback {
    private PurchaseFeedback() {}

    static Component message(ClientboundPurchaseResultPayload result) {
        return message(result, null);
    }

    static Component message(ClientboundPurchaseResultPayload result, Component localizedRequirement) {
        String abilityId = result.abilityId().toString();
        return switch (result.status()) {
            case SUCCESS -> Component.translatable(
                    "command.fantasypower.purchase.success", abilityId, result.required(), result.actual());
            case UNKNOWN_ABILITY -> Component.translatable("gui.fantasypower.purchase.unknown", abilityId);
            case ALREADY_PURCHASED -> Component.translatable(
                    "command.fantasypower.purchase.already_purchased", abilityId);
            case MAX_RANK -> Component.translatable("command.fantasypower.purchase.max_rank", abilityId);
            case SKILL_LEVEL_TOO_LOW -> Component.translatable(
                    "command.fantasypower.purchase.skill_level_too_low", result.required(), result.actual());
            case NOT_ENOUGH_SKILL_POINTS -> Component.translatable(
                    "command.fantasypower.purchase.not_enough_points", result.required(), result.actual());
            case REQUIREMENT_NOT_MET -> Component.translatable(
                    "command.fantasypower.purchase.requirement_not_met",
                    localizedRequirement == null ? Component.literal(result.detail()) : localizedRequirement);
            case INVALID_DEFINITION -> Component.translatable(
                    "command.fantasypower.purchase.invalid_definition", result.detail());
        };
    }
}
