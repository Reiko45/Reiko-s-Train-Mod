package com.reiko.trains.train;

import com.reiko.cars.car.CarEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Everything that touches Reiko's Car Mod's classes, kept apart so nothing loads them unless the car mod is installed
 * (see {@link CarCargo#CAR_MOD}): it's compiled against stand-ins for them (src/stubs).
 */
final class CarModAccess {
    private CarModAccess() {
    }

    static boolean isCar(Entity entity) {
        return entity instanceof CarEntity;
    }

    /** How long it is, nose to tail, in blocks. */
    static double length(Entity car) {
        return ((CarEntity) car).carType().length;
    }

    /** The way it faces. */
    static Vec3 forward(Entity car) {
        return ((CarEntity) car).forward();
    }

    /** Stops it where it is (its speed's its own, not just its motion). */
    static void stopDead(Entity car) {
        ((CarEntity) car).stopDead();
    }

    static boolean canBeUsedBy(Entity car, Player player) {
        return ((CarEntity) car).canBeUsedBy(player);
    }
}
