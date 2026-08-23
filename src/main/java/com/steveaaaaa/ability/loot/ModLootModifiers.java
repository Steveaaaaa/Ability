package com.steveaaaaa.ability.loot;

import com.mojang.serialization.MapCodec;
import com.steveaaaaa.ability.AbilityMod;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModLootModifiers {
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> SERIALIZERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, AbilityMod.MOD_ID);

    public static final DeferredHolder<MapCodec<? extends IGlobalLootModifier>, MapCodec<CompanionGiftLootModifier>>
            COMPANION_GIFT = SERIALIZERS.register("companion_gift", () -> CompanionGiftLootModifier.CODEC);

    private ModLootModifiers() {
    }
}
