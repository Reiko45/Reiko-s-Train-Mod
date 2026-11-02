package com.reiko.trains.menu;

import com.reiko.trains.registry.ModMenus;
import com.reiko.trains.train.FreightWagon;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The freight wagon's inventory: 108 slots in 18 columns and 6 rows, all in view at once, over the player's
 * inventory. Shift-clicking moves stacks between the two.
 */
public class FreightWagonMenu extends AbstractContainerMenu {
    /** Where the player's inventory starts across, centred under the wider wagon grid. */
    public static final int INVENTORY_X = 8 + (FreightWagon.COLUMNS - 9) * 9;
    public static final int WIDTH = FreightWagon.COLUMNS * 18 + 16;
    public static final int HEIGHT = 114 + FreightWagon.ROWS * 18;
    private final Container wagon;

    public FreightWagonMenu(int id, Inventory inventory) {
        this(id, inventory, new SimpleContainer(FreightWagon.SLOTS));
    }

    public FreightWagonMenu(int id, Inventory inventory, Container wagon) {
        super(ModMenus.FREIGHT_WAGON.get(), id);
        checkContainerSize(wagon, FreightWagon.SLOTS);
        this.wagon = wagon;
        wagon.startOpen(inventory.player);
        for (int row = 0; row < FreightWagon.ROWS; row++) {
            for (int column = 0; column < FreightWagon.COLUMNS; column++) {
                addSlot(new Slot(wagon, column + row * FreightWagon.COLUMNS, 8 + column * 18, 18 + row * 18));
            }
        }
        int top = 103 + (FreightWagon.ROWS - 4) * 18;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, INVENTORY_X + column * 18, top + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, INVENTORY_X + column * 18, top + 58));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            if (index < FreightWagon.SLOTS) {
                if (!moveItemStackTo(stack, FreightWagon.SLOTS, slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, 0, FreightWagon.SLOTS, false)) {
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
        return wagon.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        wagon.stopOpen(player);
    }
}
