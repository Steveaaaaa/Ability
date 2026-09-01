package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientboundWorldTravelerVisualPayload(
        Action action,
        int playerId,
        ResourceLocation destinationDimension,
        BlockPos destination,
        ResourceLocation itemId,
        int itemCount,
        boolean crossDimension,
        long randomSeed
) implements CustomPacketPayload {
    public static final Type<ClientboundWorldTravelerVisualPayload> TYPE =
            new Type<>(AbilityMod.id("world_traveler_visual"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundWorldTravelerVisualPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    ClientboundWorldTravelerVisualPayload::encode,
                    ClientboundWorldTravelerVisualPayload::decode
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeEnum(action);
        buffer.writeVarInt(playerId);
        ResourceLocation.STREAM_CODEC.encode(buffer, destinationDimension);
        buffer.writeBlockPos(destination);
        ResourceLocation.STREAM_CODEC.encode(buffer, itemId);
        buffer.writeVarInt(itemCount);
        buffer.writeBoolean(crossDimension);
        buffer.writeLong(randomSeed);
    }

    private static ClientboundWorldTravelerVisualPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClientboundWorldTravelerVisualPayload(
                buffer.readEnum(Action.class),
                buffer.readVarInt(),
                ResourceLocation.STREAM_CODEC.decode(buffer),
                buffer.readBlockPos(),
                ResourceLocation.STREAM_CODEC.decode(buffer),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readLong()
        );
    }

    public enum Action {
        BIND,
        ROUTE,
        REMOTE_OPEN
    }
}
