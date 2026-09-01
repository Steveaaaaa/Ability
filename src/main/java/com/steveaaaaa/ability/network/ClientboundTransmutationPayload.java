package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientboundTransmutationPayload(
        BlockPos position,
        ResourceLocation inputBlock,
        ResourceLocation outputBlock,
        boolean advanced,
        int rank,
        long randomSeed
) implements CustomPacketPayload {
    public static final Type<ClientboundTransmutationPayload> TYPE =
            new Type<>(AbilityMod.id("chorus_transmutation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundTransmutationPayload> STREAM_CODEC =
            StreamCodec.ofMember(ClientboundTransmutationPayload::encode, ClientboundTransmutationPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeBlockPos(position);
        ResourceLocation.STREAM_CODEC.encode(buffer, inputBlock);
        ResourceLocation.STREAM_CODEC.encode(buffer, outputBlock);
        buffer.writeBoolean(advanced);
        buffer.writeByte(rank);
        buffer.writeLong(randomSeed);
    }

    private static ClientboundTransmutationPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClientboundTransmutationPayload(
                buffer.readBlockPos(),
                ResourceLocation.STREAM_CODEC.decode(buffer),
                ResourceLocation.STREAM_CODEC.decode(buffer),
                buffer.readBoolean(),
                buffer.readUnsignedByte(),
                buffer.readLong()
        );
    }
}
