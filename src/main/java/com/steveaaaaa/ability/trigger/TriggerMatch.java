package com.steveaaaaa.ability.trigger;

public record TriggerMatch(Status status, double xpMultiplier, String detail) {
    public enum Status {
        MATCHED,
        NOT_MATCHED,
        INVALID
    }

    public static TriggerMatch matched(double xpMultiplier) {
        return new TriggerMatch(Status.MATCHED, xpMultiplier, "");
    }

    public static TriggerMatch notMatched() {
        return new TriggerMatch(Status.NOT_MATCHED, 0.0D, "");
    }

    public static TriggerMatch invalid(String detail) {
        return new TriggerMatch(Status.INVALID, 0.0D, detail);
    }

    public boolean matched() {
        return status == Status.MATCHED;
    }
}
