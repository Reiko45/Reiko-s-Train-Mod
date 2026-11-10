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
                shape = TrackEdge.between(edge.from(), edge.fromAxis(), edge.fromSide(), edge.to(), edge.toAxis(), edge.toSide(),
                        edge.fromStreet(), edge.toStreet());
                SHAPES.put(edge, shape);
            }
            return shape;
        }
    }

    /** The axis of the track block here (or of a turntable's bridge over it), or null if there isn't one (or it isn't loaded). */
    @Nullable
    public static TrackAxis axis(Level level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof TrackBlock) {
            return state.getValue(TrackBlock.AXIS);
        }
        // Over a turntable's pit, its bridge (lined up, along its line) is track.
        return com.reiko.trains.turntable.TurntableBlockEntity.bridgeAxis(level, pos);
    }

    /** Whether the track block here is street track (and so its rails are at the top of the block). */
    public static boolean street(Level level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof StreetTrackBlock;
    }

    /** The middle of the rail tops of the track block here. */
    public static Vec3 point(Level level, BlockPos pos) {
        return TrackEdge.node(pos, street(level, pos));
    }

    /** Every stretch of track leaving this block by this side. */
    public static List<EdgeRef> edgesFrom(Level level, BlockPos node, int side) {
        TrackAxis axis = axis(level, node);
        if (axis == null) {
            return List.of();
        }
        List<EdgeRef> edges = new ArrayList<>(2);
        boolean street = street(level, node);
        BlockPos next = node.offset(side * axis.dx, 0, side * axis.dz);
        if (axis(level, next) == axis) {
            edges.add(new EdgeRef(node, axis, side, next, axis, -side, street, street(level, next)));
        } else {
            BlockPos step = streetStep(level, node, axis, side, street);
            if (step != null) {
                edges.add(new EdgeRef(node, axis, side, step, axis, -side, street, !street));
            }
        }
        if (level.getBlockEntity(node) instanceof TrackBlockEntity track) {
            for (TrackConnection connection : track.connections()) {
                if (connection.mySide() == side) {
                    TrackAxis otherAxis = axis(level, connection.other());
                    if (otherAxis != null) {
                        edges.add(new EdgeRef(node, axis, side, connection.other(), otherAxis, connection.otherSide(), street,
                                street(level, connection.other())));
                    }
                }
            }
        }
        return edges;
    }

    /**
     * Street track running on into train track, in line: the train track block a block up from the street track's
     * (on the ground the street's set into), whose rails are a quarter of a block above the street's. They join up as
     * if they were level (or the other way, from the train track down to the street's).
     */
    @Nullable
    private static BlockPos streetStep(Level level, BlockPos node, TrackAxis axis, int side, boolean street) {
        BlockPos next = node.offset(side * axis.dx, street ? 1 : -1, side * axis.dz);
        if (!level.isLoaded(next)) {
            return null;
        }
        BlockState state = level.getBlockState(next);
        return state.getBlock() instanceof TrackBlock && state.getValue(TrackBlock.AXIS) == axis && (state.getBlock() instanceof StreetTrackBlock) != street
                ? next : null;
    }

    /**
     * The place on the track nearest {@code point}: within {@code reach} blocks of it across, and a block and a half up
     * or down, facing the way along the track nearest {@code look}. Finds it on a curve, or along a diagonal, where the
     * block clicked isn't a track block. Null if there's no track that close.
     */
    @Nullable
    public static TrackPos nearest(Level level, Vec3 point, Vec3 look, double reach) {
        java.util.Set<EdgeRef> edges = new java.util.LinkedHashSet<>();
        BlockPos centre = BlockPos.containing(point);
        // Track blocks close by, and the stretches running out of them.
        for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-2, -2, -2), centre.offset(2, 2, 2))) {
            if (axis(level, pos) != null) {
                BlockPos node = pos.immutable();
                edges.addAll(edgesFrom(level, node, 1));
                edges.addAll(edgesFrom(level, node, -1));
            }
        }
        // A curve can run a long way from the track blocks at its ends: every curve starting within a few chunks.
        net.minecraft.world.level.ChunkPos chunk = new net.minecraft.world.level.ChunkPos(centre);
        for (int cx = chunk.x - 3; cx <= chunk.x + 3; cx++) {
            for (int cz = chunk.z - 3; cz <= chunk.z + 3; cz++) {
                net.minecraft.world.level.chunk.LevelChunk loaded = level.getChunkSource().getChunkNow(cx, cz);
                if (loaded == null) {
                    continue;
                }
                for (net.minecraft.world.level.block.entity.BlockEntity blockEntity : loaded.getBlockEntities().values()) {
                    if (blockEntity instanceof TrackBlockEntity track) {
                        edges.addAll(track.ownedEdges());
                    }
                }
            }
        }
        EdgeRef best = null;
        double bestAlong = 0;
        double bestDistance = reach * reach;
        for (EdgeRef edge : edges) {
            TrackEdge shape = edge.geometry();
            double length = shape.length();
            int steps = Math.max(1, (int) Math.ceil(length / 0.2));
            for (int i = 0; i <= steps; i++) {
                double along = length * i / steps;
                Vec3 p = shape.point(along);
                double dx = p.x - point.x;
                double dz = p.z - point.z;
                double across = dx * dx + dz * dz;
                if (across <= bestDistance && Math.abs(p.y - point.y) <= 1.5) {
                    best = edge;
                    bestAlong = along;
                    bestDistance = across;
                }
            }
        }
        if (best == null) {
            return null;
        }
        Vec3 tangent = best.geometry().tangent(bestAlong);
        return new TrackPos(best, bestAlong, tangent.x * look.x + tangent.z * look.z >= 0);
    }

    /**
     * Moves {@code centre} along the track as little as it can so that there's track {@code half} blocks either way of
     * it: a car put down near the end of a line goes as near where it was put as it fits. Null if the track's too short.
     */
    @Nullable
    public static TrackPos fit(Level level, TrackPos centre, double half) {
        TrackPos at = centre.copy();
        for (int attempt = 0; attempt < 4; attempt++) {
            double ahead = at.copy().walk(level, half, TrackPos.STRAIGHTEST);
            double behind = at.copy().walk(level, -half, TrackPos.STRAIGHTEST);
            boolean shortAhead = ahead < half - 1.0E-6;
            boolean shortBehind = behind < half - 1.0E-6;
            if (!shortAhead && !shortBehind) {
                return at;
            }
            if (shortAhead && shortBehind) {
                return null;
            }
            double shift = shortAhead ? -(half - ahead) : half - behind;
            if (at.walk(level, shift, TrackPos.STRAIGHTEST) < Math.abs(shift) - 1.0E-6) {
                return null;
            }
        }
        return null;
    }

    /** Whether this stretch of track is still there. */
    public static boolean exists(Level level, EdgeRef edge) {
        if (axis(level, edge.from()) != edge.fromAxis() || axis(level, edge.to()) != edge.toAxis()
                || street(level, edge.from()) != edge.fromStreet() || street(level, edge.to()) != edge.toStreet()) {
            return false;
        }
        BlockPos next = edge.from().offset(edge.fromSide() * edge.fromAxis().dx, 0, edge.fromSide() * edge.fromAxis().dz);
        if (next.equals(edge.to()) && edge.fromAxis() == edge.toAxis() && edge.toSide() == -edge.fromSide()) {
            return true;
        }
        if (edge.fromAxis() == edge.toAxis() && edge.toSide() == -edge.fromSide() && edge.fromStreet() != edge.toStreet()
                && edge.to().equals(streetStep(level, edge.from(), edge.fromAxis(), edge.fromSide(), edge.fromStreet()))
                && axis(level, next) != edge.fromAxis()) {
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
