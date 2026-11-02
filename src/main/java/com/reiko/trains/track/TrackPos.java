package com.reiko.trains.track;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Where something (a bogie) is on the track: a stretch of track, how far along it, and which way along it the car
 * it belongs to faces. It moves by walking along the track, choosing at junctions.
 */
public final class TrackPos {
    private EdgeRef edge;
    private double distance;
    /** Whether the car's front points along the edge (from its start to its end). */
    private boolean facingAlong;

    public TrackPos(EdgeRef edge, double distance, boolean facingAlong) {
        this.edge = edge;
        this.distance = distance;
        this.facingAlong = facingAlong;
    }

    public TrackPos copy() {
        return new TrackPos(edge, distance, facingAlong);
    }

    public EdgeRef edge() {
        return edge;
    }

    public double distance() {
        return distance;
    }

    public boolean facingAlong() {
        return facingAlong;
    }

    public Vec3 position() {
        return edge.geometry().point(distance);
    }

    /** The way the car faces here: along the track, sloping on a ramp. */
    public Vec3 facing() {
        Vec3 t = edge.geometry().tangent(distance);
        return facingAlong ? t : t.reverse();
    }

    /** Picks the way on at a junction: the track block arrived at, the side being left by, and the ways on. */
    @FunctionalInterface
    public interface Chooser {
        EdgeRef choose(BlockPos node, int side, List<EdgeRef> options, Vec3 heading);
    }

    /** The straightest way on, every time. */
    public static final Chooser STRAIGHTEST = (node, side, options, heading) -> TrackGraph.choose(options, heading, 0);

    /**
     * Moves along the track: {@code delta} blocks forwards (the way the car faces) or backwards if negative. Stops
     * at the end of the track (or at track that isn't loaded). Returns how far it actually went (not negative).
     */
    public double walk(Level level, double delta, Chooser chooser) {
        double remaining = Math.abs(delta);
        boolean along = (delta >= 0) == facingAlong;
        double moved = 0;
        for (int guard = 0; guard < 64 && remaining > 1.0E-9; guard++) {
            TrackEdge shape = edge.geometry();
            double room = along ? shape.length() - distance : distance;
            if (remaining <= room) {
                distance += along ? remaining : -remaining;
                return moved + remaining;
            }
            moved += room;
            remaining -= room;
            // At the block at this end of the stretch: carry on out of its other side.
            BlockPos node = along ? edge.to() : edge.from();
            int arrivedSide = along ? edge.toSide() : edge.fromSide();
            Vec3 heading = along ? shape.tangent(shape.length()) : shape.tangent(0).reverse();
            List<EdgeRef> next = TrackGraph.edgesFrom(level, node, -arrivedSide);
            if (next.isEmpty()) {
                distance = along ? shape.length() : 0;
                return moved;
            }
            EdgeRef chosen = chooser.choose(node, -arrivedSide, next, heading);
            // The new stretch starts here and runs the way we're going.
            boolean carForwardIsTravel = (delta >= 0);
            edge = chosen;
            distance = 0;
            along = true;
            facingAlong = carForwardIsTravel;
        }
        return moved;
    }

    /** Whether the stretch of track this is on is still all there. */
    public boolean isValid(Level level) {
        return TrackGraph.exists(level, edge);
    }

    /**
     * Finds a place on the track at a track block: on one of its stretches, with the car facing {@code forward} (as
     * near as the track allows). Null if the block has no track running out of it at all.
     */
    @Nullable
    public static TrackPos at(Level level, BlockPos node, Vec3 forward) {
        TrackAxis axis = TrackGraph.axis(level, node);
        if (axis == null) {
            return null;
        }
        int side = axis.sideToward(forward);
        if (side == 0) {
            side = 1;
        }
        List<EdgeRef> ahead = TrackGraph.edgesFrom(level, node, side);
        if (!ahead.isEmpty()) {
            return new TrackPos(TrackGraph.choose(ahead, axis.out(side), 0), 0, true);
        }
        List<EdgeRef> behind = TrackGraph.edgesFrom(level, node, -side);
        if (!behind.isEmpty()) {
            return new TrackPos(TrackGraph.choose(behind, axis.out(-side), 0), 0, false);
        }
        return null;
    }

    public CompoundTag save() {
        CompoundTag tag = edge.save();
        tag.putDouble("Distance", distance);
        tag.putBoolean("FacingAlong", facingAlong);
        return tag;
    }

    public static TrackPos load(CompoundTag tag) {
        return new TrackPos(EdgeRef.load(tag), tag.getDouble("Distance"), tag.getBoolean("FacingAlong"));
    }

    @Override
    public String toString() {
        return "TrackPos[" + edge.from().toShortString() + " -> " + edge.to().toShortString() + " @ " + String.format("%.2f", distance)
                + (facingAlong ? " fwd" : " back") + "]";
    }
}
