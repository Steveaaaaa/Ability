package com.steveaaaaa.ability.client.presentation;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.phys.Vec3;

public record AbilityPresentationDefinition(Map<ResourceLocation, CueDefinition> cues) {
    private static final int MAX_PARTICLE_COUNT = 512;
    private static final int MAX_DURATION_TICKS = 20 * 60 * 10;

    public AbilityPresentationDefinition {
        cues = Map.copyOf(cues);
    }

    public static AbilityPresentationDefinition parse(JsonObject root) {
        JsonObject cuesObject = GsonHelper.getAsJsonObject(root, "cues");
        var parsed = new java.util.HashMap<ResourceLocation, CueDefinition>();
        for (Map.Entry<String, JsonElement> entry : cuesObject.entrySet()) {
            ResourceLocation cueId = ResourceLocation.tryParse(entry.getKey());
            if (cueId == null) {
                throw new JsonParseException("Invalid cue id: " + entry.getKey());
            }
            parsed.put(cueId, parseCue(GsonHelper.convertToJsonObject(entry.getValue(), entry.getKey())));
        }
        return new AbilityPresentationDefinition(parsed);
    }

    private static CueDefinition parseCue(JsonObject json) {
        int duration = boundedInt(json, "duration_ticks", 0, 0, MAX_DURATION_TICKS);
        int interval = boundedInt(json, "emission_interval_ticks", 1, 1, MAX_DURATION_TICKS);
        List<ParticleBurst> particles = new ArrayList<>();
        JsonArray particleArray = GsonHelper.getAsJsonArray(json, "particles", new JsonArray());
        for (JsonElement element : particleArray) {
            JsonObject particle = GsonHelper.convertToJsonObject(element, "particle");
            particles.add(new ParticleBurst(
                    requiredId(particle, "type"),
                    Anchor.parse(GsonHelper.getAsString(particle, "anchor", "position")),
                    boundedInt(particle, "count", 1, 1, MAX_PARTICLE_COUNT),
                    vec3(particle, "offset", Vec3.ZERO),
                    vec3(particle, "spread", Vec3.ZERO),
                    vec3(particle, "velocity", Vec3.ZERO),
                    finiteFloat(particle, "direction_speed", 0.0F, 0.0F),
                    GsonHelper.getAsBoolean(particle, "force", false)
            ));
        }
        Optional<SoundCue> sound = Optional.empty();
        if (json.has("sound")) {
            JsonObject value = GsonHelper.getAsJsonObject(json, "sound");
            sound = Optional.of(new SoundCue(
                    requiredId(value, "event"),
                    Anchor.parse(GsonHelper.getAsString(value, "anchor", "position")),
                    finiteFloat(value, "volume", 1.0F, 0.0F),
                    finiteFloat(value, "pitch", 1.0F, 0.01F),
                    finiteFloat(value, "pitch_random", 0.0F, 0.0F)
            ));
        }
        Optional<ResourceLocation> animation = Optional.empty();
        if (json.has("animation")) {
            animation = Optional.of(requiredId(json, "animation"));
        }
        return new CueDefinition(duration, interval, List.copyOf(particles), sound, animation);
    }

    private static ResourceLocation requiredId(JsonObject json, String name) {
        String raw = GsonHelper.getAsString(json, name);
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            throw new JsonParseException("Invalid resource location for " + name + ": " + raw);
        }
        return id;
    }

    private static int boundedInt(JsonObject json, String name, int fallback, int min, int max) {
        int value = GsonHelper.getAsInt(json, name, fallback);
        if (value < min || value > max) {
            throw new JsonParseException(name + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static float finiteFloat(JsonObject json, String name, float fallback, float min) {
        float value = GsonHelper.getAsFloat(json, name, fallback);
        if (!Float.isFinite(value) || value < min) {
            throw new JsonParseException(name + " must be finite and at least " + min);
        }
        return value;
    }

    private static Vec3 vec3(JsonObject json, String name, Vec3 fallback) {
        if (!json.has(name)) {
            return fallback;
        }
        JsonArray array = GsonHelper.getAsJsonArray(json, name);
        if (array.size() != 3) {
            throw new JsonParseException(name + " must contain exactly three numbers");
        }
        Vec3 result = new Vec3(array.get(0).getAsDouble(), array.get(1).getAsDouble(), array.get(2).getAsDouble());
        if (!Double.isFinite(result.x) || !Double.isFinite(result.y) || !Double.isFinite(result.z)) {
            throw new JsonParseException(name + " must contain finite numbers");
        }
        return result;
    }

    public record CueDefinition(
            int durationTicks,
            int emissionIntervalTicks,
            List<ParticleBurst> particles,
            Optional<SoundCue> sound,
            Optional<ResourceLocation> animation
    ) {
    }

    public record ParticleBurst(
            ResourceLocation type,
            Anchor anchor,
            int count,
            Vec3 offset,
            Vec3 spread,
            Vec3 velocity,
            float directionSpeed,
            boolean force
    ) {
    }

    public record SoundCue(
            ResourceLocation event,
            Anchor anchor,
            float volume,
            float pitch,
            float pitchRandom
    ) {
    }

    public enum Anchor {
        POSITION,
        SOURCE,
        TARGET;

        private static Anchor parse(String value) {
            try {
                return valueOf(value.toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new JsonParseException("Unknown presentation anchor: " + value);
            }
        }
    }
}
