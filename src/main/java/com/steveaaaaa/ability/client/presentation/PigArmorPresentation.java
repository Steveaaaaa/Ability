package com.steveaaaaa.ability.client.presentation;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class PigArmorPresentation {
    private static final Map<UUID, Long> ACTIVE = new HashMap<>();
    private static ClientLevel activeLevel;

    private PigArmorPresentation() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(AbilityMod.id("iron_cavalry"))
                || !cue.cueId().equals(AbilityMod.id("pig_armor"))) {
            return;
        }
        if (activeLevel != level) {
            clear(level);
        }
        Entity target = level.getEntity(cue.targetEntityId());
        if (!(target instanceof Pig pig)) {
            return;
        }
        if (cue.action() == AbilityCue.Action.STOP) {
            ACTIVE.remove(pig.getUUID());
        } else if (cue.action() == AbilityCue.Action.START) {
            int duration = cue.durationTicks() < 0 ? 30 : Math.max(1, cue.durationTicks());
            ACTIVE.put(pig.getUUID(), level.getGameTime() + duration);
        }
    }

    static boolean isActive(Pig pig) {
        return pig.level() == activeLevel
                && ACTIVE.getOrDefault(pig.getUUID(), Long.MIN_VALUE) > pig.level().getGameTime();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level != activeLevel) {
            clear(level);
            return;
        }
        long now = level.getGameTime();
        ACTIVE.entrySet().removeIf(entry -> now >= entry.getValue());
    }

    private static void clear(ClientLevel level) {
        ACTIVE.clear();
        activeLevel = level;
    }
}
