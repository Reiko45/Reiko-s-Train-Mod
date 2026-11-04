package com.reiko.trains.compat.jei;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * One page of JEI's "Locomotive Fuel": what a kind of locomotive runs on. Each slot of {@code fuels} (or each fluid)
 * is one thing it takes, with a line saying how long it lasts; {@code engines} are the locomotives (and tenders) that
 * take it; {@code lines} say the rest.
 */
record LocomotiveFuel(List<Slot> fuels, List<FluidSlot> fluids, List<ItemStack> engines, List<Component> lines) {
    /** Items that do the same (coal and charcoal, all the logs), cycling in one slot, and what they're good for. */
    record Slot(List<ItemStack> items, Component note) {
    }

    record FluidSlot(Fluid fluid, Component note) {
    }
}
