package com.reiko.trains.train;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * A hitbox section of a car that holds things: it stands in for the car's inventory, so a hopper under (or over)
 * any part of a long wagon can reach it.
 */
public class ContainerCarPart extends CarPart implements Container {
    public ContainerCarPart(TrainCarEntity car, double offset, float width, float height) {
        super(car, offset, width, height);
    }

    private Container container() {
        return (Container) getParent();
    }

    @Override
    public int getContainerSize() {
        return container().getContainerSize();
    }

    @Override
    public boolean isEmpty() {
        return container().isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return container().getItem(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return container().removeItem(slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return container().removeItemNoUpdate(slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        container().setItem(slot, stack);
    }

    @Override
    public void setChanged() {
        container().setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return container().stillValid(player);
    }

    @Override
    public void clearContent() {
        container().clearContent();
    }
}
