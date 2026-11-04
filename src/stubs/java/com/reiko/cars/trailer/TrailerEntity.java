package com.reiko.cars.trailer;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Compile-time stand-in for Reiko's Car Mod (see build.gradle): only what Reiko's Train Mod uses. */
public abstract class TrailerEntity extends VehicleEntity {
    protected TrailerEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public TrailerType trailerType() {
        throw new UnsupportedOperationException();
    }

    public boolean canBeUsedBy(Player player) {
        throw new UnsupportedOperationException();
    }

    public boolean isBrokenDown() {
        throw new UnsupportedOperationException();
    }

    public Vec3 forward() {
        throw new UnsupportedOperationException();
    }

    public Vec3 coupler() {
        throw new UnsupportedOperationException();
    }
}
