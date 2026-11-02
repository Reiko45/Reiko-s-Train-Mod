package com.reiko.trains.signal;

import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.track.EdgeRef;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackGraph;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * A switch's workings: the junction it works (a track block, and the side of it the ways divide on) and which way it
 * sends trains. Set normally it sends them the straightest way; thrown, the way that bends furthest off it (to the
 * left or right, at a three-way junction). The thrown state lives in the block state, so the stand shows it.
 */
public class SwitchBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos node;
    private int side = 1;
    /** Which way a thrown switch bends at a three-way junction: -1 left, +1 right. */
    private int thrownSide = 1;
    /** The train that last set it, and until when (game time) it keeps it: so two trains don't both work it. */
    @Nullable
    private UUID claimant;
    private long claimedUntil;
    private boolean registered;
    @Nullable
    private BlockPos registeredNode;

    private static final int CLAIM_TICKS = 40;

    public SwitchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SWITCH.get(), pos, state);
    }

    public void attach(BlockPos node, int side) {
        unregister();
        this.node = node.immutable();
        this.side = side;
        // A thrown switch goes whichever way the junction's branch bends.
        List<EdgeRef> ways = ways();
        if (ways.size() >= 2) {
            EdgeRef straight = TrackGraph.choose(ways, heading(), 0);
            thrownSide = TrackGraph.choose(ways, heading(), 1) == straight ? -1 : 1;
        }
        setChanged();
        register();
    }

    public BlockPos node() {
        return node == null ? worldPosition : node;
    }

    public int side() {
        return side;
    }

    public int thrownSide() {
        return thrownSide;
    }

    public boolean isThrown() {
        return getBlockState().getValue(SwitchBlock.THROWN);
    }

    /** Held by redstone: timetabled trains can't change it. */
    public boolean isLocked() {
        return getBlockState().getValue(SwitchBlock.POWERED);
    }

    public void setThrown(boolean thrown) {
        if (level != null && isThrown() != thrown) {
            level.setBlock(worldPosition, getBlockState().setValue(SwitchBlock.THROWN, thrown), 3);
        }
    }

    /** For a three-way junction: which way the thrown switch bends. */
    public void flipThrownSide() {
        thrownSide = -thrownSide;
        setChanged();
    }

    private Vec3 heading() {
        TrackAxis axis = level == null ? null : TrackGraph.axis(level, node());
        return axis == null ? Vec3.ZERO : axis.out(side);
    }

    /** The ways out of the junction this switch works. */
    public List<EdgeRef> ways() {
        return level == null || node == null ? List.of() : TrackGraph.edgesFrom(level, node, side);
    }

    /** The way the switch sends trains, out of these (the ways out of its junction). */
    public EdgeRef way(List<EdgeRef> options, Vec3 heading) {
        EdgeRef straight = TrackGraph.choose(options, heading, 0);
        if (!isThrown() || options.size() < 2) {
            return straight;
        }
        EdgeRef bent = TrackGraph.choose(options, heading, thrownSide);
        return bent != straight ? bent : TrackGraph.choose(options, heading, -thrownSide);
    }

    /**
     * A timetabled train sets the switch to send it along {@code wanted}, as it comes up to it. A train that's set it
     * keeps it for a couple of seconds after it last asked, so another train (say on the other way in) can't throw it
     * over meanwhile.
     */
    public void setTo(EdgeRef wanted, List<EdgeRef> options, Vec3 heading, UUID train) {
        if (level == null || isLocked()) {
            return;
        }
        long now = level.getGameTime();
        if (claimant != null && !claimant.equals(train) && now < claimedUntil) {
            return;
        }
        claimant = train;
        claimedUntil = now + CLAIM_TICKS;
        EdgeRef straight = TrackGraph.choose(options, heading, 0);
        if (wanted.equals(straight)) {
            setThrown(false);
            return;
        }
        if (!wanted.equals(TrackGraph.choose(options, heading, thrownSide))) {
            thrownSide = -thrownSide;
            setChanged();
        }
        setThrown(true);
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
        tag.putByte("ThrownSide", (byte) thrownSide);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Track")) {
            node = BlockPos.of(tag.getLong("Track"));
            side = tag.getByte("Side");
        }
        thrownSide = tag.getByte("ThrownSide") < 0 ? -1 : 1;
    }
}
