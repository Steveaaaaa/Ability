package com.steveaaaaa.ability.progress;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class PlayerPlacedBlockData extends SavedData {
    private static final String DATA_NAME = "ability_player_placed_blocks";
    private static final String POSITIONS_KEY = "positions";
    private static final Factory<PlayerPlacedBlockData> FACTORY =
            new Factory<>(PlayerPlacedBlockData::new, PlayerPlacedBlockData::load);

    private final Set<Long> positions = new HashSet<>();

    public static PlayerPlacedBlockData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public static PlayerPlacedBlockData load(CompoundTag tag, HolderLookup.Provider lookupProvider) {
        PlayerPlacedBlockData data = new PlayerPlacedBlockData();
        for (long position : tag.getLongArray(POSITIONS_KEY)) {
            data.positions.add(position);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray(POSITIONS_KEY, positions.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    public boolean contains(BlockPos pos) {
        return positions.contains(pos.asLong());
    }

    public void mark(BlockPos pos) {
        if (positions.add(pos.asLong())) {
            setDirty();
        }
    }

    public void remove(BlockPos pos) {
        if (positions.remove(pos.asLong())) {
            setDirty();
        }
    }
}
