package com.steveaaaaa.ability.trigger;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.progress.PlayerPlacedBlockData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEnchantItemEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID)
public final class ExperienceEventHandler {
    private ExperienceEventHandler() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        PlayerPlacedBlockData placedBlocks = PlayerPlacedBlockData.get(level);
        boolean playerPlaced = placedBlocks.contains(event.getPos());
        ExperiencePipeline.process(new ExperienceContext.BlockBreak(
                player,
                level,
                event.getPos(),
                event.getState(),
                playerPlaced
        ));
        placedBlocks.remove(event.getPos());
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        PlayerPlacedBlockData placedBlocks = PlayerPlacedBlockData.get(level);
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlace) {
            for (BlockSnapshot snapshot : multiPlace.getReplacedBlockSnapshots()) {
                placedBlocks.mark(snapshot.getPos());
                ExperiencePipeline.process(new ExperienceContext.BlockPlace(
                        player,
                        level,
                        snapshot.getPos(),
                        snapshot.getCurrentState()
                ));
            }
        } else {
            placedBlocks.mark(event.getPos());
            ExperiencePipeline.process(new ExperienceContext.BlockPlace(
                    player,
                    level,
                    event.getPos(),
                    event.getPlacedBlock()
            ));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        ExperiencePipeline.process(new ExperienceContext.EntityKill(
                player,
                level,
                event.getEntity(),
                event.getSource()
        ));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || event.getNewDamage() <= 0.0F) {
            return;
        }
        ExperiencePipeline.process(new ExperienceContext.DamageTaken(
                player,
                level,
                event.getSource(),
                event.getNewDamage()
        ));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerEnchantItem(PlayerEnchantItemEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)
                || event.getEnchantments().isEmpty()) {
            return;
        }
        int totalLevels = 0;
        for (var enchantment : event.getEnchantments()) {
            totalLevels = Math.min(10_000, totalLevels + Math.max(0, enchantment.level));
        }
        ExperiencePipeline.process(new ExperienceContext.ItemEnchanted(
                player,
                level,
                event.getEnchantedItem(),
                event.getEnchantments().size(),
                totalLevels,
                level.getGameTime()
        ));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBabyEntitySpawn(BabyEntitySpawnEvent event) {
        if (!(event.getCausedByPlayer() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ExperiencePipeline.process(new ExperienceContext.AnimalBreed(
                player,
                level,
                event.getParentA(),
                event.getParentB(),
                event.getChild()
        ));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MovementExperienceTracker.update(player).ifPresent(ExperiencePipeline::process);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            MovementExperienceTracker.forget(player);
        }
    }
}
