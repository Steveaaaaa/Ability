package com.steveaaaaa.ability;

import com.mojang.logging.LogUtils;
import com.steveaaaaa.ability.command.AbilityCommands;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.network.AbilityNetwork;
import com.steveaaaaa.ability.progress.ModAttachments;
import com.steveaaaaa.ability.registry.ModItems;
import com.steveaaaaa.ability.loot.ModLootModifiers;
import com.steveaaaaa.ability.menu.ModMenus;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(AbilityMod.MOD_ID)
public final class AbilityMod {
    public static final String MOD_ID = "ability";
    public static final Logger LOGGER = LogUtils.getLogger();

    public AbilityMod(IEventBus modBus) {
        ModItems.ITEMS.register(modBus);
        ModLootModifiers.SERIALIZERS.register(modBus);
        ModMenus.MENUS.register(modBus);
        ModAttachments.ATTACHMENT_TYPES.register(modBus);
        modBus.addListener(ModDataRegistries::registerDatapackRegistries);
        modBus.addListener(AbilityNetwork::registerPayloads);
        NeoForge.EVENT_BUS.addListener(AbilityCommands::register);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
