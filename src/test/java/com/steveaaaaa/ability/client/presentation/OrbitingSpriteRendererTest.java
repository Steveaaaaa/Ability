package com.steveaaaaa.ability.client.presentation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class OrbitingSpriteRendererTest {
    @Test
    void fadesAtBothEndsOfTheServerLifetime() {
        assertEquals(0.0F, OrbitingSpriteRenderer.fadeScale(100.0D, 100L, 120L, 2));
        assertEquals(0.5F, OrbitingSpriteRenderer.fadeScale(101.0D, 100L, 120L, 2));
        assertEquals(1.0F, OrbitingSpriteRenderer.fadeScale(110.0D, 100L, 120L, 2));
        assertEquals(0.5F, OrbitingSpriteRenderer.fadeScale(119.0D, 100L, 120L, 2));
        assertEquals(0.0F, OrbitingSpriteRenderer.fadeScale(120.0D, 100L, 120L, 2));
    }

    @Test
    void supportsImmediatePresentationWithoutFade() {
        assertEquals(1.0F, OrbitingSpriteRenderer.fadeScale(105.0D, 100L, 120L, 0));
        assertEquals(0.0F, OrbitingSpriteRenderer.fadeScale(120.0D, 100L, 120L, 0));
    }

    @Test
    void shortStunsStillReachFullSize() {
        assertEquals(1.0F, OrbitingSpriteRenderer.fadeScale(101.0D, 100L, 103L, 2));
        assertEquals(1.0F, OrbitingSpriteRenderer.fadeScale(102.0D, 100L, 103L, 2));
    }

    @Test
    void adaptsStarCountAndRadiusToEntityWidth() {
        var definition = definition();
        assertEquals(3, OrbitingSpriteRenderer.spriteCount(0.6F, definition));
        assertEquals(4, OrbitingSpriteRenderer.spriteCount(1.4F, definition));
        assertEquals(5, OrbitingSpriteRenderer.spriteCount(2.0F, definition));
        assertEquals(0.38D, OrbitingSpriteRenderer.orbitRadius(0.6F, definition), 0.0001D);
        assertEquals(0.8D, OrbitingSpriteRenderer.orbitRadius(2.0F, definition), 0.0001D);
    }

    private static AbilityPresentationDefinition.OrbitingSprite definition() {
        return new AbilityPresentationDefinition.OrbitingSprite(
                ResourceLocation.fromNamespaceAndPath("fantasypower", "textures/particle/stun_star.png"),
                AbilityPresentationDefinition.Anchor.TARGET,
                3,
                5,
                2.0F,
                1.5F,
                0.32F,
                0.9F,
                0.2F,
                0.3F,
                0.3F,
                0.34F,
                0.6F,
                0.04F,
                1.2F,
                2,
                true
        );
    }
}
