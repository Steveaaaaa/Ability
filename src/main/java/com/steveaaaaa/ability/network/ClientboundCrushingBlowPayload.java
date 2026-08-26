package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClientboundCrushingBlowPayload(
        UUID golemId,
        UUID ownerId,
        int charge,
        int chargeThreshold,
        int damagePercent,
        VisualEvent visualEvent,
        float impactX,
        float impactY,
        float impactZ
) implements CustomPacketPayload {
    public static final Type<ClientboundCrushingBlowPayload> TYPE =
            new Type<>(AbilityMod.id("crushing_blow_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundCrushingBlowPayload> STREAM_CODEC =
            StreamCodec.ofMember(ClientboundCrushingBlowPayload::encode, ClientboundCrushingBlowPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(golemId);
        buffer.writeUUID(ownerId);
        buffer.writeVarInt(charge);
        buffer.writeVarInt(chargeThreshold);
        buffer.writeVarInt(damagePercent);
        buffer.writeEnum(visualEvent);
        buffer.writeFloat(impactX);
        buffer.writeFloat(impactY);
        buffer.writeFloat(impactZ);
    }

    private static ClientboundCrushingBlowPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClientboundCrushingBlowPayload(
                buffer.readUUID(), buffer.readUUID(), buffer.readVarInt(), buffer.readVarInt(), buffer.readVarInt(),
                buffer.readEnum(VisualEvent.class), buffer.readFloat(), buffer.readFloat(), buffer.readFloat()
        );
    }

    public enum VisualEvent {
        SYNC,
        ACTIVATED,
        CHARGED,
        RELEASED
    }
}
