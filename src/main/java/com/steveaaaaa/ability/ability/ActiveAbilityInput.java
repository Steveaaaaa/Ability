package com.steveaaaaa.ability.ability;

public enum ActiveAbilityInput {
    LEFT(0),
    RIGHT(1),
    CHARGE_START(2),
    CHARGE_RELEASE(3),
    SECONDARY(4);

    private final int networkId;

    ActiveAbilityInput(int networkId) {
        this.networkId = networkId;
    }

    public int networkId() {
        return networkId;
    }

    public static ActiveAbilityInput fromNetworkId(int networkId) {
        return switch (networkId) {
            case 0 -> LEFT;
            case 1 -> RIGHT;
            case 2 -> CHARGE_START;
            case 3 -> CHARGE_RELEASE;
            case 4 -> SECONDARY;
            default -> throw new IllegalArgumentException("Unknown active ability input: " + networkId);
        };
    }
}
