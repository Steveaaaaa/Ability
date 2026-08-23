package com.steveaaaaa.ability.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class PurchaseAbilityPayloadTest {
    private static final ResourceLocation ASSOCIATED_ORE =
            ResourceLocation.fromNamespaceAndPath("ability", "associated_ore");

    @Test
    void roundTripsPurchaseRequests() {
        ServerboundPurchaseAbilityPayload original = new ServerboundPurchaseAbilityPayload(ASSOCIATED_ORE);
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            ServerboundPurchaseAbilityPayload.STREAM_CODEC.encode(buffer, original);

            assertEquals(original, ServerboundPurchaseAbilityPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void roundTripsPurchaseResults() {
        ClientboundPurchaseResultPayload original = new ClientboundPurchaseResultPayload(
                ASSOCIATED_ORE,
                ClientboundPurchaseResultPayload.Status.NOT_ENOUGH_SKILL_POINTS,
                6,
                4,
                "Not enough skill points"
        );
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            ClientboundPurchaseResultPayload.STREAM_CODEC.encode(buffer, original);

            assertEquals(original, ClientboundPurchaseResultPayload.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    private static RegistryFriendlyByteBuf buffer() {
        return new RegistryFriendlyByteBuf(
                Unpooled.buffer(),
                RegistryAccess.EMPTY,
                ConnectionType.NEOFORGE
        );
    }
}
