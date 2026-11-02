package com.reiko.trains.freight;

import net.minecraft.util.StringRepresentable;

/** What a wagon loader does: fill trains' freight wagons, or empty them. */
public enum LoaderMode implements StringRepresentable {
    LOAD("load"),
    UNLOAD("unload");

    private final String name;

    LoaderMode(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public LoaderMode other() {
        return this == LOAD ? UNLOAD : LOAD;
    }
}
