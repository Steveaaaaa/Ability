package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.ActiveAbilityInput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundActivateAbilityPayload(
        ResourceLocation abilityId,
        ActiveAbilityInput input
) implements CustomPacketPayload {
    public static final Type<ServerboundActivateAbilityPayload> TYPE =
            new Type<>(AbilityMod.id("activate_ability"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundActivateAbilityPayload> STREAM_CODEC =
            StreamCodec.ofMember(
                    ServerboundActivateAbilityPayload::encode,
                    ServerboundActivateAbilityPayload::decode
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        ResourceLocation.STREAM_CODEC.encode(buffer, abilityId);
        buffer.writeByte(input.networkId());
    }

    private static ServerboundActivateAbilityPayload decode(RegistryFriendlyByteBuf buffer) {
        ResourceLocation abilityId = ResourceLocation.STREAM_CODEC.decode(buffer);
        ActiveAbilityInput input = ActiveAbilityInput.fromNetworkId(buffer.readUnsignedByte());
        return new ServerboundActivateAbilityPayload(abilityId, input);
    }
}
