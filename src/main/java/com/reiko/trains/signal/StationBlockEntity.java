package com.reiko.trains.signal;

import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.track.TrackEdge;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A station: a name, how long trains on a timetable wait there, and the track block trains stop at (with the front
 * of the train level with the station sign). It gives a redstone signal while a train stands at it.
 */
public class StationBlockEntity extends BlockEntity {
    public static final int MIN_DWELL = 2;
    public static final int MAX_DWELL = 120;
    public static final int MAX_NAME = 32;

    @Nullable
    private BlockPos node;
    private String name = "Station";
    private int dwellSeconds = 10;
    private boolean registered;
    @Nullable
    private BlockPos registeredNode;

    public StationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STATION.get(), pos, state);
    }

    public void attach(BlockPos node) {
        unregister();
        this.node = node.immutable();
        changed();
        register();
    }

    public BlockPos node() {
        return node == null ? worldPosition : node;
    }

    public boolean isAttached() {
        return node != null && level != null && TrackGraph.axis(level, node) != null;
    }

    public String name() {
        return name;
    }

    public int dwellSeconds() {
        return dwellSeconds;
    }

    public void setName(String name) {
        String trimmed = name.strip();
        this.name = trimmed.isEmpty() ? "Station" : trimmed.length() > MAX_NAME ? trimmed.substring(0, MAX_NAME) : trimmed;
        changed();
    }

    public void setDwellSeconds(int seconds) {
        this.dwellSeconds = Mth.clamp(seconds, MIN_DWELL, MAX_DWELL);
        changed();
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
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

    /** Whether a train is standing at the station: stopped, with a car alongside the sign's track block. */
    public boolean hasTrain() {
        if (!isAttached()) {
            return false;
        }
        Vec3 at = TrackEdge.node(node);
        for (TrainCarEntity car : Trains.loadedCars(level)) {
            if (!car.isOnTrack() || Math.abs(car.trainSpeed()) > 0.005) {
                continue;
            }
            Vec3 d = at.subtract(car.position());
            Vec3 f = car.flatForward();
            double along = Math.abs(d.x * f.x + d.z * f.z);
            double across = Math.abs(d.x * -f.z + d.z * f.x);
            if (along <= car.halfLength() + 0.75 && across < 1.5 && Math.abs(d.y) < 2.5) {
                return true;
            }
        }
        return false;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, StationBlockEntity station) {
        if ((level.getGameTime() + pos.hashCode()) % 10 != 0) {
            return;
        }
        boolean present = station.hasTrain();
        if (state.getValue(StationBlock.POWERED) != present) {
            level.setBlock(pos, state.setValue(StationBlock.POWERED, present), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (node != null) {
            tag.putLong("Track", node.asLong());
        }
        tag.putString("Name", name);
        tag.putInt("Dwell", dwellSeconds);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Track")) {
            node = BlockPos.of(tag.getLong("Track"));
        }
        name = tag.contains("Name") ? tag.getString("Name") : "Station";
        dwellSeconds = tag.contains("Dwell") ? Mth.clamp(tag.getInt("Dwell"), MIN_DWELL, MAX_DWELL) : 10;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
