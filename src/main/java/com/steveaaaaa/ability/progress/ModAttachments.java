package com.steveaaaaa.ability.progress;

import com.steveaaaaa.ability.AbilityMod;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AbilityMod.MOD_ID);

    public static final Supplier<AttachmentType<PlayerProgress>> PLAYER_PROGRESS = ATTACHMENT_TYPES.register(
            "player_progress",
            () -> AttachmentType.builder(() -> PlayerProgress.EMPTY)
                    .serialize(PlayerProgress.CODEC)
                    .copyOnDeath()
                    .build()
    );

    private ModAttachments() {
    }
}
