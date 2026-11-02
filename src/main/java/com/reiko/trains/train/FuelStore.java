package com.reiko.trains.train;

/** Somewhere a steam locomotive's fuel is kept, as burn time in ticks: its own firebox, or a tender. */
public interface FuelStore {
    double fuel();

    double fuelCapacity();

    void setFuel(double ticks);

    /** How full it is, 0-100 (only 0 when it's really empty). */
    default int fuelPercent() {
        double fuel = fuel();
        return fuel <= 0 ? 0 : Math.max(1, Math.min(100, (int) Math.ceil(fuel / fuelCapacity() * 100.0)));
    }
}
