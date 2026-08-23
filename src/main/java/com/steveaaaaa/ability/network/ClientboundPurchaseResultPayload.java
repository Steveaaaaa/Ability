package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientboundPurchaseResultPayload(
        ResourceLocation abilityId,
        Status status,
        int required,
        int actual,
        String detail
) implements CustomPacketPayload {
    private static final int MAX_STATUS_LENGTH = 64;
    private static final int MAX_DETAIL_LENGTH = 1_024;

    public static final Type<ClientboundPurchaseResultPayload> TYPE =
            new Type<>(AbilityMod.id("purchase_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundPurchaseResultPayload> STREAM_CODEC =
            StreamCodec.ofMember(ClientboundPurchaseResultPayload::encode, ClientboundPurchaseResultPayload::decode);

    public ClientboundPurchaseResultPayload {
        if (required < 0 || actual < 0) {
            throw new IllegalArgumentException("Purchase result counters must be non-negative");
        }
        if (detail.length() > MAX_DETAIL_LENGTH) {
            throw new IllegalArgumentException("Purchase result detail is too long");
        }
    }

    public static ClientboundPurchaseResultPayload from(AbilityService.PurchaseResult result) {
        String detail = result.detail().length() <= MAX_DETAIL_LENGTH
                ? result.detail()
                : result.detail().substring(0, MAX_DETAIL_LENGTH);
        return new ClientboundPurchaseResultPayload(
                result.abilityId(),
                Status.valueOf(result.status().name()),
                result.required(),
                result.actual(),
                detail
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        ResourceLocation.STREAM_CODEC.encode(buffer, abilityId);
        buffer.writeUtf(status.name(), MAX_STATUS_LENGTH);
        buffer.writeVarInt(required);
        buffer.writeVarInt(actual);
        buffer.writeUtf(detail, MAX_DETAIL_LENGTH);
    }

    private static ClientboundPurchaseResultPayload decode(RegistryFriendlyByteBuf buffer) {
        ResourceLocation abilityId = ResourceLocation.STREAM_CODEC.decode(buffer);
        Status status;
        try {
            status = Status.valueOf(buffer.readUtf(MAX_STATUS_LENGTH));
        } catch (IllegalArgumentException exception) {
            throw new DecoderException("Unknown ability purchase status", exception);
        }
        int required = buffer.readVarInt();
        int actual = buffer.readVarInt();
        if (required < 0 || actual < 0) {
            throw new DecoderException("Negative ability purchase result counter");
        }
        return new ClientboundPurchaseResultPayload(
                abilityId,
                status,
                required,
                actual,
                buffer.readUtf(MAX_DETAIL_LENGTH)
        );
    }

    public enum Status {
        SUCCESS,
        UNKNOWN_ABILITY,
        ALREADY_PURCHASED,
        SKILL_LEVEL_TOO_LOW,
        NOT_ENOUGH_SKILL_POINTS,
        REQUIREMENT_NOT_MET,
        INVALID_DEFINITION
    }
}
