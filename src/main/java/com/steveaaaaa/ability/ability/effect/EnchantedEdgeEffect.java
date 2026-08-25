package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

public final class EnchantedEdgeEffect {
    public static final ResourceLocation TYPE = AbilityMod.id("enchanted_edge");
    private static final ResourceKey<DamageType> MAGIC = DamageTypes.MAGIC;
    private static final ResourceKey<DamageType> TRUE = ResourceKey.create(Registries.DAMAGE_TYPE,
            AbilityMod.id("true_damage"));
    private static final ThreadLocal<Boolean> APPLYING = ThreadLocal.withInitial(() -> false);
    private EnchantedEdgeEffect() {}

    static boolean isApplyingConvertedDamage() { return APPLYING.get(); }

    public static void modifyDamage(LivingIncomingDamageEvent event) {
        if (APPLYING.get() || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || event.getSource().getDirectEntity() != player) return;
        if (ChargedLeapEffect.isImpactAttack(event)) return;
        double fraction = activeFraction(player);
        if (fraction <= 0.0D) return;
        float original = event.getAmount();
        boolean execute = event.getEntity().getHealth() < player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        event.setCanceled(execute);
        if (!execute) event.setAmount((float) (original * (1.0D - fraction)));
        float converted = execute ? original : (float) (original * fraction);
        if (converted <= 0.0F) return;
        try {
            APPLYING.set(true);
            event.getEntity().invulnerableTime = 0;
            DamageSource source = new DamageSource(player.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                    .getHolderOrThrow(execute ? TRUE : MAGIC), player);
            event.getEntity().hurt(source, converted);
        } finally { APPLYING.set(false); }
    }

    static List<String> validateDefinition(AbilityDefinition definition) {
        try {
            for (int i = 0; i < definition.ranks().values().size(); i++) {
                Rank rank = parse(Rank.CODEC, definition.ranks().values().get(i), "ranks.values[" + i + "]");
                if (!Double.isFinite(rank.magicDamagePercent()) || rank.magicDamagePercent() < 0
                        || rank.magicDamagePercent() > 100) throw new IllegalArgumentException("magic_damage_percent must be between 0 and 100");
            }
            return List.of();
        } catch (RuntimeException exception) { return List.of(exception.getMessage()); }
    }

    private static double activeFraction(ServerPlayer player) {
        Registry<AbilityDefinition> registry = player.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
        return registry.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().location()))
                .mapToDouble(entry -> AbilityService.active(player, entry.getKey().location()).stream()
                        .flatMap(active -> CompositeEffect.componentsOfType(entry.getValue(), TYPE).stream()
                                .map(view -> CompositeEffect.projectActive(active, view)))
                        .mapToDouble(active -> parse(Rank.CODEC, active.unlockedRankValues().getLast(), "rank")
                                .magicDamagePercent() / 100.0D).max().orElse(0.0D))
                .max().orElse(0.0D);
    }

    private static <T> T parse(Codec<T> codec, Dynamic<?> input, String path) {
        StringBuilder error = new StringBuilder(); Optional<T> parsed = codec.parse(input).resultOrPartial(error::append);
        return parsed.orElseThrow(() -> new IllegalArgumentException(path + ": " + error));
    }
    public record Rank(double magicDamagePercent) {
        public static final Codec<Rank> CODEC = Codec.DOUBLE.fieldOf("magic_damage_percent")
                .xmap(Rank::new, Rank::magicDamagePercent).codec();
    }
}
