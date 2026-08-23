package com.steveaaaaa.ability.trigger;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

public sealed interface ExperienceContext permits ExperienceContext.BlockBreak, ExperienceContext.BlockPlace,
        ExperienceContext.EntityKill, ExperienceContext.AnimalBreed, ExperienceContext.Movement,
        ExperienceContext.DamageTaken, ExperienceContext.ItemEnchanted {
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
            LivingEntity target,
            DamageSource source
    ) implements ExperienceContext {
        @Override
        public String targetKey() {
            return level.dimension().location() + ":entity:" + target.getUUID();
        }
    }

    record BlockPlace(
            ServerPlayer player,
            ServerLevel level,
            BlockPos pos,
            BlockState state
    ) implements ExperienceContext {
        public BlockPlace {
            pos = pos.immutable();
        }

        @Override
        public String targetKey() {
            return level.dimension().location() + ":placed_block:" + pos.asLong();
        }
    }

    record AnimalBreed(
            ServerPlayer player,
            ServerLevel level,
            Mob parentA,
            Mob parentB,
            AgeableMob child
    ) implements ExperienceContext {
        @Override
        public String targetKey() {
            return level.dimension().location() + ":breed:"
                    + (child == null ? parentA.getUUID() + ":" + parentB.getUUID() : child.getUUID());
        }
    }

    record Movement(
            ServerPlayer player,
            ServerLevel level,
            double distance,
            MovementMode mode,
            long window
    ) implements ExperienceContext {
        @Override
        public String targetKey() {
            return level.dimension().location() + ":movement:" + player.getUUID() + ":" + window;
        }
    }

    record DamageTaken(
            ServerPlayer player,
            ServerLevel level,
            DamageSource source,
            float damage
    ) implements ExperienceContext {
        @Override
        public String targetKey() {
            String damageType = source.typeHolder().unwrapKey()
                    .map(key -> key.location().toString())
                    .orElse("unregistered");
            String attacker = source.getEntity() == null ? "environment" : source.getEntity().getUUID().toString();
            return level.dimension().location() + ":damage:" + damageType + ":" + attacker;
        }
    }

    record ItemEnchanted(
            ServerPlayer player,
            ServerLevel level,
            ItemStack item,
            int enchantmentCount,
            int totalLevels,
            long window
    ) implements ExperienceContext {
        public ItemEnchanted {
            item = item.copy();
        }

        @Override
        public String targetKey() {
            return level.dimension().location() + ":enchant:" + player.getUUID() + ":" + window;
        }
    }

    enum MovementMode implements StringRepresentable {
        ON_FOOT("on_foot"),
        SWIMMING("swimming"),
        ELYTRA("elytra");

        private final String serializedName;

        MovementMode(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
