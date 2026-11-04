package com.reiko.cars.car;

/** Compile-time stand-in for Reiko's Car Mod (see build.gradle): only what Reiko's Train Mod uses. */
public enum CarType {
    ;

    public final float width;
    public final float height;
    public final float length;

    CarType() {
        width = 0;
        height = 0;
        length = 0;
    }

    public boolean hasTowBall() {
        throw new UnsupportedOperationException();
    }

    public boolean canTow(com.reiko.cars.trailer.TrailerType trailer) {
        throw new UnsupportedOperationException();
    }
}
