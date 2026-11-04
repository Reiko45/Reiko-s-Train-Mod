package com.reiko.trains.train;

import com.reiko.cars.car.CarEntity;
import com.reiko.cars.trailer.TrailerEntity;
import javax.annotation.Nullable;
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

    static boolean isTrailer(Entity entity) {
        return entity instanceof TrailerEntity;
    }

    /** The trailer it's towing (on its tow ball or fifth wheel), if it's towing one. */
    @Nullable
    static Entity trailer(Entity car) {
        return ((CarEntity) car).trailer();
    }

    /** Where a trailer hitches to it. */
    static Vec3 hitchPoint(Entity car) {
        return ((CarEntity) car).hitchPoint();
    }

    /** Where a trailer's coupler (or kingpin) is. */
    static Vec3 coupler(Entity trailer) {
        return ((TrailerEntity) trailer).coupler();
    }

    static Vec3 trailerForward(Entity trailer) {
        return ((TrailerEntity) trailer).forward();
    }

    /** A trailer's body, bumper to bumper, in blocks. */
    static double trailerLength(Entity trailer) {
        return ((TrailerEntity) trailer).trailerType().length;
    }

    /** Whether the car can tow the trailer, and the player may: a kind it can pull, not worn out, theirs to use. */
    static boolean canTow(Entity car, Entity trailer, Player player) {
        TrailerEntity t = (TrailerEntity) trailer;
        return ((CarEntity) car).carType().canTow(t.trailerType()) && !t.isBrokenDown() && t.canBeUsedBy(player);
    }
}
