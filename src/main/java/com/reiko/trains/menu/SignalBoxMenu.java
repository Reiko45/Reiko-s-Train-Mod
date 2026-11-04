package com.reiko.trains.menu;

import com.reiko.trains.registry.ModBlocks;
import com.reiko.trains.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * A signal box's screen: no slots, just which box it is. While it's open the box sends the player its map and what's
 * going on round it ({@link com.reiko.trains.signal.SignalBoxBlockEntity}); the player's clicks come back as
 * {@link com.reiko.trains.network.SignalBoxActionPayload}s.
 */
public class SignalBoxMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    /** Server: whether this player's been sent the map yet. */
    private boolean mapSent;

    /** On the client: the box's position comes in the opening message. */
    public SignalBoxMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        this(id, buf.readBlockPos());
    }

    public SignalBoxMenu(int id, BlockPos pos) {
        super(ModMenus.SIGNAL_BOX.get(), id);
        this.pos = pos.immutable();
    }

    public BlockPos pos() {
        return pos;
    }

    public boolean mapSent() {
        return mapSent;
    }

    public void sentMap() {
        mapSent = true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /** Open while the player stands at the box. */
    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(ModBlocks.SIGNAL_BOX.get()) && player.distanceToSqr(pos.getCenter()) <= 64;
    }
}
