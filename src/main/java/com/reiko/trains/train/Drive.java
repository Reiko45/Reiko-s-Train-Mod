package com.reiko.trains.train;

import com.reiko.trains.track.TrackPos;
import javax.annotation.Nullable;

/**
 * How a train's being driven this tick, worked out by its locomotive before it moves: which way it's heading (+1
 * the train's forwards), how it picks its way at junctions, the most it may go (to stop in time for a red signal, a
 * station or the end of the line), and, for a train on a timetable, the speed it's aiming for.
 *
 * @param automatic whether it's on a timetable, so {@code target} is the speed to aim for (not the throttle)
 */
record Drive(int direction, @Nullable TrackPos.Chooser chooser, double limit, boolean automatic, double target) {
    static final Drive NONE = new Drive(1, null, Double.POSITIVE_INFINITY, false, 0);

    /** Deceleration used for stopping at signals and stations, blocks per tick per tick. */
    static final double SERVICE_BRAKE = 0.006;

    /** The fastest a train can go and still stop within {@code distance} blocks, braking normally. */
    static double stoppingSpeed(double distance) {
        if (distance <= 0.03) {
            return 0;
        }
        return Math.min(Math.sqrt(2 * SERVICE_BRAKE * distance), distance);
    }
}
