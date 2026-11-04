package com.reiko.trains.signal;

import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.track.EdgeRef;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.track.TrackPos;
import com.reiko.trains.track.TrackScanner;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A signal's workings. It guards the way out of one track block in one direction: the section of track from there to
 * the next signal facing the same way (down every branch, up to {@link #MAX_SECTION} blocks). It shows red while
 * any train is in that section, or while it's powered by redstone, and trains stop before it.
 *
 * <p>A three-light signal ({@link ThreeLightSignalBlock}) also keeps an eye on the next signal along the way the
 * switches are set (found again every second, as they may be thrown), and shows yellow while it's clear itself but
 * that one's red.
 */
public class SignalBlockEntity extends BlockEntity {
    public static final double MAX_SECTION = 512;
    private static final int SECTION_REFRESH = 100;
    /** How often a three-light signal looks for the next signal again, in its checks (every four ticks). */
    private static final int ROUTE_REFRESH = 5;

    @Nullable
    private BlockPos node;
    private int side = 1;
    @Nullable
    private Set<EdgeRef> section;
    private int sectionAge;
    private boolean occupied;
    /** A three-light signal: whether it's showing yellow; and the next signal along the way (its track block and side). */
    private boolean caution;
    @Nullable
    private BlockPos nextNode;
    private int nextSide;
    private int routeAge = ROUTE_REFRESH;
    private boolean registered;
    @Nullable
    private BlockPos registeredNode;

    public SignalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SIGNAL.get(), pos, state);
    }

    /** Ties the signal to the track block it guards, and the side of it trains leave by. */
    public void attach(BlockPos node, int side) {
        unregister();
        this.node = node.immutable();
        this.side = side;
        this.section = null;
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

    public boolean isOccupied() {
        return occupied;
    }

    /** Whether it's showing yellow (only a three-light signal does). */
    public boolean showsCaution() {
        return caution;
    }

    public boolean isThreeLight() {
        return getBlockState().getBlock() instanceof ThreeLightSignalBlock;
    }

    public boolean isPowered() {
        return level != null && level.hasNeighborSignal(worldPosition);
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

    /** The stretch of track this signal guards, worked out now and then (the track can change). */
    private Set<EdgeRef> section() {
        if (section == null || ++sectionAge > SECTION_REFRESH) {
            sectionAge = 0;
            section = new HashSet<>();
            if (isAttached()) {
                build();
            }
        }
        return section;
    }

    private record Step(BlockPos node, int side, double length) {
    }

    private void build() {
        Deque<Step> open = new ArrayDeque<>();
        open.add(new Step(node, side, 0));
        int guard = 0;
        boolean cut = false;
        while (!open.isEmpty() && guard++ < 4096) {
            Step step = open.poll();
            cut |= runsIntoUnloaded(step.node, step.side);
            for (EdgeRef edge : TrackGraph.edgesFrom(level, step.node, step.side)) {
                if (section.contains(edge)) {
                    continue;
                }
                section.add(edge);
                section.add(edge.reversed());
                double length = step.length + edge.geometry().length();
                SignalBlockEntity next = TrackSide.signal(level, edge.to(), -edge.toSide());
                if (length < MAX_SECTION && next == null) {
                    open.add(new Step(edge.to(), -edge.toSide(), length));
                }
            }
        }
        if (cut) {
            // Some of it runs into the world still loading: look again soon, not in twenty seconds.
            sectionAge = SECTION_REFRESH - 5;
        }
    }

    /** Whether track out of this block by this side could run on into a chunk that isn't loaded (yet). */
    private boolean runsIntoUnloaded(BlockPos at, int out) {
        com.reiko.trains.track.TrackAxis axis = TrackGraph.axis(level, at);
        if (axis == null) {
            return false;
        }
        if (!level.isLoaded(at.offset(out * axis.dx, 0, out * axis.dz))) {
            return true;
        }
        if (level.getBlockEntity(at) instanceof com.reiko.trains.track.TrackBlockEntity track) {
            for (com.reiko.trains.track.TrackConnection connection : track.connections()) {
                if (connection.mySide() == out && !level.isLoaded(connection.other())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a train may pass: the signal's on track and not held by redstone, and nothing but the train asking
     * (any of {@code ignoring}) is in the section ahead.
     */
    public boolean isClearFor(Collection<? extends TrainCarEntity> ignoring) {
        if (!isAttached() || isPowered()) {
            return false;
        }
        return firstCarInSection(ignoring) == null;
    }

    @Nullable
    private TrainCarEntity firstCarInSection(Collection<? extends TrainCarEntity> ignoring) {
        Set<EdgeRef> edges = section();
        if (edges.isEmpty()) {
            return null;
        }
        for (TrainCarEntity car : Trains.loadedCars(level)) {
            if (ignoring.contains(car) || !car.isOnTrack()) {
                continue;
            }
            if (edges.contains(car.front().edge()) || edges.contains(car.rear().edge())) {
                return car;
            }
        }
        return null;
    }

    /** Forgets the section, so it's worked out again (after the track or another signal changes). */
    public void refresh() {
        section = null;
        routeAge = ROUTE_REFRESH;
    }

    /** The next signal along the way the switches are set (looked for again now and then), or null if there's none in reach. */
    @Nullable
    public SignalBlockEntity nextSignal() {
        if (++routeAge >= ROUTE_REFRESH) {
            routeAge = 0;
            findNext();
        }
        if (nextNode == null || !level.isLoaded(nextNode)) {
            return null;
        }
        return TrackSide.signal(level, nextNode, nextSide);
    }

    private void findNext() {
        nextNode = null;
        if (!isAttached()) {
            return;
        }
        TrackPos.Chooser route = (at, side, options, heading) -> TrackSide.wayOn(level, at, side, options, heading, 0);
        List<EdgeRef> out = TrackGraph.edgesFrom(level, node, side);
        if (out.isEmpty()) {
            return;
        }
        com.reiko.trains.track.TrackAxis axis = TrackGraph.axis(level, node);
        EdgeRef first = route.choose(node, side, out, axis.out(side));
        TrackScanner.scan(level, new TrackPos(first, 0, true), 1, MAX_SECTION, route, (at, arrivedSide, distance) -> {
            if (TrackSide.signal(level, at, -arrivedSide) != null) {
                nextNode = at.immutable();
                nextSide = -arrivedSide;
                return false;
            }
            return true;
        });
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SignalBlockEntity signal) {
        if ((level.getGameTime() + pos.hashCode()) % 4 != 0) {
            return;
        }
        TrainCarEntity car = signal.isAttached() ? signal.firstCarInSection(List.of()) : null;
        boolean occupied = car != null;
        boolean clear = signal.isAttached() && !occupied && !signal.isPowered();
        boolean threeLight = state.getBlock() instanceof ThreeLightSignalBlock;
        SignalBlockEntity next = threeLight ? signal.nextSignal() : null;
        boolean caution = clear && next != null && !next.getBlockState().getValue(SignalBlock.CLEAR);
        if (occupied != signal.occupied || caution != signal.caution) {
            signal.occupied = occupied;
            signal.caution = caution;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
        BlockState shown = state.setValue(SignalBlock.CLEAR, clear);
        if (threeLight) {
            shown = shown.setValue(ThreeLightSignalBlock.CAUTION, caution);
        }
        if (shown != state) {
            level.setBlock(pos, shown, 3);
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
