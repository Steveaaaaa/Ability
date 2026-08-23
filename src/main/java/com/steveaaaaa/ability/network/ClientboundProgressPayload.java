package com.steveaaaaa.ability.network;

import com.steveaaaaa.ability.AbilityMod;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClientboundProgressPayload(PlayerProgressSnapshot snapshot) implements CustomPacketPayload {
    private static final int MAX_SKILLS = 4_096;
    private static final int MAX_PURCHASED_ABILITIES = 16_384;

    public static final Type<ClientboundProgressPayload> TYPE =
            new Type<>(AbilityMod.id("player_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ClientboundProgressPayload> STREAM_CODEC =
            StreamCodec.ofMember(ClientboundProgressPayload::encode, ClientboundProgressPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private void encode(RegistryFriendlyByteBuf buffer) {
        if (snapshot.skills().size() > MAX_SKILLS) {
            throw new EncoderException("Too many skill snapshots: " + snapshot.skills().size());
        }
        if (snapshot.purchasedAbilities().size() > MAX_PURCHASED_ABILITIES) {
            throw new EncoderException("Too many purchased abilities: " + snapshot.purchasedAbilities().size());
        }

        buffer.writeVarInt(snapshot.schemaVersion());
        buffer.writeVarInt(snapshot.legacyUnassignedSkillPoints());
        List<Map.Entry<ResourceLocation, PlayerProgressSnapshot.SkillSnapshot>> skills = snapshot.skills()
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();
        buffer.writeVarInt(skills.size());
        for (Map.Entry<ResourceLocation, PlayerProgressSnapshot.SkillSnapshot> entry : skills) {
            ResourceLocation.STREAM_CODEC.encode(buffer, entry.getKey());
            PlayerProgressSnapshot.SkillSnapshot skill = entry.getValue();
            buffer.writeVarLong(skill.totalXp());
            buffer.writeVarInt(skill.level());
            buffer.writeVarInt(skill.grantedSkillPoints());
            buffer.writeVarInt(skill.spentSkillPoints());
        }

        List<ResourceLocation> purchased = snapshot.purchasedAbilities().stream().sorted().toList();
        buffer.writeVarInt(purchased.size());
        purchased.forEach(id -> ResourceLocation.STREAM_CODEC.encode(buffer, id));
    }

    private static ClientboundProgressPayload decode(RegistryFriendlyByteBuf buffer) {
        int schemaVersion = readNonNegative(buffer, "schema version");
        int legacyPoints = readNonNegative(buffer, "legacy skill points");
        int skillCount = readBoundedCount(buffer, "skills", MAX_SKILLS);
        HashMap<ResourceLocation, PlayerProgressSnapshot.SkillSnapshot> skills = new HashMap<>(skillCount);
        for (int index = 0; index < skillCount; index++) {
            ResourceLocation skillId = ResourceLocation.STREAM_CODEC.decode(buffer);
            long totalXp = buffer.readVarLong();
            int level = readNonNegative(buffer, "skill level");
            int granted = readNonNegative(buffer, "granted skill points");
            int spent = readNonNegative(buffer, "spent skill points");
            if (totalXp < 0L) {
                throw new DecoderException("Negative skill experience for " + skillId);
            }
            if (skills.put(skillId, new PlayerProgressSnapshot.SkillSnapshot(
                    totalXp,
                    level,
                    granted,
                    spent
            )) != null) {
                throw new DecoderException("Duplicate skill snapshot: " + skillId);
            }
        }

        int purchasedCount = readBoundedCount(buffer, "purchased abilities", MAX_PURCHASED_ABILITIES);
        HashSet<ResourceLocation> purchased = new HashSet<>(purchasedCount);
        for (int index = 0; index < purchasedCount; index++) {
            ResourceLocation abilityId = ResourceLocation.STREAM_CODEC.decode(buffer);
            if (!purchased.add(abilityId)) {
                throw new DecoderException("Duplicate purchased ability: " + abilityId);
            }
        }
        return new ClientboundProgressPayload(new PlayerProgressSnapshot(
                schemaVersion,
                skills,
                purchased,
                legacyPoints
        ));
    }

    private static int readBoundedCount(RegistryFriendlyByteBuf buffer, String name, int maximum) {
        int value = buffer.readVarInt();
        if (value < 0 || value > maximum) {
            throw new DecoderException("Invalid " + name + " count: " + value);
        }
        return value;
    }

    private static int readNonNegative(RegistryFriendlyByteBuf buffer, String name) {
        int value = buffer.readVarInt();
        if (value < 0) {
            throw new DecoderException("Negative " + name + ": " + value);
        }
        return value;
    }
}
