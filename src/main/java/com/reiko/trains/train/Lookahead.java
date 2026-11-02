package com.reiko.trains.train;

import com.reiko.trains.signal.SignalBlockEntity;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.signal.TrackSide;
import com.reiko.trains.track.TrackPos;
import com.reiko.trains.track.TrackScanner;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * What's on the track ahead of a train, measured from its leading buffer: the next signal (and whether it's clear),
 * the next station, the stop it's heading for, and the end of the line.
 */
final class Lookahead {
    /** How far ahead a train looks, in blocks. */
    static final double RANGE = 80;
    /** How far short of a red signal trains stop. */
    static final double SIGNAL_MARGIN = 1.0;

    /** The next signal: how far, and whether it's clear. Negative distance if there's none in range. */
    double signalDistance = -1;
    boolean signalClear = true;
    /** Where the train has to stop for a red signal (infinite if it doesn't). */
    double signalStop = Double.POSITIVE_INFINITY;
    @Nullable
    StationBlockEntity station;
    double stationDistance = -1;
    /** The stop being made for (from {@code target}): found, and how far. */
    boolean targetFound;
    double targetDistance = Double.POSITIVE_INFINITY;
    boolean endOfLine;
    double endDistance = Double.POSITIVE_INFINITY;

    private Lookahead() {
    }

    /** The car at the front of the train going {@code direction} (+1 the train's forwards). */
    static int leadIndex(List<TrainCarEntity> cars, int direction) {
        return direction > 0 ? 0 : cars.size() - 1;
    }

    /** The bogie at the front of the train going {@code direction}. */
    static TrackPos leadBogie(List<TrainCarEntity> cars, int[] signs, int direction) {
        int index = leadIndex(cars, direction);
        TrainCarEntity car = cars.get(index);
        return direction * signs[index] > 0 ? car.front : car.rear;
    }

    static Lookahead look(List<TrainCarEntity> cars, int[] signs, int direction, TrackPos.Chooser chooser, @Nullable BlockPos target) {
        Lookahead look = new Lookahead();
        int index = leadIndex(cars, direction);
        TrainCarEntity car = cars.get(index);
        Level level = car.level();
        int carDirection = direction * signs[index];
        TrackPos bogie = carDirection > 0 ? car.front : car.rear;
        double overhang = car.halfLength() - car.bogieOffset();
        TrackScanner.End end = TrackScanner.scan(level, bogie, carDirection, RANGE, chooser, (node, arrivedSide, distance) -> {
            double buffer = distance - overhang;
            if (target != null && node.equals(target)) {
                look.targetFound = true;
                look.targetDistance = buffer;
            }
            if (buffer > -0.25 && look.signalDistance < 0) {
                SignalBlockEntity signal = TrackSide.signal(level, node, -arrivedSide);
                if (signal != null) {
                    look.signalDistance = buffer;
                    look.signalClear = signal.isClearFor(cars);
                    if (!look.signalClear) {
                        look.signalStop = buffer - SIGNAL_MARGIN;
                    }
                }
            }
            if (look.station == null && buffer > -2.5) {
                StationBlockEntity station = TrackSide.station(level, node);
                if (station != null) {
                    look.station = station;
                    look.stationDistance = buffer;
                }
            }
            // Nothing beyond the stop being made for, or a red signal, matters yet.
            return !look.targetFound && look.signalStop == Double.POSITIVE_INFINITY;
        });
        if (end.endOfTrack()) {
            look.endOfLine = true;
            look.endDistance = end.distance() - overhang;
        }
        return look;
    }
}
