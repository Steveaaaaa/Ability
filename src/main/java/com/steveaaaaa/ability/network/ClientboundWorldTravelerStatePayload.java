package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.progress.WorldTravelerState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

public record ClientboundWorldTravelerStatePayload(Optional<GlobalPos> boundContainer,
        List<WorldTravelerState.FilterEntry> filters) implements CustomPacketPayload {
    public static final Type<ClientboundWorldTravelerStatePayload> TYPE =
            new Type<>(AbilityMod.id("world_traveler_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWorldTravelerStatePayload> STREAM_CODEC =
            StreamCodec.ofMember(ClientboundWorldTravelerStatePayload::encode,
                    ClientboundWorldTravelerStatePayload::decode);

    public ClientboundWorldTravelerStatePayload {
        filters = List.copyOf(filters);
    }

    public static ClientboundWorldTravelerStatePayload from(WorldTravelerState state) {
        return new ClientboundWorldTravelerStatePayload(state.boundContainer(), state.filters());
    }

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBoolean(boundContainer.isPresent());
        boundContainer.ifPresent(pos -> {
            ResourceLocation.STREAM_CODEC.encode(buffer, pos.dimension().location());
            buffer.writeBlockPos(pos.pos());
        });
        buffer.writeVarInt(filters.size());
        for (WorldTravelerState.FilterEntry entry : filters) {
            buffer.writeVarInt(entry.slot());
            ResourceLocation.STREAM_CODEC.encode(buffer, BuiltInRegistries.ITEM.getKey(entry.item()));
        }
    }

    private static ClientboundWorldTravelerStatePayload decode(RegistryFriendlyByteBuf buffer) {
        Optional<GlobalPos> bound = Optional.empty();
        if (buffer.readBoolean()) {
            ResourceLocation dimension = ResourceLocation.STREAM_CODEC.decode(buffer);
            BlockPos pos = buffer.readBlockPos();
            bound = Optional.of(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, dimension), pos));
        }
        int count = buffer.readVarInt();
        if (count < 0 || count > WorldTravelerState.FILTER_SLOTS) throw new IllegalArgumentException("Invalid filter count");
        ArrayList<WorldTravelerState.FilterEntry> filters = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int slot = buffer.readVarInt();
            ResourceLocation itemId = ResourceLocation.STREAM_CODEC.decode(buffer);
            filters.add(new WorldTravelerState.FilterEntry(slot,
                    BuiltInRegistries.ITEM.getOptional(itemId).orElseThrow(() ->
                            new IllegalArgumentException("Unknown filter item " + itemId))));
        }
        return new ClientboundWorldTravelerStatePayload(bound, filters);
    }
}
