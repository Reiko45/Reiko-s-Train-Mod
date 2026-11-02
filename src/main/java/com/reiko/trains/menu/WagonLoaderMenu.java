package com.reiko.trains.menu;

import com.reiko.trains.freight.LoaderMode;
import com.reiko.trains.freight.WagonLoaderBlockEntity;
import com.reiko.trains.registry.ModMenus;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * A wagon loader's screen: its nine filter slots over the player's inventory, and two buttons: loading or unloading,
 * and whether the filter lets through only what's in it or all but that. The filter slots hold copies: clicking one
 * with an item shows that item there (the item stays in hand), clicking it empty-handed clears it, and shift-clicking
 * an item in the inventory adds it to the filter.
 */
public class WagonLoaderMenu extends AbstractContainerMenu {
    public static final int BUTTON_MODE = 0;
    public static final int BUTTON_FILTER = 1;
    private static final int FILTER = WagonLoaderBlockEntity.FILTER_SLOTS;

    @Nullable
    private final WagonLoaderBlockEntity loader;
    private final BlockPos pos;
    private final ContainerData data;

    /** On the client: the loader's position comes in the opening message. */
    public WagonLoaderMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, inventory, null, buf.readBlockPos(), new SimpleContainer(FILTER), new SimpleContainerData(2));
    }

    /** On the server, for a loader. */
    public WagonLoaderMenu(int id, Inventory inventory, WagonLoaderBlockEntity loader) {
        this(id, inventory, loader, loader.getBlockPos(), loader.filter(), new ContainerData() {
            @Override
            public int get(int index) {
                return index == 0 ? (loader.mode() == LoaderMode.LOAD ? 0 : 1) : (loader.isDeny() ? 1 : 0);
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 2;
            }
        });
    }

    private WagonLoaderMenu(int id, Inventory inventory, @Nullable WagonLoaderBlockEntity loader, BlockPos pos, Container filter, ContainerData data) {
        super(ModMenus.WAGON_LOADER.get(), id);
        this.loader = loader;
        this.pos = pos;
        this.data = data;
        for (int i = 0; i < FILTER; i++) {
            addSlot(new FilterSlot(filter, i, 8 + i * 18, 20));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
        addDataSlots(data);
    }

    public boolean isLoading() {
        return data.get(0) == 0;
    }

    public boolean isDeny() {
        return data.get(1) == 1;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (loader == null) {
            return false;
        }
        if (id == BUTTON_MODE) {
            loader.toggleMode();
            return true;
        }
        if (id == BUTTON_FILTER) {
            loader.setDeny(!loader.isDeny());
            return true;
        }
        return false;
    }

    /** Filter slots take a copy of what's carried (or clear, with nothing carried); the rest work as usual. */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < FILTER) {
            if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE || clickType == ClickType.PICKUP_ALL) {
                ItemStack carried = getCarried();
                slots.get(slotId).set(carried.isEmpty() || clickType == ClickType.QUICK_MOVE ? ItemStack.EMPTY : carried.copyWithCount(1));
            }
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /** Shift-clicking an item in the inventory adds it to the filter (if it isn't there already); nothing moves. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index >= FILTER) {
            ItemStack stack = slots.get(index).getItem();
            if (!stack.isEmpty()) {
                int free = -1;
                for (int i = 0; i < FILTER; i++) {
                    ItemStack entry = slots.get(i).getItem();
                    if (ItemStack.isSameItemSameComponents(entry, stack)) {
                        return ItemStack.EMPTY;
                    }
                    if (entry.isEmpty() && free < 0) {
                        free = i;
                    }
                }
                if (free >= 0) {
                    slots.get(free).set(stack.copyWithCount(1));
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canTakeItemForPickAll(ItemStack stack, Slot slot) {
        return !(slot instanceof FilterSlot);
    }

    @Override
    public boolean canDragTo(Slot slot) {
        return !(slot instanceof FilterSlot);
    }

    @Override
    public boolean stillValid(Player player) {
        if (loader == null) {
            return true;
        }
        return !loader.isRemoved() && player.level().getBlockEntity(pos) == loader && player.distanceToSqr(pos.getCenter()) <= 64;
    }

    /** A filter slot: shows an item, but nothing can really be put in or taken out. */
    public static final class FilterSlot extends Slot {
        FilterSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
