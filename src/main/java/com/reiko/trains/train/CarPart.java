package com.reiko.trains.train;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;

/**
 * One section of a train car's hitbox. A car is longer than one box can cover (entity boxes don't turn), so it's
 * made of a row of these along its length; clicking or hitting any of them is clicking or hitting the car.
 */
public class CarPart extends PartEntity<TrainCarEntity> {
    private final EntityDimensions size;
    /** Where along the car this section sits, in blocks forward of its middle. */
    final double offset;

    public CarPart(TrainCarEntity car, double offset, float width, float height) {
        super(car);
        this.offset = offset;
        this.size = EntityDimensions.scalable(width, height);
        refreshDimensions();
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    /**
     * Solid, as a boat is: people, animals, dropped items and the car mod's cars bump into the car instead of passing
     * through it, and can stand on it. (Its passengers don't: riding, they aren't moved by bumping into things.)
     */
    @Override
    public boolean canBeCollidedWith() {
        return !getParent().isRemoved();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return !isInvulnerableTo(source) && getParent().hurt(source, amount);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return getParent().interact(player, hand);
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
