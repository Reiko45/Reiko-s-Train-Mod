package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.signal.SignalBlock;
import com.reiko.trains.signal.SignalBlockEntity;
import com.reiko.trains.signal.StationBlock;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.item.TimetableItem;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackBuilder;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.PassengerCoach;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for signals, stations and timetables. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class SignalGameTests {
    private static final String LINE = "line";
    private static final String YARD = "yard";
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 WEST = new Vec3(-1, 0, 0);

    private SignalGameTests() {
    }

    /** Track along x at z = 4 (y = 1), the whole 64 blocks. */
    private static void line(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
        TestTracks.line(level, at(helper, 0), TrackAxis.X, 64);
    }

    private static BlockPos at(GameTestHelper helper, int x) {
        return helper.absolutePos(new BlockPos(x, 1, 4));
    }

    /** A signal two blocks south of the track at {@code x}, guarding the way east (+1) or west (-1). */
    private static SignalBlockEntity signal(GameTestHelper helper, int x, int side) {
        return TestTracks.signal(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 6)), at(helper, x), side);
    }

    private static StationBlockEntity station(GameTestHelper helper, int x, String name, int dwell) {
        return TestTracks.station(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 7)), at(helper, x), name, dwell);
    }

    private static boolean clear(SignalBlockEntity signal) {
        return signal.getBlockState().getValue(SignalBlock.CLEAR);
    }

    private static Player board(GameTestHelper helper, SteamLocomotive loco) {
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(loco.position());
        driver.startRiding(loco);
        return driver;
    }

    /** Where the front of a train going east is: the locomotive's front buffer. */
    private static double eastBuffer(TrainCarEntity car) {
        return car.getX() + car.halfLength();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Signals

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void signalShowsWhetherTheLineAheadIsClear(GameTestHelper helper) {
        line(helper);
        SignalBlockEntity first = signal(helper, 20, 1);
        SignalBlockEntity second = signal(helper, 40, 1);
        PassengerCoach behind = TestTracks.car(helper.getLevel(), ModEntities.PASSENGER_COACH.get(), at(helper, 10), EAST);
        PassengerCoach between = TestTracks.car(helper.getLevel(), ModEntities.PASSENGER_COACH.get(), at(helper, 30), EAST);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!clear(first), "the first signal should be red: there's a coach between it and the next");
            helper.assertTrue(helper.getLevel().getBlockState(first.getBlockPos()).getAnalogOutputSignal(helper.getLevel(), first.getBlockPos()) == 15,
                    "a comparator should read 15 off it");
            helper.assertTrue(clear(second), "the second signal should be green: nothing's beyond it");
            between.discard();
        });
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(clear(first), "with the coach gone, the first signal should be green (the coach behind it doesn't count)");
            behind.discard();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void redstoneHoldsASignalAtRed(GameTestHelper helper) {
        line(helper);
        SignalBlockEntity signal = signal(helper, 20, 1);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(clear(signal), "should start green");
            helper.getLevel().setBlock(signal.getBlockPos().east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        });
        helper.runAfterDelay(16, () -> {
            helper.assertTrue(!clear(signal), "powered, it should be red");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 400)
    public static void driversStopAtRedSignals(GameTestHelper helper) {
        line(helper);
        SignalBlockEntity signal = signal(helper, 35, 1);
        FreightWagon blocking = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 50), EAST);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 6), EAST);
        Player driver = board(helper, loco);
        loco.setFuel(16000);
        for (int i = 0; i < 4; i++) {
            loco.control(TrainControlPayload.THROTTLE_UP);
        }
        double signalX = signal.node().getX() + 0.5;
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(Math.abs(loco.trainSpeed()) < 1.0E-6, "the train should have stopped at the red signal");
            double buffer = eastBuffer(loco);
            helper.assertTrue(buffer < signalX && buffer > signalX - 3, "it should have stopped just short of the signal, " + (signalX - buffer) + " short");
            helper.assertTrue(loco.signalAspect() == 2, "the gauges should show the red signal");
            blocking.discard();
        });
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(eastBuffer(loco) > signalX + 5, "with the line clear it should have gone on");
            driver.stopRiding();
            helper.succeed();
        });
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Stations and timetables

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void stationGivesARedstoneSignalWhileATrainStands(GameTestHelper helper) {
        line(helper);
        StationBlockEntity station = station(helper, 30, "Halt", 5);
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(!station.getBlockState().getValue(StationBlock.POWERED), "no train yet");
            TestTracks.car(helper.getLevel(), ModEntities.PASSENGER_COACH.get(), at(helper, 32), EAST);
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(station.getBlockState().getValue(StationBlock.POWERED), "a coach standing at the station should power it");
            helper.succeed();
        });
    }

    @GameTest(template = LINE)
    public static void timetableCollectsStops(GameTestHelper helper) {
        line(helper);
        StationBlockEntity a = station(helper, 10, "Ashby", 5);
        StationBlockEntity b = station(helper, 50, "Brook", 5);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack timetable = new ItemStack(ModItems.TIMETABLE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, timetable);
        for (StationBlockEntity station : List.of(a, b, b)) {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(station.getBlockPos()), Direction.UP, station.getBlockPos(), false);
            timetable.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        }
        List<String> names = TimetableItem.stops(timetable).stream().map(stop -> stop.name()).toList();
        helper.assertTrue(names.equals(List.of("Ashby", "Brook")), "the stops should be Ashby then Brook (not Brook twice): " + names);
        helper.succeed();
    }

    @GameTest(template = LINE, timeoutTicks = 1200)
    public static void timetabledTrainRunsBackAndForth(GameTestHelper helper) {
        line(helper);
        StationBlockEntity west = station(helper, 10, "West End", 2);
        StationBlockEntity east = station(helper, 54, "East End", 2);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 24), EAST);
        PassengerCoach coach = TestTracks.car(helper.getLevel(), ModEntities.PASSENGER_COACH.get(), at(helper, 17), EAST);
        Trains.couple(loco, coach);
        loco.setFuel(16000);
        loco.autopilot().start(List.of(TestTracks.stop(east), TestTracks.stop(west)));
        boolean[] seen = new boolean[3];
        double eastX = east.node().getX() + 0.5;
        double westX = west.node().getX() + 0.5;
        helper.onEachTick(() -> {
            if (!loco.autopilot().isDwelling()) {
                return;
            }
            double front = loco.getX() + loco.halfLength();
            double back = coach.getX() - coach.halfLength();
            if (!seen[0] && Math.abs(front - eastX) < 0.7) {
                seen[0] = true; // drew up at East End, front level with the sign
            } else if (seen[0] && !seen[1] && Math.abs(back - westX) < 0.7) {
                seen[1] = true; // and back to West End, reversing, the coach in front
            }
            if (seen[0] && east.getBlockState().getValue(StationBlock.POWERED)) {
                seen[2] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(seen[0], "the train should have stopped at East End");
            helper.assertTrue(seen[2], "East End should have given a redstone signal while the train stood");
            helper.assertTrue(seen[1], "the train should have gone back to West End");
        });
    }

    @GameTest(template = LINE, timeoutTicks = 1000)
    public static void timetabledTrainWaitsForTheSignal(GameTestHelper helper) {
        line(helper);
        StationBlockEntity terminus = station(helper, 58, "Terminus", 5);
        SignalBlockEntity signal = signal(helper, 30, 1);
        FreightWagon blocking = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 40), EAST);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 6), EAST);
        loco.setFuel(16000);
        loco.autopilot().start(List.of(TestTracks.stop(terminus)));
        double signalX = signal.node().getX() + 0.5;
        helper.runAfterDelay(250, () -> {
            helper.assertTrue(Math.abs(loco.trainSpeed()) < 1.0E-6, "the train should be waiting at the red signal");
            helper.assertTrue(eastBuffer(loco) < signalX, "it should be short of the signal");
            blocking.discard();
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(loco.autopilot().isDwelling(), "the train should have gone on to the terminus");
            helper.assertTrue(Math.abs(eastBuffer(loco) - (terminus.node().getX() + 0.5)) < 0.7, "and stopped level with the sign");
        });
    }

    @GameTest(template = YARD, timeoutTicks = 800)
    public static void timetabledTrainTakesTheBranchForItsStation(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        BlockPos west = helper.absolutePos(new BlockPos(2, 1, 10));
        TestTracks.line(level, west, TrackAxis.X, 40);
        BlockPos branchStart = west.offset(12, 0, 0);
        TrackBuilder.Plan branch = TestTracks.connect(level, branchStart, branchStart.offset(12, 0, 5));
        BlockPos diagonal = branch.end().offset(1, 0, 1);
        TestTracks.line(level, diagonal, TrackAxis.SE, 14);
        BlockPos stationTrack = diagonal.offset(10, 0, 10);
        StationBlockEntity station = TestTracks.station(level, stationTrack.offset(2, 0, -2), stationTrack, "Branch", 5);
        SteamLocomotive loco = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), west.offset(5, 0, 0), EAST);
        loco.setFuel(16000);
        loco.autopilot().start(List.of(TestTracks.stop(station)));
        helper.succeedWhen(() -> {
            helper.assertTrue(loco.autopilot().isDwelling(), "the train should have reached the branch station");
            helper.assertTrue(loco.getZ() > west.getZ() + 8, "it should be down the branch");
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void drivingTakesOverFromTheTimetable(GameTestHelper helper) {
        line(helper);
        StationBlockEntity station = station(helper, 50, "Far", 5);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 10), EAST);
        loco.setFuel(16000);
        loco.autopilot().start(List.of(TestTracks.stop(station)));
        Player driver = board(helper, loco);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(loco.autopilot().isOn(), "a driver getting in shouldn't stop the timetable");
            loco.control(TrainControlPayload.BRAKE_ON);
            helper.assertTrue(!loco.autopilot().isOn(), "braking should take over from it");
            driver.stopRiding();
            helper.succeed();
        });
    }

    /**
     * Two trains on a timetable round the same loop, with signals all facing the same way: they must both go round
     * that way, and never meet.
     */
    @GameTest(template = YARD, timeoutTicks = 2000)
    public static void signalsKeepTwoTrainsApartOnALoop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        BlockPos corner = helper.absolutePos(new BlockPos(2, 1, 4));
        int length = 24;
        int r = 10;
        TestTracks.loop(level, corner, length, r);
        // The loop's straights: south one (z = corner) runs west to east, x from corner + r to + r + length.
        BlockPos south = corner.offset(r, 0, 0);
        BlockPos north = south.offset(0, 0, 2 * r);
        // Signals for trains going clockwise (east along the south straight, west along the north one), three a side.
        for (int i : new int[]{2, 12, 22}) {
            TestTracks.signal(level, south.offset(i, 0, -2), south.offset(i, 0, 0), 1);
            TestTracks.signal(level, north.offset(length - i, 0, 2), north.offset(length - i, 0, 0), -1);
        }
        StationBlockEntity s1 = TestTracks.station(level, south.offset(18, 0, -3), south.offset(18, 0, 0), "South", 3);
        StationBlockEntity s2 = TestTracks.station(level, north.offset(length - 18, 0, 3), north.offset(length - 18, 0, 0), "North", 3);
        SteamLocomotive loco1 = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), south.offset(8, 0, 0), EAST);
        PassengerCoach coach1 = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), south.offset(1, 0, 0), EAST);
        SteamLocomotive loco2 = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), north.offset(length - 8, 0, 0), WEST);
        PassengerCoach coach2 = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), north.offset(length - 1, 0, 0), WEST);
        helper.assertTrue(Trains.couple(loco1, coach1) == Trains.CoupleResult.COUPLED, "train 1 should couple");
        helper.assertTrue(Trains.couple(loco2, coach2) == Trains.CoupleResult.COUPLED, "train 2 should couple");
        for (SteamLocomotive loco : List.of(loco1, loco2)) {
            loco.setFuel(16000);
            // Both trains go round to the far station first, so they both have to pass the other's.
            loco.autopilot().start(loco == loco1 ? List.of(TestTracks.stop(s2), TestTracks.stop(s1))
                    : List.of(TestTracks.stop(s1), TestTracks.stop(s2)));
        }
        double[] travelled = {0, 0};
        Vec3[] last = {loco1.position(), loco2.position()};
        double[] closest = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            travelled[0] += loco1.position().distanceTo(last[0]);
            travelled[1] += loco2.position().distanceTo(last[1]);
            last[0] = loco1.position();
            last[1] = loco2.position();
            for (TrainCarEntity a : List.<TrainCarEntity>of(loco1, coach1)) {
                for (TrainCarEntity b : List.<TrainCarEntity>of(loco2, coach2)) {
                    double gap = a.distanceTo(b) - a.halfLength() - b.halfLength();
                    closest[0] = Math.min(closest[0], gap);
                    helper.assertTrue(gap > -1.0, "the two trains ran into each other");
                }
            }
        });
        helper.runAfterDelay(1800, () -> {
            ReikoTrains.LOGGER.info("Loop signals test: the trains went {} and {} blocks, and came within {} blocks of each other",
                    String.format("%.0f", travelled[0]), String.format("%.0f", travelled[1]), String.format("%.1f", closest[0]));
            helper.assertTrue(travelled[0] > 150 && travelled[1] > 150, "both trains should have gone round, went " + travelled[0] + " and " + travelled[1]);
            helper.succeed();
        });
    }
}
