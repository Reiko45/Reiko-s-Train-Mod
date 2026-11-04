package com.reiko.trains.compat.jei;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** One page of JEI's "Track-Laying Wagon": what it lays, or what it fills dips with, and a line or two about it. */
record TrackLaying(List<ItemStack> items, List<Component> lines) {
}
