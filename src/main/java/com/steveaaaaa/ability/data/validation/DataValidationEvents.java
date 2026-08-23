package com.steveaaaaa.ability.data.validation;

import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID)
public final class DataValidationEvents {
    private DataValidationEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        validateAndLog(event.getServer(), "server startup");
    }

    @SubscribeEvent
    public static void onDatapackReload(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            validateAndLog(event.getPlayerList().getServer(), "datapack reload");
        }
    }

    private static void validateAndLog(MinecraftServer server, String reason) {
        DataValidationReport report = DataDefinitionValidator.validate(server);
        if (report.valid()) {
            AbilityMod.LOGGER.info("Ability data validation passed after {}", reason);
            return;
        }

        AbilityMod.LOGGER.error(
                "Ability data validation found {} error(s) after {}",
                report.errorCount(),
                reason
        );
        report.diagnostics().forEach(diagnostic ->
                AbilityMod.LOGGER.error("- {}", diagnostic.displayText())
        );
    }
}
