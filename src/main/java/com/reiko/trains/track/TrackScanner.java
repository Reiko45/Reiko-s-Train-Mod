package com.reiko.trains.track;

import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Looking along the track without moving anything: what's ahead, and how far it is to somewhere. */
public final class TrackScanner {
    private TrackScanner() {
    }

    /** Told about each track block reached while scanning. Return false to stop there. */
    @FunctionalInterface
    public interface Visitor {
        boolean node(BlockPos node, int arrivedSide, double distance);
    }

    /** How a scan ended: how far it got, and whether that was the end of the track. */
    public record End(double distance, boolean endOfTrack) {
    }

    /**
     * Walks along the track from {@code start}, the way {@code direction} (+1 the way the car faces, -1 the other),
     * up to {@code maxDistance} blocks, telling {@code visitor} about each track block reached and choosing at
     * junctions with {@code chooser}.
     */
    public static End scan(Level level, TrackPos start, int direction, double maxDistance, TrackPos.Chooser chooser, Visitor visitor) {
        EdgeRef edge = start.edge();
        double distance = start.distance();
        boolean along = (direction >= 0) == start.facingAlong();
        double travelled = 0;
        for (int guard = 0; guard < 4096; guard++) {
            TrackEdge shape = edge.geometry();
            double room = along ? shape.length() - distance : distance;
            if (travelled + room > maxDistance) {
                return new End(maxDistance, false);
            }
            travelled += room;
            BlockPos node = along ? edge.to() : edge.from();
            int arrivedSide = along ? edge.toSide() : edge.fromSide();
            if (!visitor.node(node, arrivedSide, travelled)) {
                return new End(travelled, false);
            }
            List<EdgeRef> next = TrackGraph.edgesFrom(level, node, -arrivedSide);
            if (next.isEmpty()) {
                return new End(travelled, true);
            }
            Vec3 heading = along ? shape.tangent(shape.length()) : shape.tangent(0).reverse();
            edge = chooser.choose(node, -arrivedSide, next, heading);
            distance = 0;
            along = true;
        }
        return new End(travelled, false);
    }

    private record State(BlockPos node, int arrivedSide, double cost) {
    }

    /**
     * The shortest distance along the track from {@code start} (going the way {@code direction}) to the track block
     * {@code target}, or -1 if it can't be found within {@code limit} blocks (or in the loaded world). Any way at
     * any junction is tried.
     */
    public static double distanceTo(Level level, TrackPos start, int direction, BlockPos target, double limit) {
        EdgeRef edge = start.edge();
        boolean along = (direction >= 0) == start.facingAlong();
        double room = along ? edge.geometry().length() - start.distance() : start.distance();
        BlockPos node = along ? edge.to() : edge.from();
        int arrivedSide = along ? edge.toSide() : edge.fromSide();
        return search(level, new State(node, arrivedSide, room), target, limit);
    }

    /** The shortest distance to {@code target} going by way of {@code first}, from its start; -1 if not found. */
    public static double distanceVia(Level level, EdgeRef first, BlockPos target, double limit) {
        return search(level, new State(first.to(), first.toSide(), first.geometry().length()), target, limit);
    }

    private static double search(Level level, State start, BlockPos target, double limit) {
        PriorityQueue<State> open = new PriorityQueue<>((a, b) -> Double.compare(a.cost, b.cost));
        Set<Long> seen = new HashSet<>();
        open.add(start);
        int expansions = 0;
        while (!open.isEmpty() && expansions++ < 20000) {
            State state = open.poll();
            if (state.cost > limit) {
                return -1;
            }
            if (state.node.equals(target)) {
                return state.cost;
            }
            if (!seen.add(state.node.asLong() * 2 + (state.arrivedSide > 0 ? 1 : 0))) {
                continue;
            }
            for (EdgeRef next : TrackGraph.edgesFrom(level, state.node, -state.arrivedSide)) {
                open.add(new State(next.to(), next.toSide(), state.cost + next.geometry().length()));
            }
        }
        return -1;
    }
}
