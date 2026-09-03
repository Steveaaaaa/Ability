package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.effect.AttributeModifierEffect;
import com.steveaaaaa.ability.config.AbilityServerConfig;
import com.steveaaaaa.ability.progress.AbilityDailyState;
import com.steveaaaaa.ability.progress.ExperienceLimitState;
import com.steveaaaaa.ability.progress.ModAttachments;
import com.steveaaaaa.ability.progress.PlayerProgress;
import com.steveaaaaa.ability.progress.WorldTravelerState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID)
public final class PlayerProgressSyncEvents {
    private PlayerProgressSyncEvents() {
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        event.getRelevantPlayers().forEach(player -> {
            AttributeModifierEffect.reconcile(player);
            PlayerProgressSynchronizer.send(player);
        });
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!event.isWasDeath()) {
            copyAll(event, player);
            return;
        }

        PlayerProgress progress = event.getOriginal().getData(ModAttachments.PLAYER_PROGRESS).afterDeath(
                AbilityServerConfig.keepSkillProgressOnDeath(),
                AbilityServerConfig.keepLearnedAbilitiesOnDeath()
        );
        player.setData(ModAttachments.PLAYER_PROGRESS, progress);
        player.setData(
                ModAttachments.EXPERIENCE_LIMITS,
                AbilityServerConfig.keepExperienceLimitsOnDeath()
                        ? event.getOriginal().getData(ModAttachments.EXPERIENCE_LIMITS)
                        : ExperienceLimitState.EMPTY
        );
        player.setData(
                ModAttachments.ABILITY_DAILY_STATE,
                AbilityServerConfig.keepDailyAbilityStateOnDeath()
                        ? event.getOriginal().getData(ModAttachments.ABILITY_DAILY_STATE)
                        : AbilityDailyState.EMPTY
        );
        player.setData(
                ModAttachments.WORLD_TRAVELER_STATE,
                AbilityServerConfig.keepWorldTravelerStateOnDeath()
                        ? event.getOriginal().getData(ModAttachments.WORLD_TRAVELER_STATE)
                        : WorldTravelerState.EMPTY
        );
    }

    private static void copyAll(PlayerEvent.Clone event, ServerPlayer player) {
        player.setData(ModAttachments.PLAYER_PROGRESS, event.getOriginal().getData(ModAttachments.PLAYER_PROGRESS));
        player.setData(ModAttachments.EXPERIENCE_LIMITS, event.getOriginal().getData(ModAttachments.EXPERIENCE_LIMITS));
        player.setData(ModAttachments.ABILITY_DAILY_STATE, event.getOriginal().getData(ModAttachments.ABILITY_DAILY_STATE));
        player.setData(ModAttachments.WORLD_TRAVELER_STATE, event.getOriginal().getData(ModAttachments.WORLD_TRAVELER_STATE));
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AttributeModifierEffect.reconcile(player);
            PlayerProgressSynchronizer.send(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AttributeModifierEffect.reconcile(player);
            PlayerProgressSynchronizer.send(player);
        }
    }
}
