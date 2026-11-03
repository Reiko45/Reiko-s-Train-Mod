package com.reiko.trains.track;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * The shape of the track between two track blocks: a cubic Bézier curve leaving each block along its axis, which
 * is a straight line when the blocks are in line, an arc round a corner, an S between parallel tracks, and a ramp
 * when one end is higher. Positions along it are by distance (blocks) from its start.
 */
public final class TrackEdge {
    /** How high the rail tops are above the bottom of a track block. */
    public static final double RAIL_HEIGHT = 0.25;
    /** ... and of a street track block: right at its top, level with the road. */
    public static final double STREET_RAIL_HEIGHT = 1.0;
    /** How far out each end's handle reaches, as a share of the distance between the ends. 0.39 makes a 90° bend a good circular arc. */
    private static final double HANDLE = 0.39;

    private final Vec3 p0;
    private final Vec3 p1;
    private final Vec3 p2;
    private final Vec3 p3;
    private final double[] ts;
    private final double[] lengths;
    private final double length;
    private final boolean straight;

    public TrackEdge(Vec3 start, Vec3 startOut, Vec3 end, Vec3 endOut) {
        double dx = end.x - start.x;
        double dz = end.z - start.z;
        double chord = Math.sqrt(dx * dx + dz * dz);
        double h = chord * HANDLE;
        this.p0 = start;
        this.p1 = start.add(startOut.scale(h));
        this.p2 = end.add(endOut.scale(h));
        this.p3 = end;
        Vec3 along = new Vec3(dx, 0, dz).normalize();
        // In line and level: a plain straight line, measured exactly.
        this.straight = startOut.dot(along) > 0.9999 && endOut.dot(along) < -0.9999 && Math.abs(end.y - start.y) < 1.0E-9;
        int samples = straight ? 1 : Math.max(24, (int) Math.ceil(chord * 8));
        ts = new double[samples + 1];
        lengths = new double[samples + 1];
        Vec3 prev = p0;
        double total = 0;
        for (int i = 1; i <= samples; i++) {
            double t = (double) i / samples;
            Vec3 p = bezier(t);
            total += p.distanceTo(prev);
            ts[i] = t;
            lengths[i] = total;
            prev = p;
        }
        this.length = total;
    }

    /** The middle of the rail tops of a track block (or a street track block). */
    public static Vec3 node(BlockPos pos, boolean street) {
        return new Vec3(pos.getX() + 0.5, pos.getY() + (street ? STREET_RAIL_HEIGHT : RAIL_HEIGHT), pos.getZ() + 0.5);
    }

    public static TrackEdge between(BlockPos from, TrackAxis fromAxis, int fromSide, BlockPos to, TrackAxis toAxis, int toSide,
                                    boolean fromStreet, boolean toStreet) {
        return new TrackEdge(node(from, fromStreet), fromAxis.out(fromSide), node(to, toStreet), toAxis.out(toSide));
    }

    public double length() {
        return length;
    }

    public boolean isStraight() {
        return straight;
    }

    private Vec3 bezier(double t) {
        double u = 1 - t;
        double a = u * u * u;
        double b = 3 * u * u * t;
        double c = 3 * u * t * t;
        double d = t * t * t;
        return new Vec3(a * p0.x + b * p1.x + c * p2.x + d * p3.x, a * p0.y + b * p1.y + c * p2.y + d * p3.y,
                a * p0.z + b * p1.z + c * p2.z + d * p3.z);
    }

    private Vec3 derivative(double t) {
        double u = 1 - t;
        double a = 3 * u * u;
        double b = 6 * u * t;
        double c = 3 * t * t;
        return new Vec3(a * (p1.x - p0.x) + b * (p2.x - p1.x) + c * (p3.x - p2.x), a * (p1.y - p0.y) + b * (p2.y - p1.y) + c * (p3.y - p2.y),
                a * (p1.z - p0.z) + b * (p2.z - p1.z) + c * (p3.z - p2.z));
    }

    private Vec3 secondDerivative(double t) {
        double u = 1 - t;
        return new Vec3(6 * u * (p2.x - 2 * p1.x + p0.x) + 6 * t * (p3.x - 2 * p2.x + p1.x), 0,
                6 * u * (p2.z - 2 * p1.z + p0.z) + 6 * t * (p3.z - 2 * p2.z + p1.z));
    }

    /** The curve parameter at a distance along the curve. */
    private double paramAt(double distance) {
        if (distance <= 0) {
            return 0;
        }
        if (distance >= length) {
            return 1;
        }
        int lo = 0;
        int hi = lengths.length - 1;
        while (hi - lo > 1) {
            int mid = (lo + hi) >>> 1;
            if (lengths[mid] < distance) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        double span = lengths[hi] - lengths[lo];
        double f = span <= 0 ? 0 : (distance - lengths[lo]) / span;
        return ts[lo] + (ts[hi] - ts[lo]) * f;
    }

    /** The point on the rail tops (centre of the track) this far along. */
    public Vec3 point(double distance) {
        if (straight) {
            double f = length <= 0 ? 0 : Math.max(0, Math.min(1, distance / length));
            return p0.add(p3.subtract(p0).scale(f));
        }
        return bezier(paramAt(distance));
    }

    /** The direction of the track this far along, pointing from start to end (a unit vector, sloping on a ramp). */
    public Vec3 tangent(double distance) {
        if (straight) {
            return p3.subtract(p0).normalize();
        }
        Vec3 d = derivative(paramAt(distance));
        double len = d.length();
        return len < 1.0E-9 ? p3.subtract(p0).normalize() : d.scale(1 / len);
    }

    /** The tightest the curve turns, as a radius in blocks (looking down from above). Straight track is infinite. */
    public double minRadius() {
        double maxCurvature = 0;
        for (int i = 0; i <= 40; i++) {
            double t = i / 40.0;
            Vec3 d1 = derivative(t);
            Vec3 d2 = secondDerivative(t);
            double speed = Math.sqrt(d1.x * d1.x + d1.z * d1.z);
            if (speed < 1.0E-9) {
                continue;
            }
            maxCurvature = Math.max(maxCurvature, Math.abs(d1.x * d2.z - d1.z * d2.x) / (speed * speed * speed));
        }
        return maxCurvature < 1.0E-9 ? Double.POSITIVE_INFINITY : 1 / maxCurvature;
    }

    /** The steepest the track gets: rise over run. */
    public double maxGradient() {
        double max = 0;
        for (int i = 0; i <= 20; i++) {
            Vec3 d = derivative(i / 20.0);
            double run = Math.sqrt(d.x * d.x + d.z * d.z);
            if (run > 1.0E-9) {
                max = Math.max(max, Math.abs(d.y) / run);
            }
        }
        return max;
    }
}
