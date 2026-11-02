package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/**
 * An express engine's tender: four times as much coal as a tank engine's firebox, for the engine coupled to it.
 * Shovel fuel into it (or into the engine), or let hoppers and wagon loaders fill it: as a container it has one slot,
 * a chute, that takes fuel and tips it in. The coal heap in it shows how full it is.
 */
public class Tender extends TrainCarEntity implements FuelStore, Container {
    /** 128 coal's worth. */
    public static final double FUEL_CAPACITY = 128 * 1600;
    /** How much coal shows: 0 empty, else 1-3. */
    private static final EntityDataAccessor<Integer> DATA_COAL = SynchedEntityData.defineId(Tender.class, EntityDataSerializers.INT);

    private double fuel;
    /** Fuel tipped in (by a hopper, say) that there isn't room for yet. */
    private ItemStack chute = ItemStack.EMPTY;

    public Tender(EntityType<? extends Tender> type, Level level) {
        super(type, level);
    }

    @Override
    protected CarPart[] createParts() {
        CarPart[] plain = super.createParts();
        CarPart[] parts = new CarPart[plain.length];
        for (int i = 0; i < plain.length; i++) {
            parts[i] = new ContainerCarPart(this, plain[i].offset, 2.0F, partHeight());
        }
        return parts;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_COAL, 0);
    }

    @Override
    public double halfLength() {
        return 2.75;
    }

    @Override
    public double bogieOffset() {
        return 1.75;
    }

    /** Heavier with coal in it. */
    @Override
    public double mass() {
        return 1.5 + fuel / FUEL_CAPACITY;
    }

    @Override
    protected float partHeight() {
        return 2.4F;
    }

    @Override
    public int renderLoad() {
        return entityData.get(DATA_COAL);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Fuel

    @Override
    public double fuel() {
        return fuel;
    }

    @Override
    public double fuelCapacity() {
        return FUEL_CAPACITY;
    }

    @Override
    public void setFuel(double ticks) {
        fuel = Mth.clamp(ticks, 0.0, FUEL_CAPACITY);
        int coal = fuel <= 0 ? 0 : Mth.clamp(Mth.ceil(fuel / FUEL_CAPACITY * 3), 1, 3);
        if (entityData.get(DATA_COAL) != coal) {
            entityData.set(DATA_COAL, coal);
        }
    }

    /** Tips what's in the chute into the tender, as much as there's room for. */
    private void tipChute() {
        if (chute.isEmpty() || !chute.is(SteamLocomotive.FUEL)) {
            return;
        }
        int burn = chute.getBurnTime(RecipeType.SMELTING);
        if (burn <= 0) {
            return;
        }
        int count = Math.min((int) ((FUEL_CAPACITY - fuel) / burn), chute.getCount());
        if (chute.hasCraftingRemainingItem()) {
            count = Math.min(count, 1);
        }
        if (count <= 0) {
            return;
        }
        setFuel(fuel + (double) burn * count);
        ItemStack remainder = chute.getCraftingRemainingItem();
        chute.shrink(count);
        if (chute.isEmpty() && !remainder.isEmpty()) {
            chute = remainder; // a lava bucket leaves its bucket, for a hopper underneath to take
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && !chute.isEmpty() && tickCount % 10 == 0) {
            tipChute();
        }
    }

    @Override
    protected InteractionResult interactCar(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.is(SteamLocomotive.FUEL)) {
            return SteamLocomotive.shovel(player, hand, stack, this, position().add(0, 1.5, 0), "message.reikotrains.tender_fuel",
                    "message.reikotrains.tender_full");
        }
        if (!level().isClientSide) {
            player.displayClientMessage(Component.translatable("message.reikotrains.tender_fuel", fuelPercent()), true);
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public void destroy(Item dropItem) {
        if (!level().isClientSide && !chute.isEmpty()) {
            Containers.dropItemStack(level(), getX(), getY() + 1, getZ(), chute);
            chute = ItemStack.EMPTY;
        }
        super.destroy(dropItem);
    }

    @Override
    protected Item getDropItem() {
        return ModItems.TENDER.get();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("Fuel", fuel);
        if (!chute.isEmpty()) {
            tag.put("Chute", chute.save(registryAccess()));
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setFuel(tag.getDouble("Fuel"));
        chute = tag.contains("Chute") ? ItemStack.parse(registryAccess(), tag.getCompound("Chute")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Container: the chute

    @Override
    public int getContainerSize() {
        return 1;
    }

    @Override
    public boolean isEmpty() {
        return chute.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? chute : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return slot == 0 && !chute.isEmpty() ? chute.split(amount) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack taken = chute;
        chute = ItemStack.EMPTY;
        return slot == 0 ? taken : ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot == 0) {
            chute = stack;
            tipChute();
        }
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(SteamLocomotive.FUEL) && stack.getBurnTime(RecipeType.SMELTING) > 0;
    }

    /** Hoppers can't take the coal back out, only what's left of it (empty buckets). */
    @Override
    public boolean canTakeItem(Container target, int slot, ItemStack stack) {
        return !stack.is(SteamLocomotive.FUEL);
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    @Override
    public void clearContent() {
        chute = ItemStack.EMPTY;
    }
}
