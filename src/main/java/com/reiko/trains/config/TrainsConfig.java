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
    public static final ModConfigSpec.BooleanValue SIGNALS_STOP_DRIVERS = BUILDER
            .comment("Whether trains people are driving stop for red signals by themselves, like trains on a timetable",
                    "do. Turn off to leave it to the drivers.")
            .define("signalsStopDrivers", true);
    public static final ModConfigSpec.IntValue CAUTION_SPEED = BUILDER
            .comment("How fast trains may go past a yellow (three-light) signal, until the next signal, in km/h. Drivers keep",
                    "to it too while signalsStopDrivers is on.")
            .defineInRange("cautionSpeedKmh", 40, 5, 200);
    public static final ModConfigSpec.BooleanValue SPEED_LIMITS_FOR_DRIVERS = BUILDER
            .comment("Whether trains people are driving keep to speed limit signs by themselves, like trains on a timetable",
                    "do. Turn off to leave it to the drivers.")
            .define("speedLimitsForDrivers", true);
    public static final ModConfigSpec.BooleanValue TRAINS_ON_MINIMAPS = BUILDER
            .comment("Whether players are told where every train is, so minimap mods (Xaero's Minimap) can show them.")
            .define("trainsOnMinimaps", true);
    public static final ModConfigSpec.IntValue MAX_TRAIN_LENGTH = BUILDER
            .comment("The most cars (counting the locomotive) one train can have.")
            .defineInRange("maxTrainLength", 16, 2, 64);
    public static final ModConfigSpec.DoubleValue FALLBACK_DIESEL_ENERGY = BUILDER
            .comment("MJ per bucket for a fluid in the reikotrains:diesel tag that BuildCraft has no fuel value for.")
            .defineInRange("fallbackDieselEnergyPerBucket", 96_000.0, 1.0, 100_000_000.0);
    public static final ModConfigSpec.IntValue LOADER_SPEED = BUILDER
            .comment("How many items a wagon loader moves a second. 640 (ten stacks) fills a freight wagon in about 11",
                    "seconds. Two loaders at one train work twice as fast.")
            .defineInRange("loaderSpeed", 640, 1, 100000);
    public static final ModConfigSpec.LongValue CHARGER_CAPACITY = BUILDER
            .comment("MJ a charging point stores, from BuildCraft engines and kinesis pipes (or other mods' cables). It",
                    "charges electric locomotives from that store, so a few engines can keep it topped up between trains.",
                    "An electric's batteries hold 1,000,000 MJ.")
            .defineInRange("chargingPointCapacityMJ", 1_000_000L, 1_000L, 1_000_000_000L);
    public static final ModConfigSpec.IntValue CHARGER_RATE = BUILDER
            .comment("MJ a tick a charging point puts into each electric locomotive standing at it. 5000 charges a flat",
                    "electric in ten seconds, if the store has enough.")
            .defineInRange("chargingPointRateMJ", 5000, 1, 10_000_000);
    public static final ModConfigSpec.LongValue SUBSTATION_CAPACITY = BUILDER
            .comment("MJ each substation adds to its overhead line's store. Trains under the line run from the store, and",
                    "charge their batteries from it, so a line with more substations keeps going longer between top-ups.")
            .defineInRange("substationCapacityMJ", 1_000_000L, 1_000L, 1_000_000_000L);
    public static final ModConfigSpec.IntValue WIRE_CHARGE_RATE = BUILDER
            .comment("MJ a tick an electric under a live overhead line puts into its batteries (on top of what it uses to",
                    "run). 2000 charges a flat electric in about 25 seconds.")
            .defineInRange("wireChargeRateMJ", 2000, 0, 10_000_000);
    public static final ModConfigSpec.BooleanValue STEAM_NEEDS_WATER = BUILDER
            .comment("Whether steam engines need water as well as coal (from a water tower, buckets or pipes). A full load of",
                    "water lasts as long as a full load of coal.")
            .define("steamNeedsWater", true);
    public static final ModConfigSpec.IntValue WATER_TOWER_RATE = BUILDER
            .comment("Millibuckets a tick a water tower puts into each tank engine or tender of a train standing at it.")
            .defineInRange("waterTowerRateMB", 500, 1, 1_000_000);
    public static final ModConfigSpec.IntValue CROSSING_WARNING = BUILDER
            .comment("How many seconds before a train gets to a level crossing its lamps start flashing. The barrier comes",
                    "down three seconds after that, taking two seconds over it.")
            .defineInRange("crossingWarningSeconds", 10, 6, 30);
    public static final ModConfigSpec.BooleanValue GIVE_HANDBOOK = BUILDER
            .comment("Whether each player is given the Railway Handbook the first time they join the world (once ever; it",
                    "needs Patchouli). It can always be crafted from a book and Train Track.")
            .define("giveHandbook", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private TrainsConfig() {
    }
}
