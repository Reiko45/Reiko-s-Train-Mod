package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * A tank wagon: 256 buckets of one fluid (water, lava, oil, BuildCraft's fuels, any mod's fluid). Fill and empty it
 * with buckets, pipes, or a wagon loader beside a tank; a gauge on its side shows how full it is. A full one is
 * heavier to pull. If it's broken up, what's in it is lost.
 */
public class TankWagon extends TrainCarEntity {
    public static final int CAPACITY = 256_000;
    /** The gauge: 0 empty, else 1-4 lit for each quarter (or part of one) full. */
    private static final EntityDataAccessor<Integer> DATA_LEVEL = SynchedEntityData.defineId(TankWagon.class, EntityDataSerializers.INT);

    private final FluidTank tank = new FluidTank(CAPACITY) {
        @Override
        protected void onContentsChanged() {
            showLevel();
        }
    };

    public TankWagon(EntityType<? extends TankWagon> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LEVEL, 0);
    }

    @Override
    public double halfLength() {
        return 2.75;
    }

    @Override
    public double bogieOffset() {
        return 1.75;
    }

    /** Two and a half times as heavy when full. */
    @Override
    public double mass() {
        return 1.0 + 1.5 * tank.getFluidAmount() / (double) CAPACITY;
    }

    @Override
    protected float partHeight() {
        return 2.5F;
    }

    @Override
    public int renderLoad() {
        return entityData.get(DATA_LEVEL);
    }

    /** What's in it, for pipes, buckets and wagon loaders. */
    public FluidTank tank() {
        return tank;
    }

    private void showLevel() {
        int amount = tank.getFluidAmount();
        int level = amount <= 0 ? 0 : Mth.clamp(Mth.ceil(amount * 4.0 / CAPACITY), 1, 4);
        if (entityData.get(DATA_LEVEL) != level) {
            entityData.set(DATA_LEVEL, level);
        }
    }

    /** Click it with a bucket (or any fluid container) to fill it, or with an empty one to draw some off. */
    @Override
    protected InteractionResult interactCar(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (FluidUtil.getFluidHandler(stack).isPresent() && FluidUtil.interactWithFluidHandler(player, hand, tank)) {
            level().playSound(null, getX(), getY() + 1.5, getZ(), net.minecraft.sounds.SoundEvents.BUCKET_FILL, SoundSource.NEUTRAL, 0.8F, 1.0F);
        }
        player.displayClientMessage(contents(), true);
        return InteractionResult.CONSUME;
    }

    /** "Tank wagon: 12 of 256 buckets of Water", or empty. */
    public Component contents() {
        FluidStack fluid = tank.getFluid();
        if (fluid.isEmpty()) {
            return Component.translatable("message.reikotrains.tank.empty", CAPACITY / 1000);
        }
        return Component.translatable("message.reikotrains.tank.contents", fluid.getAmount() / 1000, CAPACITY / 1000, fluid.getHoverName());
    }

    @Override
    protected Item getDropItem() {
        return ModItems.TANK_WAGON.get();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Tank", tank.writeToNBT(registryAccess(), new CompoundTag()));
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        tank.readFromNBT(registryAccess(), tag.getCompound("Tank"));
        showLevel();
    }
}
