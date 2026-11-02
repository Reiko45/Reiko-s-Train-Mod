package com.reiko.trains.train;

import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * A one-slot chute that tips fuel into a {@link FuelStore} (a tender, a diesel's tank), so hoppers, pipes and wagon
 * loaders can refuel it. It takes only the fuel it's for; what won't fit yet waits in the chute; and what a fuel
 * leaves behind (a lava bucket's bucket) stays there for a hopper underneath to take away.
 */
public final class FuelChute {
    private final FuelStore store;
    private final Predicate<ItemStack> accepts;
    private ItemStack held = ItemStack.EMPTY;

    public FuelChute(FuelStore store, Predicate<ItemStack> accepts) {
        this.store = store;
        this.accepts = accepts;
    }

    public boolean accepts(ItemStack stack) {
        return accepts.test(stack) && stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    /** Tips what's in the chute in, as much as there's room for. */
    public void tip() {
        if (held.isEmpty() || !accepts(held)) {
            return;
        }
        int burn = held.getBurnTime(RecipeType.SMELTING);
        int count = Math.min((int) ((store.fuelCapacity() - store.fuel()) / burn), held.getCount());
        if (held.hasCraftingRemainingItem()) {
            count = Math.min(count, 1);
        }
        if (count <= 0) {
            return;
        }
        store.setFuel(store.fuel() + (double) burn * count);
        ItemStack remainder = held.getCraftingRemainingItem();
        held.shrink(count);
        if (held.isEmpty() && !remainder.isEmpty()) {
            held = remainder;
        }
    }

    public boolean isEmpty() {
        return held.isEmpty();
    }

    public ItemStack get() {
        return held;
    }

    public ItemStack take(int amount) {
        return held.isEmpty() ? ItemStack.EMPTY : held.split(amount);
    }

    public ItemStack takeAll() {
        ItemStack taken = held;
        held = ItemStack.EMPTY;
        return taken;
    }

    public void set(ItemStack stack) {
        held = stack;
        tip();
    }

    /** Hoppers can't take fuel back out, only what's left of it (empty buckets). */
    public boolean canTake(ItemStack stack) {
        return !accepts(stack);
    }

    public void clear() {
        held = ItemStack.EMPTY;
    }

    /** Spills what's in the chute, when the car comes apart. */
    public void drop(Entity car) {
        if (!held.isEmpty()) {
            Containers.dropItemStack(car.level(), car.getX(), car.getY() + 1, car.getZ(), held);
            held = ItemStack.EMPTY;
        }
    }

    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (!held.isEmpty()) {
            tag.put("Chute", held.save(registries));
        }
    }

    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        held = tag.contains("Chute") ? ItemStack.parse(registries, tag.getCompound("Chute")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
    }
}
