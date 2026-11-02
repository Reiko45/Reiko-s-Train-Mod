package com.reiko.trains.signal;

import net.minecraft.util.StringRepresentable;

/** Which way trains must be going for a track sensor to sense them, as seen from behind it, looking at the track. */
public enum SensorDirection implements StringRepresentable {
    BOTH("both"),
    LEFT("left"),
    RIGHT("right");

    private final String name;

    SensorDirection(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public SensorDirection next() {
        return values()[(ordinal() + 1) % values().length];
    }
}
