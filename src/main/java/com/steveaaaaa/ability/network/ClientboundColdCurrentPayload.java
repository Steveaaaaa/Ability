package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClientboundColdCurrentPayload(
        UUID golemId,
        UUID ownerId,
        int ageTicks,
        int finalThresholdTicks,
        int stage
) implements CustomPacketPayload {
    public static final Type<ClientboundColdCurrentPayload> TYPE = new Type<>(AbilityMod.id("cold_current_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundColdCurrentPayload> STREAM_CODEC =
            StreamCodec.ofMember(ClientboundColdCurrentPayload::encode, ClientboundColdCurrentPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(golemId);
        buffer.writeUUID(ownerId);
        buffer.writeVarInt(ageTicks);
        buffer.writeVarInt(finalThresholdTicks);
        buffer.writeVarInt(stage);
    }

    private static ClientboundColdCurrentPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClientboundColdCurrentPayload(
                buffer.readUUID(), buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt()
        );
    }
}
