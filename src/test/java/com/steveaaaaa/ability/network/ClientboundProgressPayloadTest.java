package com.steveaaaaa.ability.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.connection.ConnectionType;
import org.junit.jupiter.api.Test;

class ClientboundProgressPayloadTest {
    private static final ResourceLocation MINING = ResourceLocation.fromNamespaceAndPath("ability", "mining");
    private static final ResourceLocation FARMING = ResourceLocation.fromNamespaceAndPath("ability", "farming");
    private static final ResourceLocation ASSOCIATED_ORE =
            ResourceLocation.fromNamespaceAndPath("ability", "associated_ore");

    @Test
    void roundTripsTheCompleteSnapshot() {
        PlayerProgressSnapshot snapshot = new PlayerProgressSnapshot(
                PlayerProgressSnapshot.CURRENT_SCHEMA_VERSION,
                Map.of(
                        MINING, new PlayerProgressSnapshot.SkillSnapshot(3_675L, 12, 12, 6),
                        FARMING, new PlayerProgressSnapshot.SkillSnapshot(500L, 4, 4, 1)
                ),
                Set.of(ASSOCIATED_ORE),
                2
        );
        ClientboundProgressPayload original = new ClientboundProgressPayload(snapshot);
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            ClientboundProgressPayload.STREAM_CODEC.encode(buffer, original);
            ClientboundProgressPayload decoded = ClientboundProgressPayload.STREAM_CODEC.decode(buffer);

            assertEquals(original, decoded);
            assertEquals(8, decoded.snapshot().availableSkillPoints(MINING));
            assertEquals(5, decoded.snapshot().availableSkillPoints(FARMING));
            assertEquals(0, buffer.readableBytes());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectsOversizedSkillCollections() {
        RegistryFriendlyByteBuf buffer = buffer();
        try {
            buffer.writeVarInt(PlayerProgressSnapshot.CURRENT_SCHEMA_VERSION);
            buffer.writeVarInt(0);
            buffer.writeVarInt(4_097);

            assertThrows(DecoderException.class, () -> ClientboundProgressPayload.STREAM_CODEC.decode(buffer));
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
