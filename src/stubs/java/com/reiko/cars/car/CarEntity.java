package com.reiko.cars.car;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Compile-time stand-in for Reiko's Car Mod (see build.gradle): only what Reiko's Train Mod uses. */
public abstract class CarEntity extends VehicleEntity {
    protected CarEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public CarType carType() {
        throw new UnsupportedOperationException();
    }

    public Vec3 forward() {
        throw new UnsupportedOperationException();
    }

    public void stopDead() {
        throw new UnsupportedOperationException();
    }

    public boolean canBeUsedBy(Player player) {
        throw new UnsupportedOperationException();
    }

    public double tankCapacity() {
        throw new UnsupportedOperationException();
    }

    public void setFuel(double mj) {
        throw new UnsupportedOperationException();
    }

    public com.reiko.cars.trailer.TrailerEntity trailer() {
        throw new UnsupportedOperationException();
    }

    public Vec3 hitchPoint() {
        throw new UnsupportedOperationException();
    }
}
