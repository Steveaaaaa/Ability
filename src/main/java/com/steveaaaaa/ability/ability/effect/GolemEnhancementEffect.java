package com.steveaaaaa.ability.ability.effect;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.ability.AbilityService;
import com.steveaaaaa.ability.data.ModDataRegistries;
import com.steveaaaaa.ability.data.model.AbilityDefinition;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public final class GolemEnhancementEffect {
    public static final ResourceLocation CRUSHING_BLOW = AbilityMod.id("crushing_blow");
    public static final ResourceLocation OBSIDIAN_REINFORCEMENT = AbilityMod.id("obsidian_reinforcement");
    public static final ResourceLocation COLD_CURRENT = AbilityMod.id("cold_current");
    private static final String ROOT = "ability_enhancements";
    private static final ResourceLocation COLD_ATTACK_FLAT = AbilityMod.id("cold_current/attack_flat");
    private static final ResourceLocation COLD_ATTACK_PERCENT = AbilityMod.id("cold_current/attack_percent");
    private static final ResourceLocation COLD_HEALTH = AbilityMod.id("cold_current/health");
    private static final ResourceLocation COLD_ARMOR_FLAT = AbilityMod.id("cold_current/armor_flat");
    private static final ResourceLocation COLD_ARMOR_PERCENT = AbilityMod.id("cold_current/armor_percent");
    private static final ResourceLocation COLD_TOUGHNESS = AbilityMod.id("cold_current/toughness");
    private GolemEnhancementEffect() {}

    public static void enhance(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        Entity target = event.getTarget();
        for (ActiveComponent component : activeComponents(player)) {
            boolean targetMatches = component.type().equals(COLD_CURRENT) ? target instanceof SnowGolem : target instanceof IronGolem;
            if (!targetMatches || component.config().items().stream().noneMatch(event.getItemStack()::is)
                    || event.getItemStack().getCount() < component.config().itemCost()) continue;
            CompoundTag root = target.getPersistentData().getCompound(ROOT);
            CompoundTag state = new CompoundTag();
            state.putUUID("owner", player.getUUID());
            component.rank().forEach(state::putDouble);
            state.putInt("charge", 0); state.putInt("shields", 0); state.putInt("age", 0);
            root.put(component.type().getPath(), state);
            target.getPersistentData().put(ROOT, root);
            if (!player.getAbilities().instabuild) event.getItemStack().shrink(component.config().itemCost());
            event.setCancellationResult(InteractionResult.SUCCESS); event.setCanceled(true); return;
        }
    }

    public static void blockWithObsidianShield(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        CompoundTag state = state(entity, OBSIDIAN_REINFORCEMENT);
        if (state == null || state.getInt("shields") <= 0) return;
        state.putInt("shields", state.getInt("shields") - 1);
        state.putInt("charge", Math.min(9, state.getInt("charge") + (int) state.getDouble("recharge")));
        entity.heal((float) (entity.getMaxHealth() * state.getDouble("heal_percent") / 100.0D));
        event.setCanceled(true);
    }

    public static void processFinalDamage(LivingDamageEvent.Post event) {
        if (!(event.getEntity() instanceof IronGolem golem) || event.getNewDamage() <= 0) return;
        CompoundTag state = state(golem, CRUSHING_BLOW);
        if (state == null) return;
        int charge = state.getInt("charge") + 1;
        int threshold = (int) state.getDouble("charge_threshold");
        if (charge < threshold) { state.putInt("charge", charge); return; }
        state.putInt("charge", 0); golem.heal(golem.getMaxHealth() * 0.05F);
        ServerPlayer owner = owner(golem, state);
        if (owner == null || !(golem.level() instanceof ServerLevel level)) return;
        float damage = (float) (owner.getAttributeValue(Attributes.ATTACK_DAMAGE)
                * state.getDouble("damage_percent") / 100.0D);
        level.getEntitiesOfClass(LivingEntity.class, new AABB(golem.blockPosition()).inflate(7.5D),
                living -> living != golem && living.isAlive() && !(living instanceof IronGolem)
                        && !(living instanceof AbstractVillager))
                .forEach(living -> { living.invulnerableTime = 0; living.hurt(golem.damageSources().mobAttack(golem), damage); });
    }

    public static void processTick(Entity entity) {
        if (!(entity instanceof LivingEntity living) || !(entity instanceof IronGolem || entity instanceof SnowGolem)) return;
        if (entity instanceof IronGolem golem && golem.tickCount % 20 == 0 && golem.getTarget() != null) {
            CompoundTag state = state(golem, OBSIDIAN_REINFORCEMENT);
            if (state != null) {
                int charge = state.getInt("charge") + 1;
                if (charge >= 9 && state.getInt("shields") < (int) state.getDouble("max_shields")) {
                    state.putInt("shields", state.getInt("shields") + 1); charge = 0;
                }
                state.putInt("charge", charge);
            }
        }
        if (entity instanceof SnowGolem snow) processCold(snow);
    }

    public static void processSnowballImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof Snowball snowball)
                || !(snowball.getOwner() instanceof SnowGolem golem)
                || !(event.getRayTraceResult() instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof LivingEntity target)) return;
        CompoundTag state = state(golem, COLD_CURRENT);
        if (state == null) return;
        int reduction = (int) (state.getDouble("time_reduction_seconds") * 20);
        if (state.getInt("age") < 600 - reduction) return;
        float damage = coldProjectileDamage(state.getInt("age"), reduction,
                state.getDouble("attack_bonus_percent") / 100.0D);
        if (damage > 0.0F) target.hurt(golem.damageSources().mobProjectile(snowball, golem), damage);
    }

    static float coldProjectileDamage(int ageTicks, int reductionTicks, double bonus) {
        if (ageTicks < Math.max(0, 600 - reductionTicks)) return 0.0F;
        return (float) (4.0D * (ageTicks >= Math.max(0, 1800 - reductionTicks)
                ? 1.0D + Math.max(0.0D, bonus) : 1.0D));
    }

    private static void processCold(SnowGolem golem) {
        CompoundTag state = state(golem, COLD_CURRENT);
        if (state == null) return;
        int age = state.getInt("age") + 1; state.putInt("age", age);
        int reduction = (int) (state.getDouble("time_reduction_seconds") * 20);
        setModifier(golem, Attributes.ATTACK_DAMAGE, COLD_ATTACK_FLAT, age >= 600 - reduction ? 4.0D : 0.0D,
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(golem, Attributes.MAX_HEALTH, COLD_HEALTH, age >= 600 - reduction ? 6.0D : 0.0D,
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(golem, Attributes.ARMOR, COLD_ARMOR_FLAT, age >= 900 - reduction ? 5.0D : 0.0D,
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(golem, Attributes.ARMOR_TOUGHNESS, COLD_TOUGHNESS, age >= 900 - reduction ? 7.0D : 0.0D,
                AttributeModifier.Operation.ADD_VALUE);
        setModifier(golem, Attributes.ARMOR, COLD_ARMOR_PERCENT, age >= 1200 - reduction
                ? state.getDouble("armor_percent") / 100.0D : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        setModifier(golem, Attributes.ATTACK_DAMAGE, COLD_ATTACK_PERCENT, age >= 1800 - reduction
                ? state.getDouble("attack_bonus_percent") / 100.0D : 0.0D, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    }

    private static void setModifier(LivingEntity entity, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            ResourceLocation id, double amount, AttributeModifier.Operation operation) {
        AttributeInstance instance = entity.getAttribute(attribute); if (instance == null) return;
        instance.removeModifier(id);
        if (amount != 0.0D) instance.addTransientModifier(new AttributeModifier(id, amount, operation));
    }

    static List<String> validateCrushingDefinition(AbilityDefinition d) { return validate(d, Set.of("charge_threshold", "damage_percent")); }
    static List<String> validateObsidianDefinition(AbilityDefinition d) { return validate(d, Set.of("max_shields", "heal_percent", "recharge")); }
    static List<String> validateColdDefinition(AbilityDefinition d) { return validate(d, Set.of("attack_bonus_percent", "armor_percent", "time_reduction_seconds")); }
    private static List<String> validate(AbilityDefinition definition, Set<String> keys) {
        try {
            parse(Config.CODEC, definition.effect().config(), "effect.config");
            Map<String, Double> merged = new HashMap<>();
            for (int i = 0; i < definition.ranks().values().size(); i++) {
                merged.putAll(parse(Rank.CODEC, definition.ranks().values().get(i), "ranks.values[" + i + "]").values());
                if (!merged.keySet().equals(keys)) throw new IllegalArgumentException("rank values must define " + keys);
            }
            return List.of();
        } catch (RuntimeException e) { return List.of(e.getMessage()); }
    }

    private static List<ActiveComponent> activeComponents(ServerPlayer player) {
        ArrayList<ActiveComponent> result = new ArrayList<>();
        Registry<AbilityDefinition> registry = player.registryAccess().registryOrThrow(ModDataRegistries.ABILITIES);
        registry.entrySet().stream().sorted(Comparator.comparing(e -> e.getKey().location())).forEach(entry -> {
            Optional<AbilityService.ActiveAbility> active = AbilityService.active(player, entry.getKey().location());
            if (active.isEmpty()) return;
            for (ResourceLocation type : List.of(CRUSHING_BLOW, OBSIDIAN_REINFORCEMENT, COLD_CURRENT))
                for (CompositeEffect.ComponentView view : CompositeEffect.componentsOfType(entry.getValue(), type)) {
                    AbilityService.ActiveAbility projected = CompositeEffect.projectActive(active.get(), view);
                    Map<String, Double> merged = new HashMap<>();
                    projected.unlockedRankValues().forEach(value -> merged.putAll(parse(Rank.CODEC, value, "rank").values()));
                    result.add(new ActiveComponent(type, parse(Config.CODEC, view.config(), "effect.config"), merged));
                }
        }); return result;
    }

    private static CompoundTag state(Entity entity, ResourceLocation type) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(ROOT)) return null;
        CompoundTag root = data.getCompound(ROOT);
        return root.contains(type.getPath()) ? root.getCompound(type.getPath()) : null;
    }
    private static ServerPlayer owner(LivingEntity entity, CompoundTag state) {
        if (!(entity.level() instanceof ServerLevel level) || !state.hasUUID("owner")) return null;
        return level.getServer().getPlayerList().getPlayer(state.getUUID("owner"));
    }
    private static <T> T parse(Codec<T> codec, Dynamic<?> input, String path) {
        StringBuilder error = new StringBuilder(); Optional<T> parsed = codec.parse(input).resultOrPartial(error::append);
        return parsed.orElseThrow(() -> new IllegalArgumentException(path + ": " + error));
    }
    public record Config(List<Item> items, int itemCost) {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.byNameCodec().listOf().fieldOf("items").forGetter(Config::items),
                Codec.intRange(1, 64).optionalFieldOf("item_cost", 1).forGetter(Config::itemCost)
        ).apply(instance, Config::new));
        public Config {
            items = List.copyOf(items);
            if (items.isEmpty()) throw new IllegalArgumentException("items must not be empty");
        }
    }
    public record Rank(Map<String, Double> values) {
        public static final Codec<Rank> CODEC = Codec.unboundedMap(Codec.STRING, Codec.DOUBLE).xmap(Rank::new, Rank::values);
    }
    private record ActiveComponent(ResourceLocation type, Config config, Map<String, Double> rank) {}
}
