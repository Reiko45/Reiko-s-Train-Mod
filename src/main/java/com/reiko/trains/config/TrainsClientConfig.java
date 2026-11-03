package com.reiko.trains.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Each player's own settings, in config/reikotrains-client.toml. */
public final class TrainsClientConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue TRAINS_ON_MINIMAP = BUILDER
            .comment("Whether trains show on Xaero's Minimap (when it's installed, and the server shares where they are).")
            .define("trainsOnMinimap", true);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private TrainsClientConfig() {
    }
}
