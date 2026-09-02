package com.steveaaaaa.ability.client.presentation;

import com.steveaaaaa.ability.AbilityMod;
import com.steveaaaaa.ability.presentation.AbilityCue;
import com.steveaaaaa.ability.registry.ModParticles;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = AbilityMod.MOD_ID, value = Dist.CLIENT)
public final class LuckyCatPresentation {
    private static final ResourceLocation ABILITY = AbilityMod.id("lucky_cat");
    private static final ResourceLocation LUCKY_GIFT = AbilityMod.id("lucky_gift");
    private static final List<LuckyGift> ACTIVE = new ArrayList<>();
    private static ClientLevel activeLevel;

    private LuckyCatPresentation() {
    }

    static void accept(ClientLevel level, AbilityCue cue) {
        if (!cue.abilityId().equals(ABILITY) || !cue.cueId().equals(LUCKY_GIFT)
                || cue.action() == AbilityCue.Action.STOP) {
            return;
        }
        if (activeLevel != level) clear(level);
        Entity cat = level.getEntity(cue.sourceEntityId());
        Vec3 catFeet = cat == null ? cue.position() : cat.position();
        Vec3 coinStart = cat == null
                ? catFeet.add(0.0D, 0.42D, 0.0D)
                : catFeet.add(0.0D, cat.getBbHeight() * 0.52D, 0.0D);
        if (ACTIVE.size() >= 24) ACTIVE.removeFirst();
        ACTIVE.add(new LuckyGift(
                level.getGameTime(), catFeet, coinStart, cue.position(), cue.randomSeed()
        ));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level != activeLevel) {
            clear(level);
            return;
        }
        long now = level.getGameTime();
        Iterator<LuckyGift> iterator = ACTIVE.iterator();
        while (iterator.hasNext()) {
            LuckyGift gift = iterator.next();
            int age = (int) (now - gift.startedAt);
            if (!gift.coinSpawned) {
                emitCoin(level, gift);
                gift.coinSpawned = true;
            }
            while (gift.nextPaw < 3 && age >= 1 + gift.nextPaw * 3) {
                emitPaw(level, gift, gift.nextPaw);
                gift.nextPaw++;
            }
            if (!gift.burstSpawned && age >= 12) {
                emitArrival(level, gift);
                gift.burstSpawned = true;
            }
            if (age > 30) iterator.remove();
        }
    }

    private static void emitCoin(ClientLevel level, LuckyGift gift) {
        int flightTicks = 12;
        Vec3 destination = gift.giftPosition.add(0.0D, 0.27D, 0.0D);
        Vec3 delta = destination.subtract(gift.coinStart);
        double gravityPerTick = 0.04D * 0.14D;
        Vec3 velocity = new Vec3(
                delta.x / flightTicks,
                (delta.y + 0.5D * gravityPerTick * flightTicks * flightTicks) / flightTicks,
                delta.z / flightTicks
        );
        level.addParticle(ModParticles.LUCKY_CAT_COIN.get(),
                gift.coinStart.x, gift.coinStart.y, gift.coinStart.z,
                velocity.x, velocity.y, velocity.z);
    }

    private static void emitPaw(ClientLevel level, LuckyGift gift, int index) {
        Vec3 horizontal = gift.giftPosition.subtract(gift.catFeet).multiply(1.0D, 0.0D, 1.0D);
        Vec3 side = horizontal.lengthSqr() < 1.0E-8D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : new Vec3(-horizontal.z, 0.0D, horizontal.x).normalize();
        double progress = 0.24D + index * 0.25D;
        Vec3 position = gift.catFeet.lerp(gift.giftPosition, progress)
                .add(side.scale((index & 1) == 0 ? 0.075D : -0.075D))
                .add(0.0D, 0.045D, 0.0D);
        level.addParticle(ModParticles.LUCKY_CAT_PAW.get(),
                position.x, position.y, position.z, 0.0D, 0.003D, 0.0D);
    }

    private static void emitArrival(ClientLevel level, LuckyGift gift) {
        RandomSource random = RandomSource.create(gift.seed ^ 0x6A09E667F3BCC909L);
        Vec3 center = gift.giftPosition.add(0.0D, 0.31D, 0.0D);
        level.addParticle(ModParticles.LUCKY_CAT_KNOT.get(),
                center.x, center.y, center.z, 0.0D, 0.006D, 0.0D);
        int count = 11;
        double phase = random.nextDouble() * Math.PI * 2.0D;
        for (int index = 0; index < count; index++) {
            double angle = phase + Math.PI * 2.0D * index / count;
            double height = (random.nextDouble() - 0.5D) * 0.23D;
            double speed = 0.018D + random.nextDouble() * 0.024D;
            level.addParticle(ModParticles.SNIFFER_TREASURE_GOLD.get(),
                    center.x + Math.cos(angle) * 0.12D,
                    center.y + height,
                    center.z + Math.sin(angle) * 0.12D,
                    Math.cos(angle) * speed,
                    0.012D + random.nextDouble() * 0.025D,
                    Math.sin(angle) * speed);
        }
    }

    private static void clear(ClientLevel level) {
        ACTIVE.clear();
        activeLevel = level;
    }

    private static final class LuckyGift {
        private final long startedAt;
        private final Vec3 catFeet;
        private final Vec3 coinStart;
        private final Vec3 giftPosition;
        private final long seed;
        private int nextPaw;
        private boolean coinSpawned;
        private boolean burstSpawned;

        private LuckyGift(long startedAt, Vec3 catFeet, Vec3 coinStart, Vec3 giftPosition, long seed) {
            this.startedAt = startedAt;
            this.catFeet = catFeet;
            this.coinStart = coinStart;
            this.giftPosition = giftPosition;
            this.seed = seed;
        }
    }
}
