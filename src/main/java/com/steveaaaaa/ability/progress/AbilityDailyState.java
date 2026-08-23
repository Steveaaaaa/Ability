package com.steveaaaaa.ability.progress;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

public record AbilityDailyState(Map<ResourceLocation, Long> lastTriggerDays) {
    public static final AbilityDailyState EMPTY = new AbilityDailyState(Map.of());
    public static final Codec<AbilityDailyState> CODEC = Codec.unboundedMap(
            ResourceLocation.CODEC,
            Codec.LONG
    ).xmap(AbilityDailyState::new, AbilityDailyState::lastTriggerDays);

    public AbilityDailyState {
        lastTriggerDays = Map.copyOf(lastTriggerDays);
    }

    public boolean available(ResourceLocation abilityId, long currentDay) {
        return !lastTriggerDays.containsKey(abilityId) || lastTriggerDays.get(abilityId) != currentDay;
    }

    public AbilityDailyState consume(ResourceLocation abilityId, long currentDay) {
        if (!available(abilityId, currentDay)) {
            return this;
        }
        HashMap<ResourceLocation, Long> updated = new HashMap<>(lastTriggerDays);
        updated.put(abilityId, currentDay);
        return new AbilityDailyState(updated);
    }
}
