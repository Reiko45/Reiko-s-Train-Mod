package com.reiko.trains.signal;

import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.track.TrackGraph;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A speed sign's workings: it's tied to a track block and one side of it, like a signal, and trains leaving that block
 * by that side (coming towards its face) take its limit from there on, until the next speed sign their way.
 */
public class SpeedSignBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos node;
    private int side = 1;
    private boolean registered;
    @Nullable
    private BlockPos registeredNode;

    public SpeedSignBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SPEED_SIGN.get(), pos, state);
    }

    /** Ties it to the track block it stands by, for trains leaving it by {@code side}. */
    public void attach(BlockPos node, int side) {
        unregister();
        this.node = node.immutable();
        this.side = side;
        setChanged();
        register();
    }

    public BlockPos node() {
        return node == null ? worldPosition : node;
    }

    public int side() {
        return side;
    }

    public boolean isAttached() {
        return node != null && level != null && TrackGraph.axis(level, node) != null;
    }

    /** Its limit in km/h, or 0 for the end of a limit. */
    public int limitKmh() {
        return SpeedSignBlock.kmh(getBlockState());
    }

    /** Its limit in blocks per tick (infinite at the end of a limit). */
    public double limit() {
        int kmh = limitKmh();
        return kmh <= 0 ? Double.POSITIVE_INFINITY : SpeedSignBlock.toBlocksPerTick(kmh);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        register();
    }

    @Override
    public void setRemoved() {
        unregister();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        unregister();
        super.onChunkUnloaded();
    }

    private void register() {
        if (level != null && !level.isClientSide && node != null && !registered) {
            TrackSide.add(this);
            registered = true;
            registeredNode = node;
        }
    }

    private void unregister() {
        if (registered && level != null) {
            TrackSide.remove(this, registeredNode);
            registered = false;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (node != null) {
            tag.putLong("Track", node.asLong());
            tag.putByte("Side", (byte) side);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Track")) {
            node = BlockPos.of(tag.getLong("Track"));
            side = tag.getByte("Side");
        }
    }
}
