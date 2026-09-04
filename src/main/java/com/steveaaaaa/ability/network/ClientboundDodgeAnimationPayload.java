package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClientboundDodgeAnimationPayload(
        int playerEntityId,
        float motionX,
        float motionZ,
        int durationTicks
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
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(playerEntityId);
        buffer.writeFloat(motionX);
        buffer.writeFloat(motionZ);
        buffer.writeVarInt(durationTicks);
    }

    private static ClientboundDodgeAnimationPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClientboundDodgeAnimationPayload(
                buffer.readVarInt(),
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readVarInt()
        );
    }
}
