package com.reiko.trains.wire;

import com.reiko.trains.registry.ModBlockEntities;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A mast's workings: where it holds the contact wire (over the track beside it), and the spans of wire it holds, with
 * the other masts' wire points, for drawing. The line itself ({@link OverheadLines}) is the record; this is its copy for
 * the client.
 */
public class CatenaryMastBlockEntity extends BlockEntity {
    @Nullable
    private Vec3 wire;
    private final Map<BlockPos, Vec3> links = new HashMap<>();

    public CatenaryMastBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CATENARY_MAST.get(), pos, state);
    }

    /** Where it holds the contact wire, or null if it isn't by any track. */
    @Nullable
    public Vec3 wire() {
        return wire;
    }

    /** The masts it's wired to, and where they hold the wire. */
    public Map<BlockPos, Vec3> links() {
        return links;
    }

    /** Sets where it holds the wire, and puts it on the line. */
    void place(ServerLevel level, Vec3 wire) {
        this.wire = wire;
        OverheadLines.get(level).addMast(worldPosition, wire);
        refresh();
    }

    /** Copies its spans from the line, and tells the clients. */
    public void refresh() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        OverheadLines lines = OverheadLines.get(server);
        Vec3 known = lines.wire(worldPosition);
        if (known != null) {
            wire = known;
        }
        links.clear();
        for (OverheadLines.Span span : lines.spansAt(worldPosition)) {
            BlockPos other = span.other(worldPosition);
            Vec3 otherWire = lines.wire(other);
            if (otherWire != null) {
                links.put(other, otherWire);
            }
        }
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** Refreshes the mast at {@code pos}, if there's one loaded there. */
    public static void refresh(Level level, BlockPos pos) {
        if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof CatenaryMastBlockEntity mast) {
            mast.refresh();
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // A mast from before the line was saved (or one whose line record went missing) puts itself back on it.
        if (level instanceof ServerLevel server && wire != null && !OverheadLines.get(server).hasMast(worldPosition)) {
            OverheadLines.get(server).addMast(worldPosition, wire);
        }
    }

    private CompoundTag write(CompoundTag tag) {
        if (wire != null) {
            tag.putDouble("WireX", wire.x);
            tag.putDouble("WireY", wire.y);
            tag.putDouble("WireZ", wire.z);
        }
        ListTag list = new ListTag();
        for (Map.Entry<BlockPos, Vec3> link : links.entrySet()) {
            CompoundTag l = new CompoundTag();
            l.putLong("Pos", link.getKey().asLong());
            l.putDouble("X", link.getValue().x);
            l.putDouble("Y", link.getValue().y);
            l.putDouble("Z", link.getValue().z);
            list.add(l);
        }
        tag.put("Links", list);
        return tag;
    }

    private void read(CompoundTag tag) {
        wire = tag.contains("WireX") ? new Vec3(tag.getDouble("WireX"), tag.getDouble("WireY"), tag.getDouble("WireZ")) : null;
        links.clear();
        for (Tag t : tag.getList("Links", Tag.TAG_COMPOUND)) {
            CompoundTag l = (CompoundTag) t;
            links.put(BlockPos.of(l.getLong("Pos")), new Vec3(l.getDouble("X"), l.getDouble("Y"), l.getDouble("Z")));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        write(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        read(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return write(new CompoundTag());
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
