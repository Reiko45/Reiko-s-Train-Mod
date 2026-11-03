package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.signal.SpeedSignBlock;
import com.reiko.trains.signal.SpeedSignBlockEntity;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.SteamLocomotive;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for speed limit signs. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class SpeedSignGameTests {
    private static final String LINE = "line";
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 WEST = new Vec3(-1, 0, 0);
    /** {@link SpeedSignBlock#LIMITS}' places for the limits the tests use. */
    private static final int KMH_10 = 0;
    private static final int KMH_20 = 1;
    private static final int END = 5;
    /** A little over for rounding. */
    private static final double SLACK = 1.0E-6;

    private SpeedSignGameTests() {
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

    /** A speed sign two blocks south of the track at {@code x}, for trains going east (+1) or west (-1). */
    private static SpeedSignBlockEntity sign(GameTestHelper helper, int x, int side, int limit) {
        return TestTracks.speedSign(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 6)), at(helper, x), side, limit);
    }

    private static double blocksPerTick(int kmh) {
        return kmh / 72.0;
    }

    /** A steam locomotive on the line at {@code x}, facing {@code look}, with a driver aboard at full throttle. */
    private static SteamLocomotive driven(GameTestHelper helper, int x, Vec3 look) {
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, x), look);
        loco.setFuel(16000);
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(loco.position());
        driver.startRiding(loco);
        for (int i = 0; i < 4; i++) {
            loco.control(TrainControlPayload.THROTTLE_UP);
        }
        return loco;
    }

    private static double eastBuffer(SteamLocomotive loco) {
        return loco.getX() + loco.halfLength();
    }

    private static double westBuffer(SteamLocomotive loco) {
        return loco.getX() - loco.halfLength();
    }

    @GameTest(template = LINE, timeoutTicks = 500)
    public static void driversSlowDownForASpeedSign(GameTestHelper helper) {
        line(helper);
        SpeedSignBlockEntity sign = sign(helper, 34, 1, KMH_20);
        SteamLocomotive loco = driven(helper, 4, EAST);
        double signX = sign.node().getX() + 0.5;
        double limit = blocksPerTick(20);
        // [fastest well before the sign, speed passing it, fastest after it, whether it's passed]
        double[] seen = {0, -1, 0, 0};
        helper.onEachTick(() -> {
            double buffer = eastBuffer(loco);
            double speed = Math.abs(loco.trainSpeed());
            if (buffer < signX - 12) {
                seen[0] = Math.max(seen[0], speed);
            } else if (buffer >= signX && seen[1] < 0) {
                seen[1] = speed;
            }
            if (buffer > signX + 0.5) {
                seen[2] = Math.max(seen[2], speed);
                seen[3] = 1;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(eastBuffer(loco) > at(helper, 55).getX(), "the train should get down the line");
            helper.assertTrue(seen[0] > limit + 0.05, "it should go faster than the limit before it gets near the sign: " + seen[0]);
            helper.assertTrue(seen[1] <= limit + 0.01, "it should be down to the limit by the sign: " + seen[1] * 72 + " km/h");
            helper.assertTrue(seen[2] <= limit + SLACK, "it should keep to the limit after the sign: " + seen[2] * 72 + " km/h");
            helper.assertTrue(loco.speedLimitKmh() == 20 && !loco.isSlowingForLimit(), "the gauges should show 20 km/h, not " + loco.speedLimitKmh());
        });
    }

    @GameTest(template = LINE, timeoutTicks = 900)
    public static void timetabledTrainsKeepToTheSpeedLimit(GameTestHelper helper) {
        line(helper);
        StationBlockEntity terminus = TestTracks.station(helper.getLevel(), helper.absolutePos(new BlockPos(58, 1, 7)), at(helper, 58), "Terminus", 5);
        SpeedSignBlockEntity sign = sign(helper, 20, 1, KMH_10);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 6), EAST);
        loco.setFuel(16000);
        loco.autopilot().start(List.of(TestTracks.stop(terminus)));
        double signX = sign.node().getX() + 0.5;
        double limit = blocksPerTick(10);
        double[] fastest = {0};
        helper.onEachTick(() -> {
            if (eastBuffer(loco) > signX + 0.5) {
                fastest[0] = Math.max(fastest[0], Math.abs(loco.trainSpeed()));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(loco.autopilot().isDwelling(), "the train should get to the terminus");
            helper.assertTrue(fastest[0] > limit * 0.8, "it should have run at about the limit: " + fastest[0] * 72 + " km/h");
            helper.assertTrue(fastest[0] <= limit + SLACK, "it should have kept to the 10 km/h limit: " + fastest[0] * 72 + " km/h");
        });
    }

    @GameTest(template = LINE, timeoutTicks = 600)
    public static void anEndOfLimitSignLiftsIt(GameTestHelper helper) {
        line(helper);
        SpeedSignBlockEntity first = sign(helper, 12, 1, KMH_10);
        SpeedSignBlockEntity end = sign(helper, 28, 1, END);
        SteamLocomotive loco = driven(helper, 4, EAST);
        double firstX = first.node().getX() + 0.5;
        double endX = end.node().getX() + 0.5;
        double limit = blocksPerTick(10);
        // [fastest within the limit, fastest after its end]
        double[] fastest = {0, 0};
        helper.onEachTick(() -> {
            double buffer = eastBuffer(loco);
            double speed = Math.abs(loco.trainSpeed());
            if (buffer > firstX + 0.5 && buffer < endX) {
                fastest[0] = Math.max(fastest[0], speed);
            } else if (buffer > endX + 1) {
                fastest[1] = Math.max(fastest[1], speed);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(eastBuffer(loco) > at(helper, 54).getX(), "the train should get down the line");
            helper.assertTrue(fastest[0] <= limit + SLACK, "it should keep to 10 km/h up to the end sign: " + fastest[0] * 72 + " km/h");
            helper.assertTrue(fastest[1] > limit + 0.1, "it should speed up past the end sign: " + fastest[1] * 72 + " km/h");
            helper.assertTrue(loco.speedLimitKmh() == 0, "the gauges should show no limit, not " + loco.speedLimitKmh());
        });
    }

    @GameTest(template = LINE, timeoutTicks = 400)
    public static void speedSignsAreOnlyForTrainsComingTowardsThem(GameTestHelper helper) {
        line(helper);
        SpeedSignBlockEntity sign = sign(helper, 30, 1, KMH_10);
        SteamLocomotive loco = driven(helper, 56, WEST);
        double signX = sign.node().getX() + 0.5;
        double limit = blocksPerTick(10);
        double[] fastest = {0};
        helper.onEachTick(() -> {
            if (westBuffer(loco) < signX - 1) {
                fastest[0] = Math.max(fastest[0], Math.abs(loco.trainSpeed()));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(westBuffer(loco) < at(helper, 10).getX(), "the train should get down the line");
            helper.assertTrue(fastest[0] > limit + 0.1, "going away from the sign's face it shouldn't be held to it: " + fastest[0] * 72 + " km/h");
            helper.assertTrue(loco.speedLimitKmh() == 0, "the gauges should show no limit, not " + loco.speedLimitKmh());
        });
    }

    @GameTest(template = LINE, timeoutTicks = 900)
    public static void aTrainSettingOffBackLeavesTheLimitBehind(GameTestHelper helper) {
        line(helper);
        SpeedSignBlockEntity sign = sign(helper, 16, 1, KMH_10);
        SteamLocomotive loco = driven(helper, 6, EAST);
        double signX = sign.node().getX() + 0.5;
        double limit = blocksPerTick(10);
        // [reversed yet, fastest going back, furthest east]
        double[] seen = {0, 0, 0};
        helper.onEachTick(() -> {
            seen[2] = Math.max(seen[2], loco.getX());
            if (seen[0] == 0 && eastBuffer(loco) > signX + 8) {
                helper.assertTrue(loco.speedLimitKmh() == 10, "past the sign, the gauges should show 10 km/h, not " + loco.speedLimitKmh());
                seen[0] = 1;
                for (int i = 0; i < 6; i++) {
                    loco.control(TrainControlPayload.THROTTLE_DOWN); // to full reverse
                }
            } else if (seen[0] == 1 && loco.getX() < seen[2] - 0.5) {
                seen[1] = Math.max(seen[1], Math.abs(loco.trainSpeed()));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(seen[1] > limit + 0.05, "backing away, it shouldn't be held to the limit: " + seen[1] * 72 + " km/h");
            helper.assertTrue(loco.speedLimitKmh() == 0, "the gauges should show no limit, not " + loco.speedLimitKmh());
        });
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void clickingASpeedSignChangesItsLimit(GameTestHelper helper) {
        line(helper);
        SpeedSignBlockEntity sign = sign(helper, 20, 1, KMH_20);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = sign.getBlockPos();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false);
        for (int kmh : new int[] {30, 40, 50, 0, 10, 20}) {
            helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit);
            helper.assertTrue(sign.limitKmh() == kmh, "the sign should show " + (kmh == 0 ? "the end of the limit" : kmh + " km/h") + ", not " + sign.limitKmh());
        }
        helper.assertTrue(sign.limit() == blocksPerTick(20), "a 20 km/h sign should limit trains to 20 km/h");
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit);
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit);
        helper.assertTrue(sign.limit() == Double.POSITIVE_INFINITY, "an end of limit sign shouldn't limit anything");
        helper.succeed();
    }
}
