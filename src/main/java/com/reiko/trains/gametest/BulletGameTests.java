package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.power.ChargingPointBlockEntity;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.signal.SignalBlockEntity;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.BulletCoach;
import com.reiko.trains.train.BulletTrain;
import com.reiko.trains.train.Locomotive;
import com.reiko.trains.train.Trains;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for the Bullet Train: its two driving cars and the coaches between them. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class BulletGameTests {
    private static final String LINE = "line";
    /** 256 blocks of line, to get up to speed. */
    private static final String LONG_LINE = "long_line";
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 WEST = new Vec3(-1, 0, 0);

    private BulletGameTests() {
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

    /** A Bullet Train set: a driving car at each end, noses out, a coach between; the front car's nose at {@code x} + 4.5, facing east. */
    private record Set(BulletTrain front, BulletCoach coach, BulletTrain back) {
    }

    private static Set set(GameTestHelper helper, int x) {
        ServerLevel level = helper.getLevel();
        BulletTrain front = TestTracks.car(level, ModEntities.BULLET_TRAIN.get(), at(helper, x), EAST);
        BulletCoach coach = TestTracks.car(level, ModEntities.BULLET_COACH.get(), at(helper, x - 9), EAST);
        BulletTrain back = TestTracks.car(level, ModEntities.BULLET_TRAIN.get(), at(helper, x - 18), WEST);
        helper.assertTrue(Trains.couple(front, coach) == Trains.CoupleResult.COUPLED, "the coach should couple to the front car");
        helper.assertTrue(Trains.couple(coach, back) == Trains.CoupleResult.COUPLED, "the back car should couple to the coach");
        front.setFuel(BulletTrain.BATTERY_CAPACITY);
        back.setFuel(BulletTrain.BATTERY_CAPACITY);
        return new Set(front, coach, back);
    }

    private static Player board(GameTestHelper helper, Locomotive loco) {
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(loco.position());
        driver.startRiding(loco);
        return driver;
    }

    private static void fullThrottle(Locomotive loco) {
        for (int i = 0; i < 4; i++) {
            loco.control(TrainControlPayload.THROTTLE_UP);
        }
    }

    @GameTest(template = LONG_LINE, timeoutTicks = 600)
    public static void aBulletTrainRunsAt100KmH(GameTestHelper helper) {
        line(helper, 256);
        Set set = set(helper, 40);
        board(helper, set.front());
        fullThrottle(set.front());
        double top = set.front().maxSpeed();
        helper.assertTrue(Math.abs(top * 72 - 100) < 1, "its top speed should be about 100 km/h, not " + top * 72);
        double[] fastest = {0};
        helper.onEachTick(() -> fastest[0] = Math.max(fastest[0], Math.abs(set.front().trainSpeed())));
        helper.succeedWhen(() -> {
            helper.assertTrue(fastest[0] >= top - 0.02, "it should get up to top speed: " + fastest[0] * 72 + " km/h");
            helper.assertTrue(fastest[0] <= top + 1.0E-6, "and no faster: " + fastest[0] * 72 + " km/h");
            helper.assertTrue(set.back().helping() == set.front(), "the back car should be pulling too");
            helper.assertTrue(set.front().fuel() < BulletTrain.BATTERY_CAPACITY && set.back().fuel() < BulletTrain.BATTERY_CAPACITY,
                    "both cars should be using their batteries");
        });
    }

    @GameTest(template = LONG_LINE, timeoutTicks = 800)
    public static void aBulletTrainBrakesInTimeForARedSignal(GameTestHelper helper) {
        line(helper, 256);
        SignalBlockEntity signal = TestTracks.signal(helper.getLevel(), helper.absolutePos(new BlockPos(200, 1, 6)), at(helper, 200), 1);
        TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 230), EAST);
        Set set = set(helper, 40);
        board(helper, set.front());
        fullThrottle(set.front());
        double signalX = signal.node().getX() + 0.5;
        // [fastest, hardest it slowed in one tick, speed last tick]
        double[] seen = {0, 0, 0};
        helper.onEachTick(() -> {
            double speed = Math.abs(set.front().trainSpeed());
            seen[0] = Math.max(seen[0], speed);
            seen[1] = Math.max(seen[1], seen[2] - speed);
            seen[2] = speed;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(seen[0] > 0.9, "it should get going fast: " + seen[0] * 72 + " km/h");
            helper.assertTrue(seen[2] < 1.0E-6, "it should stop for the red signal");
            double buffer = set.front().getX() + set.front().halfLength();
            helper.assertTrue(buffer < signalX && buffer > signalX - 3, "just short of it: " + (signalX - buffer) + " short");
            helper.assertTrue(seen[1] < 0.03, "braking steadily, seeing the signal in time, not all at once: " + seen[1]);
        });
    }

    @GameTest(template = LONG_LINE, timeoutTicks = 1600)
    public static void aTimetabledBulletTrainRunsBackWithoutTurning(GameTestHelper helper) {
        line(helper, 256);
        StationBlockEntity west = TestTracks.station(helper.getLevel(), helper.absolutePos(new BlockPos(60, 1, 7)), at(helper, 60), "West", 2);
        StationBlockEntity east = TestTracks.station(helper.getLevel(), helper.absolutePos(new BlockPos(220, 1, 7)), at(helper, 220), "East", 2);
        Set set = set(helper, 110);
        set.front().autopilot().start(List.of(TestTracks.stop(east), TestTracks.stop(west)));
        double eastX = east.node().getX() + 0.5;
        double westX = west.node().getX() + 0.5;
        boolean[] seen = new boolean[2];
        helper.onEachTick(() -> {
            if (!set.front().autopilot().isDwelling()) {
                return;
            }
            double frontNose = set.front().getX() + set.front().halfLength();
            double backNose = set.back().getX() - set.back().halfLength();
            if (!seen[0] && Math.abs(frontNose - eastX) < 0.7) {
                seen[0] = true; // at East, the front car's nose by the nameboard
            } else if (seen[0] && Math.abs(backNose - westX) < 0.7) {
                seen[1] = true; // and back at West, the other car's nose leading
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(seen[0], "the train should have drawn up at East");
            helper.assertTrue(seen[1], "the train should have come back to West, the back car leading");
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void aBulletTrainCanBeDrivenFromEitherEnd(GameTestHelper helper) {
        line(helper, 64);
        Set set = set(helper, 40);
        board(helper, set.back());
        set.back().control(TrainControlPayload.THROTTLE_UP);
        set.back().control(TrainControlPayload.THROTTLE_UP);
        double startX = set.front().getX();
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(set.front().getX() < startX - 2, "driven from the back car, the train should go its way (west)");
            helper.assertTrue(set.front().helping() == set.back(), "the front car should be pulling for it");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void bothDrivingCarsChargeAtAChargingPoint(GameTestHelper helper) {
        line(helper, 64);
        Set set = set(helper, 40);
        set.front().setFuel(0);
        set.back().setFuel(0);
        ChargingPointBlockEntity charger = TestTracks.chargingPoint(helper.getLevel(), helper.absolutePos(new BlockPos(31, 1, 6)), at(helper, 31));
        charger.addMj(3_000_000);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(set.front().fuel() > 0 && set.back().fuel() > 0, "both driving cars should be charging: " + set.front().fuel()
                    + ", " + set.back().fuel());
            helper.assertTrue(set.front().batteryCapacity() == 1_500_000, "a driving car's batteries should hold 1,500,000 MJ");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 40)
    public static void bulletCarsHaveSeats(GameTestHelper helper) {
        line(helper, 64);
        Set set = set(helper, 40);
        helper.assertTrue(set.front().seats().length == 9, "a driving car should seat the driver and eight more");
        helper.assertTrue(set.coach().seats().length == 12, "a coach should seat twelve");
        helper.succeed();
    }
}
