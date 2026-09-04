package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClientboundDodgeAnimationPayload(
        int playerEntityId,
        float motionX,
        float motionZ,
        long startedAt,
        int durationTicks,
        float totalDistance,
        boolean backward
) implements CustomPacketPayload {
    public static final Type<ClientboundDodgeAnimationPayload> TYPE =
            new Type<>(AbilityMod.id("dodge_animation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundDodgeAnimationPayload> STREAM_CODEC =
            StreamCodec.ofMember(ClientboundDodgeAnimationPayload::encode, ClientboundDodgeAnimationPayload::decode);

    public ClientboundDodgeAnimationPayload {
        if (!Float.isFinite(motionX) || !Float.isFinite(motionZ)
                || (double) motionX * motionX + (double) motionZ * motionZ < 1.0E-8D) {
            throw new IllegalArgumentException("Dodge animation requires finite horizontal motion");
        }
        if (durationTicks < 1 || durationTicks > 100) {
            throw new IllegalArgumentException("Dodge animation duration must be between 1 and 100 ticks");
        }
        if (!Float.isFinite(totalDistance) || totalDistance < 0.0F || totalDistance > 12.0F) {
            throw new IllegalArgumentException("Dodge animation distance must be finite and between 0 and 12");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(playerEntityId);
        buffer.writeFloat(motionX);
        buffer.writeFloat(motionZ);
        buffer.writeVarLong(startedAt);
        buffer.writeVarInt(durationTicks);
        buffer.writeFloat(totalDistance);
        buffer.writeBoolean(backward);
    }

    private static ClientboundDodgeAnimationPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClientboundDodgeAnimationPayload(
                buffer.readVarInt(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readVarLong(),
                buffer.readVarInt(),
                buffer.readFloat(),
                buffer.readBoolean()
        );
    }
}
