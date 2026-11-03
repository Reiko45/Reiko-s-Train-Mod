package com.reiko.trains.wire;

import buildcraftreborn.api.mj.IMjConnector;
import buildcraftreborn.api.mj.IMjReadable;
import buildcraftreborn.api.mj.IMjRedstoneReceiver;
import buildcraftreborn.api.mj.MjAPI;
import buildcraftreborn.api.mj.MjCapabilityHelper;
import com.reiko.trains.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A substation's workings: it feeds the overhead line through the mast it's beside (the nearest, up to four blocks
 * away), taking BuildCraft power (MJ from engines and kinesis pipes, or other mods' energy as BuildCraft's config
 * allows) into the line's store, which every substation on the line adds room to. The store is the line's, not the
 * substation's (see {@link OverheadLines}), so trains anywhere along the line can draw on it.
 */
public class SubstationBlockEntity extends BlockEntity {
    /** How far from a mast it can feed the line, in blocks. */
    public static final int REACH = 4;
    private static final int PERIOD = 10;

    private final Receiver receiver = new Receiver();
    private final MjCapabilityHelper capabilities = new MjCapabilityHelper(receiver);
    private int comparator;

    public SubstationBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUBSTATION.get(), pos, state);
    }

    public MjCapabilityHelper capabilities() {
        return capabilities;
    }

    /** The line it feeds, if it's beside a mast. */
    @Nullable
    public BlockPos line() {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        OverheadLines lines = OverheadLines.get(server);
        BlockPos mast = lines.fedBy(worldPosition);
        return mast == null ? null : lines.line(mast);
    }

    /** Ties it to the nearest mast within reach, if it isn't tied to one; false if there's none. */
    public boolean attach() {
        if (!(level instanceof ServerLevel server)) {
            return false;
        }
        OverheadLines lines = OverheadLines.get(server);
        BlockPos fed = lines.fedBy(worldPosition);
        if (fed != null && lines.hasMast(fed)) {
            return true;
        }
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos at : BlockPos.betweenClosed(worldPosition.offset(-REACH, -2, -REACH), worldPosition.offset(REACH, 2, REACH))) {
            if (lines.hasMast(at)) {
                double distance = at.distSqr(worldPosition);
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = at.immutable();
                }
            }
        }
        if (best == null) {
            return false;
        }
        lines.attachSubstation(worldPosition, best);
        return true;
    }

    /** Puts up to {@code mj} MJ into the line (an admin's top-up, or a test). Returns how much went in. */
    public double addMj(double mj) {
        BlockPos line = line();
        return line == null ? 0 : OverheadLines.get((ServerLevel) level).give(line, mj);
    }

    public int comparatorSignal() {
        return comparator;
    }

    /** Off the line: it no longer adds room to the line's store. */
    void detach() {
        if (level instanceof ServerLevel server) {
            OverheadLines.get(server).detachSubstation(worldPosition);
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SubstationBlockEntity substation) {
        if ((level.getGameTime() + pos.hashCode()) % PERIOD != 0 || !(level instanceof ServerLevel server)) {
            return;
        }
        substation.attach();
        BlockPos line = substation.line();
        OverheadLines lines = OverheadLines.get(server);
        double capacity = line == null ? 0 : lines.capacity(line);
        double stored = line == null ? 0 : lines.energy(line);
        int bars = stored <= 0 || capacity <= 0 ? 0 : Math.max(1, (int) Math.min(4, Math.ceil(stored * 4 / capacity)));
        boolean live = line != null && stored >= 1;
        if (state.getValue(SubstationBlock.LEVEL) != bars || state.getValue(SubstationBlock.LIVE) != live) {
            level.setBlock(pos, state.setValue(SubstationBlock.LEVEL, bars).setValue(SubstationBlock.LIVE, live), 3);
        }
        int signal = stored <= 0 || capacity <= 0 ? 0 : Math.max(1, (int) Math.min(15, stored * 15 / capacity));
        if (signal != substation.comparator) {
            substation.comparator = signal;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    /** What engines, pipes and gauges talk to: takes power while the line has room, and says how full the line is. */
    private final class Receiver implements IMjRedstoneReceiver, IMjReadable {
        @Override
        public boolean canConnect(IMjConnector other) {
            return true;
        }

        private double room() {
            BlockPos line = line();
            if (line == null) {
                return 0;
            }
            OverheadLines lines = OverheadLines.get((ServerLevel) level);
            return Math.max(0, lines.capacity(line) - lines.energy(line));
        }

        @Override
        public long getPowerRequested() {
            return (long) (room() * MjAPI.MJ);
        }

        /** Returns the power it couldn't take, as BuildCraft expects. */
        @Override
        public long receivePower(long microJoules, boolean simulate) {
            BlockPos line = line();
            if (line == null) {
                return microJoules;
            }
            double mj = microJoules / (double) MjAPI.MJ;
            double taken = simulate ? Math.min(mj, room()) : OverheadLines.get((ServerLevel) level).give(line, mj);
            return microJoules - (long) Math.floor(taken * MjAPI.MJ);
        }

        @Override
        public long getStored() {
            BlockPos line = line();
            return line == null ? 0 : (long) (OverheadLines.get((ServerLevel) level).energy(line) * MjAPI.MJ);
        }

        @Override
        public long getCapacity() {
            BlockPos line = line();
            return line == null ? 0 : (long) (OverheadLines.get((ServerLevel) level).capacity(line) * MjAPI.MJ);
        }
    }
}
