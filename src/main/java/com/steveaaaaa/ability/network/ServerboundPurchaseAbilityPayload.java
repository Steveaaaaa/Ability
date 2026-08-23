package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ServerboundPurchaseAbilityPayload(ResourceLocation abilityId) implements CustomPacketPayload {
    public static final Type<ServerboundPurchaseAbilityPayload> TYPE =
            new Type<>(AbilityMod.id("purchase_ability"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ServerboundPurchaseAbilityPayload> STREAM_CODEC =
            StreamCodec.ofMember(ServerboundPurchaseAbilityPayload::encode, ServerboundPurchaseAbilityPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        ResourceLocation.STREAM_CODEC.encode(buffer, abilityId);
    }

    private static ServerboundPurchaseAbilityPayload decode(RegistryFriendlyByteBuf buffer) {
        return new ServerboundPurchaseAbilityPayload(ResourceLocation.STREAM_CODEC.decode(buffer));
    }
}
