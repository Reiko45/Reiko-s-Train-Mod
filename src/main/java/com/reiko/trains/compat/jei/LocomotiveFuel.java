package com.reiko.trains.compat.jei;

import java.util.List;
import java.util.function.DoubleFunction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

/**
 * One page of JEI's "Locomotive Fuel": what a kind of locomotive runs on. Each slot of {@code fuels} (or each fluid)
 * is one thing it takes, with a line saying how long it lasts; {@code engines} are the locomotives (and tenders) that
 * take it; {@code lines} say the rest.
 */
record LocomotiveFuel(List<Slot> fuels, List<FluidSlot> fluids, List<ItemStack> engines, List<DoubleFunction<Component>> lines) {
    /**
     * Items that do the same (coal and charcoal, all the logs), cycling in one slot, and what they're good for: worked
     * out for the server's {@code fuelUse} when shown.
     */
    record Slot(List<ItemStack> items, DoubleFunction<Component> note) {
    }

    record FluidSlot(Fluid fluid, DoubleFunction<Component> note) {
    }

    /** A line or note that doesn't depend on how fast fuel burns. */
    static DoubleFunction<Component> fixed(Component text) {
        return fuelUse -> text;
    }

    /** What a note says on this server: how long, or that fuel isn't needed. */
    static Component shown(DoubleFunction<Component> note, boolean timed) {
        if (timed && !com.reiko.trains.client.ServerSettings.needsFuel()) {
            return Component.translatable("jei.reikotrains.fuel.free");
        }
        return note.apply(Math.max(1.0E-6, com.reiko.trains.client.ServerSettings.fuelUse()));
    }
}
