package com.steveaaaaa.ability.trigger;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;

public sealed interface ExperienceContext permits ExperienceContext.BlockBreak, ExperienceContext.EntityKill {
    ServerPlayer player();

    ServerLevel level();

    String targetKey();

    record BlockBreak(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            boolean playerPlaced
    ) implements ExperienceContext {
        public BlockBreak {
            pos = pos.immutable();
        }

        @Override
        public String targetKey() {
            return level.dimension().location() + ":block:" + pos.asLong();
        }
    }

    record EntityKill(
            ServerPlayer player,
            ServerLevel level,
            LivingEntity target
    ) implements ExperienceContext {
        @Override
        public String targetKey() {
            return level.dimension().location() + ":entity:" + target.getUUID();
        }
    }
}
