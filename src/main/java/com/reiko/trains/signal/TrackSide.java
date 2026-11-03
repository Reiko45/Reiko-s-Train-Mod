package com.reiko.trains.signal;

import com.reiko.trains.track.EdgeRef;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackGraph;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayList;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Things beside the track (signals, stations, switch stands and speed signs): finding the track block one goes with
 * when it's placed, and knowing which ones are at which track block, so trains can look them up as they go.
 */
public final class TrackSide {
    /** How far (blocks, sideways) from the track a signal or station can be. */
    public static final int REACH = 3;

    private static final Map<Level, Map<Long, List<SignalBlockEntity>>> SIGNALS = new WeakHashMap<>();
    private static final Map<Level, Map<Long, List<StationBlockEntity>>> STATIONS = new WeakHashMap<>();
    private static final Map<Level, Map<Long, List<SwitchBlockEntity>>> SWITCHES = new WeakHashMap<>();
    private static final Map<Level, Map<Long, List<SpeedSignBlockEntity>>> SPEED_SIGNS = new WeakHashMap<>();

    /** A junction: a track block with more than one way out of its side {@code side}. */
    public record Junction(BlockPos node, int side) {
    }

    private TrackSide() {
    }

    /** The nearest track block to {@code pos}, within reach (and a block up or down), or null if there isn't one. */
    @Nullable
    public static BlockPos nearestTrack(Level level, BlockPos pos) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    BlockPos at = pos.offset(dx, dy, dz);
                    if (TrackGraph.axis(level, at) == null) {
                        continue;
                    }
                    double d = dx * dx + dz * dz + dy * dy * 0.5;
                    if (d < bestDistance) {
                        bestDistance = d;
                        best = at;
                    }
                }
            }
        }
        return best;
    }

    /** The nearest junction to {@code pos}, within reach (and a block up or down), or null if there isn't one. */
    @Nullable
    public static Junction nearestJunction(Level level, BlockPos pos) {
        Junction best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -REACH; dx <= REACH; dx++) {
                for (int dz = -REACH; dz <= REACH; dz++) {
                    BlockPos at = pos.offset(dx, dy, dz);
                    if (TrackGraph.axis(level, at) == null) {
                        continue;
                    }
                    double d = dx * dx + dz * dz + dy * dy * 0.5;
                    if (d >= bestDistance) {
                        continue;
                    }
                    for (int side = -1; side <= 1; side += 2) {
                        if (TrackGraph.edgesFrom(level, at, side).size() >= 2) {
                            bestDistance = d;
                            best = new Junction(at.immutable(), side);
                        }
                    }
                }
            }
        }
        return best;
    }

    /** The side of the track block at {@code node} that points (most nearly) the way {@code look} does. */
    public static int sideToward(Level level, BlockPos node, Vec3 look) {
        TrackAxis axis = TrackGraph.axis(level, node);
        if (axis == null) {
            return 1;
        }
        int side = axis.sideToward(look);
        return side == 0 ? 1 : side;
    }

    /** The horizontal direction nearest a flat vector. */
    public static Direction nearestDirection(Vec3 v) {
        return Direction.getNearest(v.x, 0, v.z);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // What's where (server only, loaded ones)

    static synchronized void add(SignalBlockEntity signal) {
        SIGNALS.computeIfAbsent(signal.getLevel(), l -> new HashMap<>()).computeIfAbsent(signal.node().asLong(), k -> new ArrayList<>()).add(signal);
    }

    static synchronized void remove(SignalBlockEntity signal, BlockPos node) {
        Map<Long, List<SignalBlockEntity>> map = SIGNALS.get(signal.getLevel());
        if (map != null && map.containsKey(node.asLong())) {
            map.get(node.asLong()).remove(signal);
        }
    }

    static synchronized void add(StationBlockEntity station) {
        STATIONS.computeIfAbsent(station.getLevel(), l -> new HashMap<>()).computeIfAbsent(station.node().asLong(), k -> new ArrayList<>()).add(station);
    }

    static synchronized void remove(StationBlockEntity station, BlockPos node) {
        Map<Long, List<StationBlockEntity>> map = STATIONS.get(station.getLevel());
        if (map != null && map.containsKey(node.asLong())) {
            map.get(node.asLong()).remove(station);
        }
    }

    static synchronized void add(SwitchBlockEntity stand) {
        SWITCHES.computeIfAbsent(stand.getLevel(), l -> new HashMap<>()).computeIfAbsent(stand.node().asLong(), k -> new ArrayList<>()).add(stand);
    }

    static synchronized void remove(SwitchBlockEntity stand, BlockPos node) {
        Map<Long, List<SwitchBlockEntity>> map = SWITCHES.get(stand.getLevel());
        if (map != null && map.containsKey(node.asLong())) {
            map.get(node.asLong()).remove(stand);
        }
    }

    static synchronized void add(SpeedSignBlockEntity sign) {
        SPEED_SIGNS.computeIfAbsent(sign.getLevel(), l -> new HashMap<>()).computeIfAbsent(sign.node().asLong(), k -> new ArrayList<>()).add(sign);
    }

    static synchronized void remove(SpeedSignBlockEntity sign, BlockPos node) {
        Map<Long, List<SpeedSignBlockEntity>> map = SPEED_SIGNS.get(sign.getLevel());
        if (map != null && map.containsKey(node.asLong())) {
            map.get(node.asLong()).remove(sign);
        }
    }

    /** The speed sign at this track block for trains leaving it by {@code side}, if there is one (and it's loaded). */
    @Nullable
    public static synchronized SpeedSignBlockEntity speedSign(Level level, BlockPos node, int side) {
        Map<Long, List<SpeedSignBlockEntity>> map = SPEED_SIGNS.get(level);
        if (map == null) {
            return null;
        }
        List<SpeedSignBlockEntity> here = map.get(node.asLong());
        if (here == null) {
            return null;
        }
        for (SpeedSignBlockEntity sign : here) {
            if (!sign.isRemoved() && sign.side() == side) {
                return sign;
            }
        }
        return null;
    }

    /** The switch working the junction at this track block where the ways leave by {@code side}, if any (and loaded). */
    @Nullable
    public static synchronized SwitchBlockEntity switchAt(Level level, BlockPos node, int side) {
        Map<Long, List<SwitchBlockEntity>> map = SWITCHES.get(level);
        if (map == null) {
            return null;
        }
        List<SwitchBlockEntity> here = map.get(node.asLong());
        if (here == null) {
            return null;
        }
        for (SwitchBlockEntity stand : here) {
            if (!stand.isRemoved() && stand.side() == side) {
                return stand;
            }
        }
        return null;
    }

    /**
     * The way a train takes at a junction when it isn't choosing for itself: the way the switch is set, if there's a
     * switch, or else the way the driver's steering ({@code steer}: -1 left, 0 straight, +1 right).
     */
    public static EdgeRef wayOn(Level level, BlockPos node, int side, List<EdgeRef> options, Vec3 heading, int steer) {
        if (options.size() > 1) {
            SwitchBlockEntity stand = switchAt(level, node, side);
            if (stand != null) {
                return stand.way(options, heading);
            }
        }
        return TrackGraph.choose(options, heading, steer);
    }

    /** The signal at this track block guarding the way out of it by {@code side}, if there is one (and it's loaded). */
    @Nullable
    public static synchronized SignalBlockEntity signal(Level level, BlockPos node, int side) {
        Map<Long, List<SignalBlockEntity>> map = SIGNALS.get(level);
        if (map == null) {
            return null;
        }
        List<SignalBlockEntity> here = map.get(node.asLong());
        if (here == null) {
            return null;
        }
        for (SignalBlockEntity signal : here) {
            if (!signal.isRemoved() && signal.side() == side) {
                return signal;
            }
        }
        return null;
    }

    /** The station at this track block, if there is one (and it's loaded). */
    @Nullable
    public static synchronized StationBlockEntity station(Level level, BlockPos node) {
        Map<Long, List<StationBlockEntity>> map = STATIONS.get(level);
        if (map == null) {
            return null;
        }
        List<StationBlockEntity> here = map.get(node.asLong());
        if (here == null) {
            return null;
        }
        for (StationBlockEntity station : here) {
            if (!station.isRemoved()) {
                return station;
            }
        }
        return null;
    }
}
