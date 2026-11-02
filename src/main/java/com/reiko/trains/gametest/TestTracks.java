package com.reiko.trains.gametest;

import com.reiko.trains.item.TrainCarItem;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackBuilder;
import com.reiko.trains.train.TrainCarEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Lays test track and puts trains on it: used by the game tests and the dev showcase. */
public final class TestTracks {
    private TestTracks() {
    }

    /** A stone floor, {@code width} by {@code depth}, with its top at {@code corner}'s y. */
    public static void floor(ServerLevel level, BlockPos corner, int width, int depth) {
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                level.setBlock(corner.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 2);
            }
        }
    }

    /** A straight line of track blocks. */
    public static void line(ServerLevel level, BlockPos from, TrackAxis axis, int blocks) {
        TrackBuilder.line(level, from, axis, blocks);
    }

    /**
     * Joins the track block at {@code from} to {@code to} the way the track item does, without a player; a new end
     * faces the way that makes a smooth arc. Throws if it can't be built.
     */
    public static TrackBuilder.Plan connect(ServerLevel level, BlockPos from, BlockPos to) {
        TrackBuilder.Plan plan = TrackBuilder.plan(level, from, to, Vec3.ZERO, false);
        if (!plan.ok() || !TrackBuilder.build(level, plan, null)) {
            throw new IllegalStateException("Couldn't lay track from " + from.toShortString() + " to " + to.toShortString() + ": "
                    + (plan.problem() == null ? "building failed" : plan.problem().getString()));
        }
        return plan;
    }

    /**
     * A stadium-shaped loop: straights along x at {@code z} and {@code z + 2r}, from {@code x} to {@code x + length},
     * joined at each end by two quarter circles of radius {@code r}.
     */
    public static void loop(ServerLevel level, BlockPos corner, int length, int r) {
        BlockPos southWest = corner.offset(r, 0, 0);
        line(level, southWest, TrackAxis.X, length + 1);
        line(level, southWest.offset(0, 0, 2 * r), TrackAxis.X, length + 1);
        BlockPos eastEnd = southWest.offset(length, 0, 0);
        connect(level, eastEnd, eastEnd.offset(r, 0, r));
        connect(level, eastEnd.offset(r, 0, r), eastEnd.offset(0, 0, 2 * r));
        BlockPos westEnd = southWest.offset(0, 0, 2 * r);
        connect(level, westEnd, westEnd.offset(-r, 0, -r));
        connect(level, westEnd.offset(-r, 0, -r), southWest);
    }

    /** Puts a car on the track block at {@code pos}, facing {@code look}, and adds it to the world. */
    public static <T extends TrainCarEntity> T car(ServerLevel level, EntityType<T> type, BlockPos pos, Vec3 look) {
        T car = type.create(level);
        TrainCarItem.Problem problem = TrainCarItem.place(level, car, pos, look);
        if (problem != null) {
            throw new IllegalStateException("Couldn't put a " + type.getDescription().getString() + " at " + pos.toShortString() + ": " + problem);
        }
        level.addFreshEntity(car);
        return car;
    }
}
