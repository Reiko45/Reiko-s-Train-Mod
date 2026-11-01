package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * An open wagon that holds as much as a chest. Hoppers can fill and empty it like a chest minecart, and the crates
 * piled in it show roughly how full it is.
 */
public class FreightWagon extends AbstractMinecartContainer implements TrainCar {
    public static final int SLOTS = 27;
    /** How many of the three crates to show. */
    private static final EntityDataAccessor<Integer> DATA_LOAD = SynchedEntityData.defineId(FreightWagon.class, EntityDataSerializers.INT);

    private final TrainCarState trainState = new TrainCarState();

    public FreightWagon(EntityType<? extends FreightWagon> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LOAD, 0);
    }

    /** 0 when empty; otherwise 1-3 crates, for a third, two thirds or more of the slots used. */
    public int getLoad() {
        return entityData.get(DATA_LOAD);
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            Trains.follow(this);
        }
        super.tick();
        if (level().isClientSide) {
            Trains.rollWheels(this);
        } else if (tickCount % 10 == 0) {
            updateLoad();
        }
    }

    private void updateLoad() {
        int used = 0;
        for (ItemStack stack : getItemStacks()) {
            if (!stack.isEmpty()) {
                used++;
            }
        }
        int load = used == 0 ? 0 : Mth.clamp(Mth.ceil(used * 3.0 / SLOTS), 1, 3);
        if (load != getLoad()) {
            entityData.set(DATA_LOAD, load);
        }
    }

    @Override
    public void moveMinecartOnRail(BlockPos pos) {
        Trains.moveOnRail(this);
    }

    @Override
    protected void applyNaturalSlowdown() {
        Trains.slowDown(this);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        InteractionResult coupler = Trains.useCoupler(this, player, hand);
        if (coupler != InteractionResult.PASS) {
            return coupler;
        }
        return super.interact(player, hand);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory playerInventory) {
        return ChestMenu.threeRows(containerId, playerInventory, this);
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public TrainCarState trainState() {
        return trainState;
    }

    @Override
    public double halfLength() {
        return 20.0 / 16.0;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return Trains.cullingBox(this);
    }

    @Override
    public float getPickRadius() {
        return 0.5F;
    }

    @Override
    public Type getMinecartType() {
        return Type.CHEST;
    }

    @Override
    protected Item getDropItem() {
        return ModItems.FREIGHT_WAGON.get();
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(getDropItem());
    }

    @Override
    public void remove(RemovalReason reason) {
        Trains.onRemoved(this, reason);
        super.remove(reason);
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        Trains.save(this, tag);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        Trains.load(this, tag);
    }
}
