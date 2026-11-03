package com.reiko.trains.train;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/**
 * A steam engine's (or tender's) water as a fluid handler, so buckets, pipes and other mods' fluid containers can
 * fill it. It takes only water, and can't be drained.
 */
public final class WaterTank implements IFluidHandler {
    private final WaterStore store;

    public WaterTank(WaterStore store) {
        this.store = store;
    }

    @Override
    public int getTanks() {
        return 1;
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        int amount = (int) Math.floor(store.water());
        return amount <= 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, amount);
    }

    @Override
    public int getTankCapacity(int tank) {
        return (int) store.waterCapacity();
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return stack.getFluid().is(FluidTags.WATER);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty() || !resource.getFluid().is(FluidTags.WATER)) {
            return 0;
        }
        int amount = (int) Math.min(resource.getAmount(), Math.floor(store.waterRoom()));
        if (amount > 0 && action.execute()) {
            store.setWater(store.water() + amount);
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

    /**
     * Fills it from what the player's holding, if that's a bucket (or other container) of water: shows how full it is
     * either way. Returns null if what they're holding has no water in it, for the caller to carry on.
     */
    @javax.annotation.Nullable
    public static net.minecraft.world.InteractionResult fillFromHand(net.minecraft.world.entity.player.Player player,
            net.minecraft.world.InteractionHand hand, WaterStore store, String message) {
        net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
        boolean water = net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(stack).map(f -> f.getFluid().is(FluidTags.WATER)).orElse(false);
        if (!water) {
            return null;
        }
        if (player.level().isClientSide) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        net.neoforged.neoforge.fluids.FluidUtil.interactWithFluidHandler(player, hand, new WaterTank(store));
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(message, store.waterFill()), true);
        return net.minecraft.world.InteractionResult.CONSUME;
    }
}
