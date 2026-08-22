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
import net.neoforged.neoforge.event.level.BlockEvent;

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
        if (!(event.getEntity() instanceof ServerPlayer)
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        PlayerPlacedBlockData placedBlocks = PlayerPlacedBlockData.get(level);
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlace) {
            for (BlockSnapshot snapshot : multiPlace.getReplacedBlockSnapshots()) {
                placedBlocks.mark(snapshot.getPos());
            }
        } else {
            placedBlocks.mark(event.getPos());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !(event.getEntity().level() instanceof ServerLevel level)) {
            return;
        }
        ExperiencePipeline.process(new ExperienceContext.EntityKill(player, level, event.getEntity()));
    }
}
