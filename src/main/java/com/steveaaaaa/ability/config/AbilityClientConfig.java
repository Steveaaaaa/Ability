package com.steveaaaaa.ability.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AbilityClientConfig {
    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.EnumValue<ChargedLeapControlMode> CHARGED_LEAP_CONTROL_MODE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("controls");
        CHARGED_LEAP_CONTROL_MODE = builder
                .comment(
                        "Charged Leap input mode.",
                        "DEDICATED_KEY uses the configurable Charged Leap key (default V).",
                        "JUMP_KEY holds the vanilla Jump key; Charged Leap is disabled in Creative mode."
                )
                .defineEnum("chargedLeapControlMode", ChargedLeapControlMode.DEDICATED_KEY);
        builder.pop();
        SPEC = builder.build();
    }

    private AbilityClientConfig() {
    }

    public static ChargedLeapControlMode chargedLeapControlMode() {
        return SPEC.isLoaded() ? CHARGED_LEAP_CONTROL_MODE.get() : ChargedLeapControlMode.DEDICATED_KEY;
    }

    public enum ChargedLeapControlMode {
        DEDICATED_KEY,
        JUMP_KEY
    }
}
