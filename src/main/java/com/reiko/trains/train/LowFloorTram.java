package com.reiko.trains.train;

import com.reiko.trains.item.TrainCarItem;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.track.TrackPos;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The low-floor tram: three sections that bend in the middle, a driving section at each end (noses out) and a short
 * middle section between, always together. One item puts the three on the track, coupled; breaking any of them takes
 * the whole tram up again, as one item.
 */
public final class LowFloorTram {
    private LowFloorTram() {
    }

    /** Whether a car is a section of a low-floor tram. */
    public static boolean isSection(Entity car) {
        return car instanceof TramCab || car instanceof TramMiddle;
    }

    /**
     * The low-floor tram a section is part of: from one driving section, through the middle, to the other. Just the
     * section itself if it isn't in a whole one.
     */
    public static List<TrainCarEntity> sections(TrainCarEntity car) {
        List<TrainCarEntity> cars = Trains.train(car);
        int i = cars.indexOf(car);
        List<TrainCarEntity> set = new ArrayList<>();
        if (i < 0) {
            set.add(car);
            return set;
        }
        // Out to the driving section at each end of it: from a driving section, only inwards (through middles).
        int from = i;
        int to = i;
        if (car instanceof TramCab) {
            if (i + 1 < cars.size() && cars.get(i + 1) instanceof TramMiddle) {
                to = walk(cars, i, 1);
            } else if (i > 0 && cars.get(i - 1) instanceof TramMiddle) {
                from = walk(cars, i, -1);
            }
        } else {
            from = walk(cars, i, -1);
            to = walk(cars, i, 1);
        }
        set.addAll(cars.subList(from, to + 1));
        return set;
    }

    /** From car {@code i}, along through the middle sections to the driving section beyond them (or the last middle). */
    private static int walk(List<TrainCarEntity> cars, int i, int step) {
        int j = i + step;
        while (j >= 0 && j < cars.size() && cars.get(j) instanceof TramMiddle) {
            j += step;
        }
        return j >= 0 && j < cars.size() && cars.get(j) instanceof TramCab ? j : j - step;
    }

    /**
     * Puts a low-floor tram on the track: its middle section centred on the track block at {@code pos}, a driving section
     * either side of it, the front one facing the way along the track nearest {@code look}, and couples them. Adds them
     * to the world. Returns why it couldn't, or null if it did.
     */
    @Nullable
    public static TrainCarItem.Problem place(ServerLevel level, BlockPos pos, Vec3 look) {
        TramCab front = ModEntities.TRAM_CAB.get().create(level);
        TramMiddle middle = ModEntities.TRAM_MIDDLE.get().create(level);
        TramCab back = ModEntities.TRAM_CAB.get().create(level);
        if (front == null || middle == null || back == null) {
            return TrainCarItem.Problem.NOT_TRACK;
        }
        TrackPos centre = TrackPos.at(level, pos, look);
        if (centre == null) {
            return TrainCarItem.Problem.NO_ROOM;
        }
        double apart = middle.halfLength() + Trains.BUFFER_GAP + front.halfLength();
        if (!put(level, middle, centre, false) || !put(level, front, along(level, centre, apart), false)
                || !put(level, back, along(level, centre, -apart), true)) {
            return TrainCarItem.Problem.NO_ROOM;
        }
        for (TrainCarEntity car : List.of(front, middle, back)) {
            if (!Trains.clear(level, car)) {
                return TrainCarItem.Problem.BLOCKED;
            }
        }
        level.addFreshEntity(front);
        level.addFreshEntity(middle);
        level.addFreshEntity(back);
        Trains.couple(front, middle);
        Trains.couple(middle, back);
        return null;
    }

    @Nullable
    private static TrackPos along(ServerLevel level, TrackPos from, double distance) {
        TrackPos pos = from.copy();
        return Math.abs(pos.walk(level, distance, TrackPos.STRAIGHTEST)) < Math.abs(distance) - 1.0E-6 ? null : pos;
    }

    /** Sets a car's bogies either side of {@code centre} (facing the other way, if {@code turned}). */
    private static boolean put(ServerLevel level, TrainCarEntity car, @Nullable TrackPos centre, boolean turned) {
        if (centre == null) {
            return false;
        }
        TrackPos facing = turned ? centre.turned() : centre;
        TrackPos front = facing.copy();
        TrackPos rear = facing.copy();
        double offset = car.bogieOffset();
        if (front.walk(level, offset, TrackPos.STRAIGHTEST) < offset - 1.0E-6 || rear.walk(level, -offset, TrackPos.STRAIGHTEST) < offset - 1.0E-6) {
            return false;
        }
        car.setBogies(front, rear);
        return true;
    }

    /** One section's gone (broken up, or off the track): the rest of its tram goes with it, without dropping anything. */
    static void removeOthers(TrainCarEntity car, List<TrainCarEntity> set) {
        for (TrainCarEntity other : set) {
            if (other != car && !other.isRemoved() && other instanceof Section section) {
                section.markBreakingUp();
                other.discard();
            }
        }
    }

    /** What both kinds of section do when one of them is taken away. */
    interface Section {
        void markBreakingUp();
    }
}
