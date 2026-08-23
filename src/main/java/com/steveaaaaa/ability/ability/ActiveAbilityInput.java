package com.steveaaaaa.ability.ability;

public enum ActiveAbilityInput {
    LEFT(0),
    RIGHT(1),
    CHARGE_START(2),
    CHARGE_RELEASE(3),
    SECONDARY(4),
    FORWARD(5),
    BACKWARD(6),
    FORWARD_LEFT(7),
    FORWARD_RIGHT(8),
    BACKWARD_LEFT(9),
    BACKWARD_RIGHT(10);

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
            case 5 -> FORWARD;
            case 6 -> BACKWARD;
            case 7 -> FORWARD_LEFT;
            case 8 -> FORWARD_RIGHT;
            case 9 -> BACKWARD_LEFT;
            case 10 -> BACKWARD_RIGHT;
            default -> throw new IllegalArgumentException("Unknown active ability input: " + networkId);
        };
    }
}
