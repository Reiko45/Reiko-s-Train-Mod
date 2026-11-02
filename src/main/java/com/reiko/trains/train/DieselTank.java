package com.reiko.trains.train;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A diesel locomotive's tank as a fluid handler, so buckets, pipes, BuildCraft tanks (through a wagon loader) and
 * other mods' fluid containers can fill it. It takes only diesel, turns it into energy on the way in, and can't be
 * drained. Its capacity is in buckets of Dense Fuel.
 */
public final class DieselTank implements IFluidHandler {
    private final DieselLocomotive loco;

    public DieselTank(DieselLocomotive loco) {
        this.loco = loco;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    /** The tank holds energy rather than a fluid, so it never reports one. */
    @Override
    public FluidStack getFluidInTank(int tank) {
        return FluidStack.EMPTY;
    }

    @Override
    public int getTankCapacity(int tank) {
        return DieselLocomotive.TANK_BUCKETS * 1000;
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return DieselFuel.isDiesel(stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (!DieselFuel.isDiesel(resource) || loco.level().isClientSide) {
            return 0;
        }
        int amount = Math.min(resource.getAmount(), DieselFuel.millibucketsThatFit(resource, loco.fuelCapacity() - loco.fuel()));
        if (amount > 0 && action.execute()) {
            loco.setFuel(loco.fuel() + DieselFuel.energyOf(resource, amount));
        }
        return Math.max(0, amount);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return FluidStack.EMPTY;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return FluidStack.EMPTY;
    }
}
