package com.steveaaaaa.ability.ability.effect;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

public final class AbilityTargeting {
    private AbilityTargeting() {
    }

    public static boolean canHarm(ServerPlayer player, LivingEntity target) {
        if (target == player || player.isAlliedTo(target) || target.isAlliedTo(player)) {
            return false;
        }
        return !(target instanceof ServerPlayer otherPlayer) || player.canHarmPlayer(otherPlayer);
    }
}
