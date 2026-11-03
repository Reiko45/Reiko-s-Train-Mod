package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A long, low flatbed wagon for carrying road vehicles from Reiko's Car Mod: drive one up to either end and keep going,
 * and it rolls aboard and up to the far end (or up to the cars already there, or on along the train to the next car
 * transporter if this one's empty). Aboard, a car doesn't drive or burn fuel; its driver can stay in it or get out. To
 * drive off, sit in it with the train stopped and keep going forwards or backwards: off the end of the train, or on to
 * the next car transporter that way. (See {@link CarCargo}, which does the car mod's side of it.)
 */
public class CarTransporter extends TrainCarEntity {
    /** How high the deck is above the rails, in blocks. */
    public static final double DECK = 17.0 / 16;
    /** How far the deck reaches either side of the middle, in blocks: cars fit along it end to end, this long in all. */
    public static final double DECK_HALF = 5.75;
    /** The space left between cars. */
    public static final double GAP = 0.2;
    private static final Seat[] NO_SEATS = {};
    /** The cars aboard, synced so the client puts them in their places: [{Id, Along, Facing}]. */
    private static final EntityDataAccessor<CompoundTag> DATA_CARS = SynchedEntityData.defineId(CarTransporter.class,
            EntityDataSerializers.COMPOUND_TAG);

    /**
     * Where a car is on the deck: how far along from the middle (forwards positive), which way it faces (+1 forwards),
     * and how long it is.
     */
    public record Slot(double along, int facing, double length) {
    }

    private final Map<UUID, Slot> slots = new HashMap<>();

    public CarTransporter(EntityType<? extends CarTransporter> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_CARS, new CompoundTag());
    }

    @Override
    public double halfLength() {
        return 6.0;
    }

    @Override
    public double bogieOffset() {
        return 4.25;
    }

    /** Heavier with cars aboard. */
    @Override
    public double mass() {
        return 2.0 + slots.size();
    }

    @Override
    protected float partHeight() {
        return 1.3F;
    }

    @Override
    public Seat[] seats() {
        return NO_SEATS;
    }

    /** The cars aboard, by where they are. */
    public Map<UUID, Slot> slots() {
        return Map.copyOf(slots);
    }

    @Nullable
    public Slot slot(Entity car) {
        return slots.get(car.getUUID());
    }

    public boolean isEmpty() {
        return slots.isEmpty();
    }

    /** The cars aboard. */
    public List<Entity> cars() {
        return getPassengers().stream().filter(passenger -> slots.containsKey(passenger.getUUID())).toList();
    }

    /** Where the deck ends, at the front ({@code end} +1) or back (-1), level with the deck. */
    public Vec3 endPoint(int end) {
        return position().add(flatForward().scale(end * halfLength())).add(0, DECK, 0);
    }

    /**
     * Where a car {@code length} blocks long coming aboard over end {@code end} would end up: as far along as it can go,
     * to the far end or up to the cars already aboard. Null if there isn't room.
     */
    @Nullable
    public Double placeFrom(int end, double length) {
        // Measured from the far end towards this one: the nearest of the cars aboard (to this end) stops it.
        double reached = -DECK_HALF - GAP;
        for (Slot slot : slots.values()) {
            reached = Math.max(reached, end * slot.along() + slot.length() / 2);
        }
        double centre = reached + GAP + length / 2;
        if (centre + length / 2 > DECK_HALF + 1.0E-6) {
            return null;
        }
        return end * centre;
    }

    /** Whether a car at {@code along} has the deck clear between it and end {@code end}. */
    public boolean clearTowards(Entity car, int end) {
        Slot mine = slots.get(car.getUUID());
        if (mine == null) {
            return false;
        }
        for (Map.Entry<UUID, Slot> entry : slots.entrySet()) {
            if (!entry.getKey().equals(car.getUUID()) && end * entry.getValue().along() > end * mine.along()) {
                return false;
            }
        }
        return true;
    }

    /** Takes {@code car} ({@code length} blocks long) aboard, at {@code along} facing {@code facing}. */
    public boolean carry(Entity car, double along, int facing, double length) {
        slots.put(car.getUUID(), new Slot(along, facing, length));
        if (!car.startRiding(this, true)) {
            slots.remove(car.getUUID());
            return false;
        }
        sync();
        return true;
    }

    /** Only cars it's making room for (see {@link #carry}). */
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return slots.containsKey(passenger.getUUID());
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (slots.remove(passenger.getUUID()) != null) {
            sync();
        }
    }

    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        Slot slot = slots.get(passenger.getUUID());
        return forward().scale(slot == null ? 0 : slot.along()).add(0, DECK, 0);
    }

    /** Cars ride facing along the deck, the way they came aboard. */
    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        super.positionRider(passenger, callback);
        Slot slot = slots.get(passenger.getUUID());
        if (slot != null) {
            passenger.setYRot(slot.facing() > 0 ? getYRot() : getYRot() + 180.0F);
            passenger.setXRot(0);
        }
    }

    /** Clicking it says how many cars it has aboard, and how to load one. */
    @Override
    protected InteractionResult interactCar(Player player, InteractionHand hand) {
        if (!level().isClientSide) {
            player.displayClientMessage(CarCargo.CAR_MOD
                    ? Component.translatable("message.reikotrains.car_transporter.info", slots.size())
                    : Component.translatable("message.reikotrains.car_transporter.no_car_mod"), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    protected Item getDropItem() {
        return ModItems.CAR_TRANSPORTER.get();
    }

    private void sync() {
        entityData.set(DATA_CARS, writeSlots());
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key.equals(DATA_CARS) && level().isClientSide) {
            readSlots(entityData.get(DATA_CARS));
        }
    }

    private CompoundTag writeSlots() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Slot> entry : slots.entrySet()) {
            CompoundTag car = new CompoundTag();
            car.putUUID("Id", entry.getKey());
            car.putDouble("Along", entry.getValue().along());
            car.putByte("Facing", (byte) entry.getValue().facing());
            car.putDouble("Length", entry.getValue().length());
            list.add(car);
        }
        tag.put("Cars", list);
        return tag;
    }

    private void readSlots(CompoundTag tag) {
        slots.clear();
        for (Tag t : tag.getList("Cars", Tag.TAG_COMPOUND)) {
            CompoundTag car = (CompoundTag) t;
            if (car.hasUUID("Id")) {
                slots.put(car.getUUID("Id"), new Slot(car.getDouble("Along"), car.getByte("Facing") < 0 ? -1 : 1, car.getDouble("Length")));
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.put("Deck", writeSlots());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        readSlots(tag.getCompound("Deck"));
        sync();
    }
}
