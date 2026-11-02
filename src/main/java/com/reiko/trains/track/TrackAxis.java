package com.reiko.trains.track;

import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.Vec3;

/**
 * The line a track block runs along: east-west, north-south, or one of the two diagonals. Each has two sides,
 * +1 (towards {@link #dx}, {@link #dz}) and -1.
 */
public enum TrackAxis implements StringRepresentable {
    X("x", 1, 0),
    Z("z", 0, 1),
    /** South-east to north-west. */
    SE("se", 1, 1),
    /** North-east to south-west. */
    NE("ne", 1, -1);

    /** The step to the next block along the track on the +1 side. */
    public final int dx;
    public final int dz;
    private final String name;
    private final Vec3 dir;

    TrackAxis(String name, int dx, int dz) {
        this.name = name;
        this.dx = dx;
        this.dz = dz;
        this.dir = new Vec3(dx, 0, dz).normalize();
    }

    /** The flat unit direction of the +1 side. */
    public Vec3 dir() {
        return dir;
    }

    /** The flat unit direction out of the given side. */
    public Vec3 out(int side) {
        return side > 0 ? dir : dir.reverse();
    }

    /** How long one block of this track is: 1, or about 1.41 on a diagonal. */
    public double step() {
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The side facing (most nearly) the way of {@code v}, or 0 if {@code v} is square across the track. */
    public int sideToward(Vec3 v) {
        double d = v.x * dir.x + v.z * dir.z;
        return Math.abs(d) < 1.0E-3 ? 0 : d > 0 ? 1 : -1;
    }

    /** The axis (and side) nearest a flat direction: one of eight compass points. */
    public static TrackAxis nearest(Vec3 v) {
        int octant = Math.floorMod(Math.round((float) (Mth.atan2(v.z, v.x) / (Math.PI / 4))), 8);
        return switch (octant) {
            case 0, 4 -> X;
            case 2, 6 -> Z;
            case 1, 5 -> SE;
            default -> NE;
        };
    }

    /** One of the eight compass directions nearest {@code v}, as a flat unit vector. */
    public static Vec3 snap(Vec3 v) {
        TrackAxis axis = nearest(v);
        int side = axis.sideToward(v);
        return axis.out(side == 0 ? 1 : side);
    }

    /** A player's flat look direction from their yaw. */
    public static Vec3 look(float yaw) {
        float r = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(r), 0, Mth.cos(r));
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
