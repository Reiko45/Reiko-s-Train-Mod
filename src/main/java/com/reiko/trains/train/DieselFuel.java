package com.reiko.trains.train;

import buildcraftreborn.api.fuels.BuildcraftFuelRegistry;
import buildcraftreborn.api.fuels.IFuel;
import buildcraftreborn.api.mj.MjAPI;
import com.reiko.trains.ReikoTrains;
import com.reiko.trains.config.TrainsConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * What a diesel locomotive runs on: the fluids in the {@code reikotrains:diesel} tag (BuildCraft Reborn's Dense Fuel
 * and Mixed Heavy Fuels, and other mods' diesel and biodiesel), the same diesel as Reiko's Car Mod's diesel vehicles.
 * How much energy a bucket holds comes from BuildCraft's fuel registry, the numbers its combustion engine uses; tank
 * sizes are in buckets of Dense Fuel, as the cars' are.
 */
public final class DieselFuel {
    public static final TagKey<Fluid> TAG = TagKey.create(Registries.FLUID, ReikoTrains.id("diesel"));
    /** MJ in a bucket of BuildCraft's Dense Fuel: what tank sizes are measured in (the car mod's diesel bucket too). */
    public static final double BUCKET_ENERGY = 360_000;

    private DieselFuel() {
    }

    public static boolean isDiesel(FluidStack stack) {
        return !stack.isEmpty() && stack.is(TAG);
    }

    /** MJ in a bucket of {@code stack}'s fluid, or 0 if it isn't diesel. */
    public static double energyPerBucket(FluidStack stack) {
        if (!isDiesel(stack)) {
            return 0;
        }
        IFuel fuel = BuildcraftFuelRegistry.fuel == null ? null : BuildcraftFuelRegistry.fuel.getFuel(stack);
        if (fuel == null) {
            return TrainsConfig.FALLBACK_DIESEL_ENERGY.get();
        }
        return (double) fuel.getPowerPerCycle() / MjAPI.MJ * fuel.getTotalBurningTime();
    }

    /** MJ in {@code millibuckets} of {@code stack}'s fluid. */
    public static double energyOf(FluidStack stack, int millibuckets) {
        return energyPerBucket(stack) * millibuckets / 1000.0;
    }

    /** The most of {@code stack}'s fluid, in millibuckets, that fits in {@code room} MJ of tank. */
    public static int millibucketsThatFit(FluidStack stack, double room) {
        double perBucket = energyPerBucket(stack);
        if (perBucket <= 0 || room <= 0) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.floor(room / perBucket * 1000.0 + 1.0E-6));
    }
}
