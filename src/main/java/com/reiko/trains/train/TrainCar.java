package com.reiko.trains.train;

import net.minecraft.world.entity.vehicle.AbstractMinecart;

/**
 * Something that can be part of a train: the locomotive, a coach or a wagon. They're all minecarts underneath (so
 * they run on any rails), with a longer model, couplings front and back, and seats.
 */
public interface TrainCar {
    /** Where someone sits: blocks forward of the middle (negative is behind), to the side, and up from the rails. */
    record Seat(double forward, double side, double up) {
    }

    TrainCarState trainState();

    /** From the middle of the car to the face of its buffers, in blocks. Two coupled cars keep their buffers this far apart. */
    double halfLength();

    default Seat[] seats() {
        return new Seat[0];
    }

    default boolean isLocomotive() {
        return false;
    }

    default AbstractMinecart cart() {
        return (AbstractMinecart) this;
    }
}
