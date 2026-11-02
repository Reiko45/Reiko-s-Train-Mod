package com.reiko.trains.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server settings, in serverconfig/reikotrains-server.toml in each world. */
public final class TrainsConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue NEEDS_FUEL = BUILDER
            .comment("Whether steam locomotives need fuel (coal, charcoal, wood...) to run. Turn off for free trains.")
            .define("needsFuel", true);
    public static final ModConfigSpec.DoubleValue FUEL_USE = BUILDER
            .comment("How fast locomotives burn fuel. 1.0 means a piece of coal lasts about three minutes at full",
                    "throttle and much longer standing still with the fire banked.")
            .defineInRange("fuelUse", 1.0, 0.0, 10.0);
    public static final ModConfigSpec.DoubleValue MAX_SPEED = BUILDER
            .comment("A locomotive's top speed at full throttle, in blocks per tick (0.6 is 12 blocks a second, 43 km/h).",
                    "Reverse is half of it.")
            .defineInRange("maxSpeed", 0.6, 0.1, 1.2);
    public static final ModConfigSpec.DoubleValue ACCELERATION = BUILDER
            .comment("How quickly a locomotive pulling two coaches picks up speed, in blocks per tick per tick. Lighter",
                    "trains pick up speed faster, heavier ones slower, and uphill slower still.")
            .defineInRange("acceleration", 0.004, 0.0005, 0.05);
    public static final ModConfigSpec.IntValue MAX_TRAIN_LENGTH = BUILDER
            .comment("The most cars (counting the locomotive) one train can have.")
            .defineInRange("maxTrainLength", 16, 2, 64);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private TrainsConfig() {
    }
}
