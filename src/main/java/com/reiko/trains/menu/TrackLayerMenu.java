package com.reiko.trains.menu;

import com.reiko.trains.registry.ModMenus;
import com.reiko.trains.train.TrackLayer;
import javax.annotation.Nullable;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The track-laying wagon's screen: its store (27 slots: track, fill, and whatever it digs up) over the player's
 * inventory, with its on/off switch and what it's doing between them.
 */
public class TrackLayerMenu extends AbstractContainerMenu {
    public static final int BUTTON_SWITCH = 0;
    public static final int INVENTORY_TOP = 114;
    @Nullable
    private final TrackLayer layer;
    private final Container store;
    private final ContainerData data;

    /** On the client. */
    public TrackLayerMenu(int id, Inventory inventory) {
        this(id, inventory, null, new SimpleContainer(TrackLayer.SLOTS), new SimpleContainerData(4));
    }

    /** On the server, for a wagon. */
    public TrackLayerMenu(int id, Inventory inventory, TrackLayer layer) {
        this(id, inventory, layer, layer, new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> layer.isOn() ? 1 : 0;
                    case 1 -> layer.status().ordinal();
                    case 2 -> Math.min(Short.MAX_VALUE, layer.count(stack -> stack.is(com.reiko.trains.registry.ModItems.TRACK.get())));
                    default -> Math.min(Short.MAX_VALUE, layer.count(stack -> stack.is(TrackLayer.TRACKBED)));
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 4;
            }
        });
    }

    private TrackLayerMenu(int id, Inventory inventory, @Nullable TrackLayer layer, Container store, ContainerData data) {
        super(ModMenus.TRACK_LAYER.get(), id);
        checkContainerSize(store, TrackLayer.SLOTS);
        this.layer = layer;
        this.store = store;
        this.data = data;
        store.startOpen(inventory.player);
        for (int row = 0; row < TrackLayer.ROWS; row++) {
            for (int column = 0; column < TrackLayer.COLUMNS; column++) {
                addSlot(new Slot(store, column + row * TrackLayer.COLUMNS, 8 + column * 18, 18 + row * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, INVENTORY_TOP + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, INVENTORY_TOP + 58));
        }
        addDataSlots(data);
    }

    public boolean isOn() {
        return data.get(0) == 1;
    }

    public TrackLayer.Status status() {
        TrackLayer.Status[] all = TrackLayer.Status.values();
        int index = data.get(1);
        return index >= 0 && index < all.length ? all[index] : TrackLayer.Status.READY;
    }

    /** How much track it has. */
    public int track() {
        return data.get(2);
    }

    /** How much fill it has. */
    public int fill() {
        return data.get(3);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (layer != null && id == BUTTON_SWITCH) {
            layer.setOn(!layer.isOn());
            return true;
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            if (index < TrackLayer.SLOTS) {
                if (!moveItemStackTo(stack, TrackLayer.SLOTS, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, TrackLayer.SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
        }
        return moved;
    }

    @Override
    public boolean stillValid(Player player) {
        return store.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        store.stopOpen(player);
    }
}
