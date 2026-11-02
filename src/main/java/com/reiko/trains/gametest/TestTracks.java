package com.reiko.trains.gametest;

import com.reiko.trains.freight.LoaderMode;
import com.reiko.trains.freight.WagonLoaderBlock;
import com.reiko.trains.freight.WagonLoaderBlockEntity;
import com.reiko.trains.item.TrainCarItem;
import com.reiko.trains.registry.ModBlocks;
import com.reiko.trains.signal.LevelCrossingBlock;
import com.reiko.trains.signal.LevelCrossingBlockEntity;
import com.reiko.trains.signal.SignalBlock;
import com.reiko.trains.signal.SignalBlockEntity;
import com.reiko.trains.signal.StationBlock;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.signal.SwitchBlock;
import com.reiko.trains.signal.SwitchBlockEntity;
import com.reiko.trains.signal.TrackSide;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.train.Timetable;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackBuilder;
import com.reiko.trains.train.TrainCarEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    /**
     * A signal at {@code pos} guarding the track block {@code node} for trains leaving it by {@code side} (facing
     * them), as if a player had put it there.
     */
    public static SignalBlockEntity signal(ServerLevel level, BlockPos pos, BlockPos node, int side) {
        TrackAxis axis = TrackGraph.axis(level, node);
        Vec3 out = axis.out(side);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(pos, ModBlocks.SIGNAL.get().defaultBlockState().setValue(SignalBlock.FACING, Direction.getNearest(-out.x, 0, -out.z)), 3);
        SignalBlockEntity signal = (SignalBlockEntity) level.getBlockEntity(pos);
        signal.attach(node, side);
        return signal;
    }

    /** A station at {@code pos} for the track block {@code node}. */
    public static StationBlockEntity station(ServerLevel level, BlockPos pos, BlockPos node, String name, int dwellSeconds) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
        Vec3 toTrack = Vec3.atCenterOf(node).subtract(Vec3.atCenterOf(pos));
        level.setBlock(pos, ModBlocks.STATION.get().defaultBlockState().setValue(StationBlock.FACING, Direction.getNearest(toTrack.x, 0, toTrack.z)), 3);
        StationBlockEntity station = (StationBlockEntity) level.getBlockEntity(pos);
        station.attach(node);
        station.setName(name);
        station.setDwellSeconds(dwellSeconds);
        return station;
    }

    /** A switch stand at {@code pos}, working the nearest junction, as if a player had put it there (normal, unless it's powered). */
    public static SwitchBlockEntity switchStand(ServerLevel level, BlockPos pos) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
        TrackSide.Junction junction = TrackSide.nearestJunction(level, pos);
        if (junction == null) {
            throw new IllegalStateException("No junction near " + pos.toShortString() + " for a switch stand");
        }
        boolean powered = level.hasNeighborSignal(pos);
        level.setBlock(pos, ModBlocks.SWITCH.get().defaultBlockState().setValue(SwitchBlock.FACING, SwitchBlock.facing(junction, pos))
                .setValue(SwitchBlock.POWERED, powered), 3);
        SwitchBlockEntity stand = (SwitchBlockEntity) level.getBlockEntity(pos);
        stand.attach(junction.node(), junction.side());
        stand.powered(powered);
        return stand;
    }

    /** A wagon loader at {@code pos} for the track block {@code node}, as if a player had put it there. */
    public static WagonLoaderBlockEntity wagonLoader(ServerLevel level, BlockPos pos, BlockPos node, LoaderMode mode) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(pos, ModBlocks.WAGON_LOADER.get().defaultBlockState().setValue(WagonLoaderBlock.FACING, WagonLoaderBlock.facing(pos, node, null))
                .setValue(WagonLoaderBlock.MODE, mode), 3);
        WagonLoaderBlockEntity loader = (WagonLoaderBlockEntity) level.getBlockEntity(pos);
        loader.attach(node);
        return loader;
    }

    /**
     * A level crossing at {@code pos} for the nearest track block, its arm coming down towards {@code facing}, as if a
     * player had put it there.
     */
    public static LevelCrossingBlockEntity levelCrossing(ServerLevel level, BlockPos pos, Direction facing, int length) {
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
        BlockPos node = TrackSide.nearestTrack(level, pos);
        if (node == null) {
            throw new IllegalStateException("No track near " + pos.toShortString() + " for a level crossing");
        }
        level.setBlock(pos, ModBlocks.LEVEL_CROSSING.get().defaultBlockState().setValue(LevelCrossingBlock.FACING, facing)
                .setValue(LevelCrossingBlock.LENGTH, length), 3);
        LevelCrossingBlockEntity crossing = (LevelCrossingBlockEntity) level.getBlockEntity(pos);
        crossing.attach(node);
        return crossing;
    }

    /** A timetable stop for a station. */
    public static Timetable.Stop stop(StationBlockEntity station) {
        return new Timetable.Stop(station.getBlockPos(), station.node(), station.name());
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
