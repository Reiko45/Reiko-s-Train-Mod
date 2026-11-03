package com.reiko.trains.train;

import com.reiko.trains.config.TrainsConfig;

/**
 * Where a steam engine's water is kept, in millibuckets: a tank engine's own side tanks, or a tender's. Engines use
 * it as fast as they burn coal (a full load of each lasts the same), and can't work with none.
 */
public interface WaterStore {
    double water();

    double waterCapacity();

    void setWater(double millibuckets);

    /** Room for more, in millibuckets. */
    default double waterRoom() {
        return Math.max(0, waterCapacity() - water());
    }

    /** How full it is, 0-100 (only 0 when it's really empty). */
    default int waterFill() {
        double water = water();
        return water <= 0 ? 0 : Math.max(1, Math.min(100, (int) Math.ceil(water / waterCapacity() * 100.0)));
    }

    /** Whether steam engines need water: they do, unless the server turns it off (or they need no fuel at all). */
    static boolean needed() {
        return TrainsConfig.NEEDS_FUEL.getAsBoolean() && TrainsConfig.STEAM_NEEDS_WATER.getAsBoolean();
    }
}
