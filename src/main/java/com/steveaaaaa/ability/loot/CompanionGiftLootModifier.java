package com.steveaaaaa.ability.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.ability.effect.CompanionGiftEffect;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

public final class CompanionGiftLootModifier extends LootModifier {
    public static final MapCodec<CompanionGiftLootModifier> CODEC = RecordCodecBuilder.mapCodec(instance ->
            codecStart(instance).apply(instance, CompanionGiftLootModifier::new)
    );

    public CompanionGiftLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        Entity entity = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        return entity == null ? generatedLoot : CompanionGiftEffect.addLoot(generatedLoot, context, entity);
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return ModLootModifiers.COMPANION_GIFT.get();
    }
}
