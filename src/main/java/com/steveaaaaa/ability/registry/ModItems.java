package com.steveaaaaa.ability.registry;

import com.steveaaaaa.ability.AbilityMod;
import java.util.function.Supplier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(
            BuiltInRegistries.ITEM,
            AbilityMod.MOD_ID
    );
    public static final Supplier<Item> COPPER_NUGGET = ITEMS.register(
            "copper_nugget",
            () -> new Item(new Item.Properties())
    );

    private ModItems() {
    }
}
