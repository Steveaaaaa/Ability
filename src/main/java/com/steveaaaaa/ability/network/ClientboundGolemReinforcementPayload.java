package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClientboundGolemReinforcementPayload(
        UUID golemId,
        UUID ownerId,
        int charge,
        int chargeThreshold,
        int shields,
        int maxShields,
        VisualEvent visualEvent,
        float impactX,
        float impactY,
        float impactZ
) implements CustomPacketPayload {
    public static final Type<ClientboundGolemReinforcementPayload> TYPE =
            new Type<>(AbilityMod.id("golem_reinforcement"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundGolemReinforcementPayload> STREAM_CODEC =
            StreamCodec.ofMember(ClientboundGolemReinforcementPayload::encode, ClientboundGolemReinforcementPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(golemId);
        buffer.writeUUID(ownerId);
        buffer.writeVarInt(charge);
        buffer.writeVarInt(chargeThreshold);
        buffer.writeVarInt(shields);
        buffer.writeVarInt(maxShields);
        buffer.writeEnum(visualEvent);
        buffer.writeFloat(impactX);
        buffer.writeFloat(impactY);
        buffer.writeFloat(impactZ);
    }

    private static ClientboundGolemReinforcementPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClientboundGolemReinforcementPayload(
                buffer.readUUID(),
                buffer.readUUID(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readEnum(VisualEvent.class),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readFloat()
        );
    }

    public enum VisualEvent {
        SYNC,
        ACTIVATED,
        SHIELD_GAINED,
        SHIELD_BLOCKED
    }
}
