package com.steveaaaaa.ability.menu;

import com.steveaaaaa.ability.AbilityMod;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, AbilityMod.MOD_ID);
    public static final Supplier<MenuType<WorldTravelerRemoteMenu>> WORLD_TRAVELER_REMOTE = MENUS.register(
            "world_traveler_remote", () -> IMenuTypeExtension.create(WorldTravelerRemoteMenu::new));
    private ModMenus() {}
}
