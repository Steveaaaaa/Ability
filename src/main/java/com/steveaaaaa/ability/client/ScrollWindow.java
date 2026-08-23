package com.steveaaaaa.ability.client;

public final class ScrollWindow {
    private ScrollWindow() {
    }

    public static int clamp(int offset, int itemCount, int visibleCount) {
        int maximum = Math.max(0, itemCount - Math.max(1, visibleCount));
        return Math.max(0, Math.min(offset, maximum));
    }

    public static int scroll(int offset, double delta, int itemCount, int visibleCount) {
        if (delta == 0.0D) {
            return clamp(offset, itemCount, visibleCount);
        }
        int direction = delta > 0.0D ? -1 : 1;
        return clamp(offset + direction, itemCount, visibleCount);
    }
}
