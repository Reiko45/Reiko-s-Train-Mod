package com.reiko.trains.track;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Finding your way along the track. Every track block is a node with two sides; out of each side run its links:
 * to the next block along its line (if that's a track block on the same line), and any curves drawn from it with
 * the track item. More than one link out of a side makes a junction.
 */
public final class TrackGraph {
    private static final int CACHE_SIZE = 8192;
    private static final Map<EdgeRef, TrackEdge> SHAPES = new LinkedHashMap<>(256, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<EdgeRef, TrackEdge> eldest) {
            return size() > CACHE_SIZE;
        }
    };

    private TrackGraph() {
    }

    /** The shape of a stretch of track (worked out once and remembered). */
    public static TrackEdge geometry(EdgeRef edge) {
        synchronized (SHAPES) {
            TrackEdge shape = SHAPES.get(edge);
            if (shape == null) {
                shape = TrackEdge.between(edge.from(), edge.fromAxis(), edge.fromSide(), edge.to(), edge.toAxis(), edge.toSide());
                SHAPES.put(edge, shape);
            }
            return shape;
        }
    }

    /** The axis of the track block here, or null if there isn't one (or it isn't loaded). */
    @Nullable
    public static TrackAxis axis(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof TrackBlock ? state.getValue(TrackBlock.AXIS) : null;
    }

    /** Every stretch of track leaving this block by this side. */
    public static List<EdgeRef> edgesFrom(Level level, BlockPos node, int side) {
        TrackAxis axis = axis(level, node);
        if (axis == null) {
            return List.of();
        }
        List<EdgeRef> edges = new ArrayList<>(2);
        BlockPos next = node.offset(side * axis.dx, 0, side * axis.dz);
        if (axis(level, next) == axis) {
            edges.add(new EdgeRef(node, axis, side, next, axis, -side));
        }
        if (level.getBlockEntity(node) instanceof TrackBlockEntity track) {
            for (TrackConnection connection : track.connections()) {
                if (connection.mySide() == side) {
                    TrackAxis otherAxis = axis(level, connection.other());
                    if (otherAxis != null) {
                        edges.add(new EdgeRef(node, axis, side, connection.other(), otherAxis, connection.otherSide()));
                    }
                }
            }
        }
        return edges;
    }

    /** Whether this stretch of track is still there. */
    public static boolean exists(Level level, EdgeRef edge) {
        if (axis(level, edge.from()) != edge.fromAxis() || axis(level, edge.to()) != edge.toAxis()) {
            return false;
        }
        BlockPos next = edge.from().offset(edge.fromSide() * edge.fromAxis().dx, 0, edge.fromSide() * edge.fromAxis().dz);
        if (next.equals(edge.to()) && edge.fromAxis() == edge.toAxis() && edge.toSide() == -edge.fromSide()) {
            return true;
        }
        return level.getBlockEntity(edge.from()) instanceof TrackBlockEntity track
                && track.connections().contains(new TrackConnection(edge.to(), edge.fromSide(), edge.toSide()));
    }

    /**
     * Picks the way on at a junction. Looking a couple of blocks down each way, {@code steer} -1 takes the one bending
     * furthest left, +1 furthest right, and 0 the straightest.
     */
    public static EdgeRef choose(List<EdgeRef> options, Vec3 heading, int steer) {
        if (options.size() == 1) {
            return options.getFirst();
        }
        EdgeRef best = options.getFirst();
        double bestScore = Double.NEGATIVE_INFINITY;
        for (EdgeRef option : options) {
            TrackEdge shape = option.geometry();
            Vec3 start = shape.point(0);
            Vec3 ahead = shape.point(Math.min(2.5, shape.length())).subtract(start);
            double flat = Math.sqrt(ahead.x * ahead.x + ahead.z * ahead.z);
            if (flat < 1.0E-6) {
                continue;
            }
            double ax = ahead.x / flat;
            double az = ahead.z / flat;
            // Positive is a turn to the right (clockwise, looking down).
            double turn = heading.x * az - heading.z * ax;
            double score = steer == 0 ? -Math.abs(turn) : steer * turn;
            if (score > bestScore) {
                bestScore = score;
                best = option;
            }
        }
        return best;
    }
}
