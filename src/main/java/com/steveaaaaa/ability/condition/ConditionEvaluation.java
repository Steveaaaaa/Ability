package com.steveaaaaa.ability.condition;

public record ConditionEvaluation(Status status, String detail) {
    public enum Status {
        SATISFIED,
        NOT_SATISFIED,
        INVALID
    }

    public static ConditionEvaluation satisfied() {
        return new ConditionEvaluation(Status.SATISFIED, "");
    }

    public static ConditionEvaluation notSatisfied(String detail) {
        return new ConditionEvaluation(Status.NOT_SATISFIED, detail);
    }

    public static ConditionEvaluation invalid(String detail) {
        return new ConditionEvaluation(Status.INVALID, detail);
    }

    public boolean isSatisfied() {
        return status == Status.SATISFIED;
    }
}
