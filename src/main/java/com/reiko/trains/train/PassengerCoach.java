package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A coach with four seats. Like a minecart, a moving coach with a free seat picks up mobs (and villagers) it runs
 * into, and a powered activator rail lets everyone off.
 */
public class PassengerCoach extends AbstractMinecart implements TrainCar {
    private static final Seat[] SEATS = {
            new Seat(0.9375, 0.0, 0.55), new Seat(0.3125, 0.0, 0.55), new Seat(-0.3125, 0.0, 0.55), new Seat(-0.9375, 0.0, 0.55)};

    private final TrainCarState trainState = new TrainCarState();

    public PassengerCoach(EntityType<? extends PassengerCoach> type, Level level) {
        super(type, level);
    }

    @Override
    public void tick() {
        if (!level().isClientSide) {
            Trains.follow(this);
        }
        super.tick();
        if (level().isClientSide) {
            Trains.rollWheels(this);
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
    public void activateMinecart(int x, int y, int z, boolean powered) {
        if (powered && isVehicle()) {
            ejectPassengers();
        }
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        InteractionResult coupler = Trains.useCoupler(this, player, hand);
        if (coupler != InteractionResult.PASS) {
            return coupler;
        }
        return Trains.board(this, player);
    }

    @Override
    public TrainCarState trainState() {
        return trainState;
    }

    @Override
    public double halfLength() {
        return 22.0 / 16.0;
    }

    @Override
    public Seat[] seats() {
        return SEATS;
    }

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return Trains.hasFreeSeat(this);
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        Vec3 seat = Trains.seatPosition(this, passenger);
        return seat != null ? seat : super.getPassengerAttachmentPoint(passenger, dimensions, partialTick);
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
        return Type.RIDEABLE;
    }

    @Override
    protected Item getDropItem() {
        return ModItems.PASSENGER_COACH.get();
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
