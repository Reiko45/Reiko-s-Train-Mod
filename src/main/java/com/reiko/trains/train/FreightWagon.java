package com.reiko.trains.train;

import com.reiko.trains.menu.FreightWagonMenu;
import com.reiko.trains.registry.ModItems;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * An open wagon holding 108 stacks (four chests' worth). Hoppers above or below it fill and empty it, and crates
 * pile up in it as it fills. A full wagon is heavier for the locomotive to pull.
 */
public class FreightWagon extends TrainCarEntity implements Container {
    public static final int COLUMNS = 18;
    public static final int ROWS = 6;
    public static final int SLOTS = COLUMNS * ROWS;
    /** How many of the four crates to show. */
    private static final EntityDataAccessor<Integer> DATA_LOAD = SynchedEntityData.defineId(FreightWagon.class, EntityDataSerializers.INT);

    private final NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private int usedSlots;

    public FreightWagon(EntityType<? extends FreightWagon> type, Level level) {
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
        builder.define(DATA_LOAD, 0);
    }

    @Override
    public double halfLength() {
        return 2.5;
    }

    @Override
    public double bogieOffset() {
        return 1.5;
    }

    /** One and a half again when full. */
    @Override
    public double mass() {
        return 1.0 + 1.5 * usedSlots / SLOTS;
    }

    @Override
    protected float partHeight() {
        return 2.2F;
    }

    @Override
    public int renderLoad() {
        return getLoad();
    }

    /** 0 when empty; otherwise 1-4 crates, for each quarter (or part of one) of the slots used. */
    public int getLoad() {
        return entityData.get(DATA_LOAD);
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && tickCount % 10 == 0) {
            int used = 0;
            for (ItemStack stack : items) {
                if (!stack.isEmpty()) {
                    used++;
                }
            }
            usedSlots = used;
            int load = used == 0 ? 0 : Mth.clamp(Mth.ceil(used * 4.0 / SLOTS), 1, 4);
            if (load != getLoad()) {
                entityData.set(DATA_LOAD, load);
            }
        }
    }

    @Override
    protected InteractionResult interactCar(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (!level().isClientSide) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new FreightWagonMenu(id, inventory, this), getDisplayName()));
        }
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    /** However the wagon comes apart (broken, derailed, or an old one turned back into an item), its load spills out. */
    @Override
    public void destroy(Item dropItem) {
        if (!level().isClientSide) {
            Containers.dropContents(level(), this, this);
        }
        super.destroy(dropItem);
    }

    @Override
    protected Item getDropItem() {
        return ModItems.FREIGHT_WAGON.get();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Container

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
    }

    @Override
    public void setChanged() {
    }

    @Override
    public boolean stillValid(Player player) {
        return !isRemoved() && player.distanceToSqr(this) < 64.0;
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        ContainerHelper.saveAllItems(tag, items, registryAccess());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registryAccess());
    }
}
