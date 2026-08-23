package com.steveaaaaa.ability.client.presentation;

import com.google.gson.JsonParser;
import com.steveaaaaa.ability.AbilityMod;
import java.io.Reader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class AbilityPresentationResources implements ResourceManagerReloadListener {
    private static final String DIRECTORY = "ability_presentations";
    private static volatile Map<ResourceLocation, AbilityPresentationDefinition> definitions = Map.of();

    @SubscribeEvent
    public static void register(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new AbilityPresentationResources());
    }

    public static AbilityPresentationDefinition.CueDefinition find(ResourceLocation abilityId, ResourceLocation cueId) {
        AbilityPresentationDefinition definition = definitions.get(abilityId);
        return definition == null ? null : definition.cues().get(cueId);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        Map<ResourceLocation, AbilityPresentationDefinition> loaded = new HashMap<>();
        resourceManager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).forEach((resourceId, resource) -> {
            try (Reader reader = resource.openAsReader()) {
                ResourceLocation abilityId = definitionId(resourceId);
                loaded.put(abilityId, AbilityPresentationDefinition.parse(JsonParser.parseReader(reader).getAsJsonObject()));
            } catch (Exception exception) {
                AbilityMod.LOGGER.error("Failed to load ability presentation {}", resourceId, exception);
            }
        });
        definitions = Map.copyOf(loaded);
        ClientAbilityPresentationManager.clearActive();
        AbilityMod.LOGGER.info("Loaded {} ability presentation definition(s)", loaded.size());
    }

    static ResourceLocation definitionId(ResourceLocation resourceId) {
        String path = resourceId.getPath();
        String prefix = DIRECTORY + "/";
        return ResourceLocation.fromNamespaceAndPath(
                resourceId.getNamespace(),
                path.substring(prefix.length(), path.length() - ".json".length())
        );
    }
}
