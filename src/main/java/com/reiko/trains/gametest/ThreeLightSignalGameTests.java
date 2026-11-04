package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.signal.SignalBlock;
import com.reiko.trains.signal.SignalBlockEntity;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.signal.SwitchBlockEntity;
import com.reiko.trains.signal.ThreeLightSignalBlock;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackBuilder;
import com.reiko.trains.train.ExpressLocomotive;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.Locomotive;
import com.reiko.trains.train.Tender;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for three-light signals and caution speed. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class ThreeLightSignalGameTests {
    private static final String LINE = "line";
    private static final String LONG_LINE = "long_line";
    private static final String YARD = "yard";
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private ThreeLightSignalGameTests() {
    }

    /** Track along x at z = 4 (y = 1), {@code length} blocks. */
    private static void line(GameTestHelper helper, int length) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), length, 9);
        TestTracks.line(level, at(helper, 0), TrackAxis.X, length);
    }

    private static BlockPos at(GameTestHelper helper, int x) {
        return helper.absolutePos(new BlockPos(x, 1, 4));
    }

    /** A three-light signal two blocks south of the track at {@code x}, guarding the way east. */
    private static SignalBlockEntity threeLight(GameTestHelper helper, int x) {
        return TestTracks.threeLightSignal(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 6)), at(helper, x), 1);
    }

    /** "red", "yellow" or "green". */
    private static String aspect(SignalBlockEntity signal) {
        BlockState state = signal.getBlockState();
        if (!state.getValue(SignalBlock.CLEAR)) {
            return "red";
        }
        return state.hasProperty(ThreeLightSignalBlock.CAUTION) && state.getValue(ThreeLightSignalBlock.CAUTION) ? "yellow" : "green";
    }

    private static int comparator(GameTestHelper helper, SignalBlockEntity signal) {
        BlockPos pos = signal.getBlockPos();
        return helper.getLevel().getBlockState(pos).getAnalogOutputSignal(helper.getLevel(), pos);
    }

    private static Player board(GameTestHelper helper, Locomotive loco) {
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(loco.position());
        driver.startRiding(loco);
        return driver;
    }

    private static ExpressLocomotive express(GameTestHelper helper, int x) {
        ServerLevel level = helper.getLevel();
        ExpressLocomotive express = TestTracks.car(level, ModEntities.EXPRESS_LOCOMOTIVE.get(), at(helper, x), EAST);
        Tender tender = TestTracks.car(level, ModEntities.TENDER.get(), at(helper, x - 8), EAST);
        helper.assertTrue(Trains.couple(express, tender) == Trains.CoupleResult.COUPLED, "the tender should couple");
        tender.setFuel(Tender.FUEL_CAPACITY);
        return express;
    }

    private static double eastBuffer(TrainCarEntity car) {
        return car.getX() + car.halfLength();
    }

    private static double caution() {
        return TrainsConfig.CAUTION_SPEED.get() / 72.0;
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void itShowsYellowBeforeARedSignal(GameTestHelper helper) {
        line(helper, 64);
        SignalBlockEntity first = threeLight(helper, 10);
        SignalBlockEntity second = threeLight(helper, 30);
        SignalBlockEntity third = threeLight(helper, 50);
        FreightWagon wagon = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 40), EAST);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(aspect(second).equals("red"), "with a wagon beyond it, the second should be red: " + aspect(second));
            helper.assertTrue(aspect(first).equals("yellow"), "and the one before it yellow: " + aspect(first));
            helper.assertTrue(aspect(third).equals("green"), "and the one after it green: " + aspect(third));
            helper.assertTrue(comparator(helper, second) == 15 && comparator(helper, first) == 7 && comparator(helper, third) == 0,
                    "comparators should read 15 for red with a train, 7 for yellow, 0 for green");
            wagon.discard();
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(aspect(first).equals("green") && aspect(second).equals("green"), "with the wagon gone, both should be green");
            // Held at red by redstone, the one before it shows yellow too.
            helper.getLevel().setBlockAndUpdate(second.getBlockPos().east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(aspect(second).equals("red") && aspect(first).equals("yellow"),
                    "held at red, the one before should be yellow: " + aspect(second) + ", " + aspect(first));
            helper.succeed();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 120)
    public static void itLooksTheWayTheSwitchIsSet(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        BlockPos west = helper.absolutePos(new BlockPos(2, 1, 10));
        TestTracks.line(level, west, TrackAxis.X, 40);
        BlockPos junction = west.offset(12, 0, 0);
        TrackBuilder.Plan branch = TestTracks.connect(level, junction, junction.offset(12, 0, 5));
        BlockPos branchLine = branch.end().offset(1, 0, 1);
        TestTracks.line(level, branchLine, TrackAxis.SE, 14);
        SignalBlockEntity approach = TestTracks.threeLightSignal(level, west.offset(4, 0, -2), west.offset(4, 0, 0), 1);
        // The main line's next signal is red (a wagon beyond it); the branch's is green.
        SignalBlockEntity main = TestTracks.signal(level, west.offset(28, 0, -2), west.offset(28, 0, 0), 1);
        BlockPos branchNode = branchLine.offset(3, 0, 3);
        SignalBlockEntity side = TestTracks.signal(level, branchNode.offset(1, 0, -1), branchNode, 1);
        TestTracks.car(level, ModEntities.FREIGHT_WAGON.get(), west.offset(34, 0, 0), EAST);
        SwitchBlockEntity stand = TestTracks.switchStand(level, junction.offset(0, 0, -2));
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(aspect(main).equals("red") && aspect(side).equals("green"), "the main line's signal red, the branch's green");
            helper.assertTrue(aspect(approach).equals("yellow"), "set for the main line, it should be yellow: " + aspect(approach));
            stand.setThrown(true);
        });
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(aspect(approach).equals("green"), "set for the branch, it should be green: " + aspect(approach));
            helper.succeed();
        });
    }

    @GameTest(template = LONG_LINE, timeoutTicks = 1100)
    public static void driversKeepToCautionSpeedPastAYellow(GameTestHelper helper) {
        line(helper, 256);
        SignalBlockEntity yellow = threeLight(helper, 120);
        SignalBlockEntity red = TestTracks.signal(helper.getLevel(), helper.absolutePos(new BlockPos(200, 1, 6)), at(helper, 200), 1);
        FreightWagon wagon = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 225), EAST);
        ExpressLocomotive express = express(helper, 20);
        Player driver = board(helper, express);
        for (int i = 0; i < Locomotive.MAX_THROTTLE; i++) {
            express.control(TrainControlPayload.THROTTLE_UP);
        }
        double yellowX = yellow.node().getX() + 0.5;
        double redX = red.node().getX() + 0.5;
        // [fastest before the yellow, fastest between the signals, fastest after the red cleared, ticks stopped at it]
        double[] seen = {0, 0, 0, 0};
        boolean[] sawYellow = {false};
        helper.onEachTick(() -> {
            double speed = Math.abs(express.trainSpeed());
            double front = eastBuffer(express);
            if (front < yellowX - 30) {
                seen[0] = Math.max(seen[0], speed);
            }
            if (express.signalAspect() == 3) {
                sawYellow[0] = true;
            }
            if (front > yellowX + 1 && front < redX && !wagon.isRemoved()) {
                seen[1] = Math.max(seen[1], speed);
                if (speed < 1.0E-6) {
                    if (++seen[3] == 40) {
                        helper.assertTrue(redX - front < 3, "it should have stopped just short of the red signal: " + (redX - front));
                        helper.assertTrue(express.speedLimitKmh() == TrainsConfig.CAUTION_SPEED.get(), "showing caution speed as its limit");
                        wagon.discard();
                    }
                }
            }
            if (wagon.isRemoved()) {
                seen[2] = Math.max(seen[2], speed);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(sawYellow[0], "the gauges should have shown the yellow signal");
            helper.assertTrue(seen[0] > caution() + 0.05, "it should have been going faster than caution speed before: " + seen[0] * 72);
            helper.assertTrue(seen[1] <= caution() + 0.002, "past the yellow, no faster than caution speed: " + seen[1] * 72);
            helper.assertTrue(seen[3] >= 40, "it should have stopped at the red signal");
            helper.assertTrue(seen[2] > caution() + 0.03, "once it cleared, it should have speeded up again: " + seen[2] * 72);
            driver.stopRiding();
        });
    }

    @GameTest(template = LONG_LINE, timeoutTicks = 1100)
    public static void timetabledTrainsKeepToCautionSpeedToo(GameTestHelper helper) {
        line(helper, 256);
        SignalBlockEntity yellow = threeLight(helper, 120);
        SignalBlockEntity red = TestTracks.signal(helper.getLevel(), helper.absolutePos(new BlockPos(200, 1, 6)), at(helper, 200), 1);
        FreightWagon wagon = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 236), EAST);
        StationBlockEntity station = TestTracks.station(helper.getLevel(), helper.absolutePos(new BlockPos(225, 1, 7)), at(helper, 225), "Junction", 2);
        ExpressLocomotive express = express(helper, 20);
        express.autopilot().start(List.of(TestTracks.stop(station)));
        double yellowX = yellow.node().getX() + 0.5;
        double redX = red.node().getX() + 0.5;
        double[] seen = {0, 0, 0};
        helper.onEachTick(() -> {
            double speed = Math.abs(express.trainSpeed());
            double front = eastBuffer(express);
            if (front < yellowX - 30) {
                seen[0] = Math.max(seen[0], speed);
            }
            if (front > yellowX + 1 && front < redX && !wagon.isRemoved()) {
                seen[1] = Math.max(seen[1], speed);
                if (speed < 1.0E-6 && ++seen[2] == 40) {
                    wagon.discard();
                }
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(seen[0] > caution() + 0.05, "it should have been going faster than caution speed before: " + seen[0] * 72);
            helper.assertTrue(seen[1] <= caution() + 0.002, "past the yellow, no faster than caution speed: " + seen[1] * 72);
            helper.assertTrue(seen[2] >= 40, "it should have waited at the red signal");
            helper.assertTrue(express.status().getString().contains("Junction") && Math.abs(eastBuffer(express) - (at(helper, 225).getX() + 0.5)) < 0.7,
                    "then gone on to its station: " + express.status().getString());
            express.autopilot().stop();
        });
    }
}
