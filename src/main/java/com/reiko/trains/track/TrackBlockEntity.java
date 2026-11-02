package com.reiko.trains.track;

import com.reiko.trains.registry.ModBlockEntities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Keeps the curves drawn from (and to) a track block. Both ends keep each curve; the lower one draws it. */
public class TrackBlockEntity extends BlockEntity {
    private final List<TrackConnection> connections = new ArrayList<>();
    @Nullable
    private AABB renderBox;

    public TrackBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRACK.get(), pos, state);
    }

    public List<TrackConnection> connections() {
        return Collections.unmodifiableList(connections);
    }

    public void add(TrackConnection connection) {
        if (!connections.contains(connection)) {
            connections.add(connection);
            changed();
        }
    }

    public boolean remove(TrackConnection connection) {
        boolean removed = connections.remove(connection);
        if (removed) {
            changed();
        }
        return removed;
    }

    /** Removes the curve to another block, whichever sides it joins. */
    @Nullable
    public TrackConnection removeTo(BlockPos other) {
        for (TrackConnection connection : connections) {
            if (connection.other().equals(other)) {
                remove(connection);
                return connection;
            }
        }
        return null;
    }

    private void changed() {
        renderBox = null;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** The curves this block draws, as stretches of track starting here. */
    public List<EdgeRef> ownedEdges() {
        List<EdgeRef> edges = new ArrayList<>();
        if (level == null || !(getBlockState().getBlock() instanceof TrackBlock)) {
            return edges;
        }
        TrackAxis axis = getBlockState().getValue(TrackBlock.AXIS);
        for (TrackConnection connection : connections) {
            if (connection.ownedBy(worldPosition)) {
                TrackAxis otherAxis = TrackGraph.axis(level, connection.other());
                if (otherAxis != null) {
                    edges.add(new EdgeRef(worldPosition, axis, connection.mySide(), connection.other(), otherAxis, connection.otherSide()));
                }
            }
        }
        return edges;
    }

    /** Everything this block's curves cover, so they're drawn while any of them is on screen. */
    public AABB renderBox() {
        if (renderBox == null) {
            AABB box = new AABB(worldPosition).inflate(1);
            for (EdgeRef edge : ownedEdges()) {
                TrackEdge shape = edge.geometry();
                for (double s = 0; s <= shape.length(); s += 1.0) {
                    Vec3 p = shape.point(s);
                    box = box.minmax(new AABB(p, p).inflate(1.5));
                }
                Vec3 end = shape.point(shape.length());
                box = box.minmax(new AABB(end, end).inflate(1.5));
            }
            renderBox = box;
        }
        return renderBox;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag list = new ListTag();
        for (TrackConnection connection : connections) {
            list.add(connection.save());
        }
        tag.put("Connections", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        connections.clear();
        ListTag list = tag.getList("Connections", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            connections.add(TrackConnection.load(list.getCompound(i)));
        }
        renderBox = null;
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
