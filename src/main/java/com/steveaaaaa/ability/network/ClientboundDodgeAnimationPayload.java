package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.ActiveAbilityInput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record ClientboundDodgeAnimationPayload(
        int playerEntityId,
        ActiveAbilityInput direction
) implements CustomPacketPayload {
    public static final Type<ClientboundDodgeAnimationPayload> TYPE =
            new Type<>(AbilityMod.id("dodge_animation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundDodgeAnimationPayload> STREAM_CODEC =
            StreamCodec.ofMember(ClientboundDodgeAnimationPayload::encode, ClientboundDodgeAnimationPayload::decode);

    public ClientboundDodgeAnimationPayload {
        if (!isDirection(direction)) {
            throw new IllegalArgumentException("Dodge animation requires a directional input");
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(playerEntityId);
        buffer.writeByte(direction.networkId());
    }

    private static ClientboundDodgeAnimationPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ClientboundDodgeAnimationPayload(
                buffer.readVarInt(),
                ActiveAbilityInput.fromNetworkId(buffer.readUnsignedByte())
        );
    }

    private static boolean isDirection(ActiveAbilityInput input) {
        return input == ActiveAbilityInput.FORWARD
                || input == ActiveAbilityInput.BACKWARD
                || input == ActiveAbilityInput.LEFT
                || input == ActiveAbilityInput.RIGHT
                || input == ActiveAbilityInput.FORWARD_LEFT
                || input == ActiveAbilityInput.FORWARD_RIGHT
                || input == ActiveAbilityInput.BACKWARD_LEFT
                || input == ActiveAbilityInput.BACKWARD_RIGHT;
    }
}
