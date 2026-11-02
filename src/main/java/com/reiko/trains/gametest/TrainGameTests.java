package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.item.TrainCarItem;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.track.EdgeRef;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackBlockEntity;
import com.reiko.trains.track.TrackBuilder;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.track.TrackPos;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Game tests: ./gradlew runGameTestServer. They lay track (straight lines, a loop with curves, a junction, a ramp)
 * and run trains on it.
 */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class TrainGameTests {
    /** A 64-block-long strip: track along x at z = 4. */
    private static final String LINE = "line";
    /** A 48 by 48 yard. */
    private static final String YARD = "yard";
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 WEST = new Vec3(-1, 0, 0);

    private TrainGameTests() {
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Helpers

    /** A straight line of track along x at z = 4, y = 1, from x = 0, {@code blocks} long. */
    private static void straight(GameTestHelper helper, int blocks) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
        TestTracks.line(level, helper.absolutePos(new BlockPos(0, 1, 4)), TrackAxis.X, blocks);
    }

    private static BlockPos onLine(GameTestHelper helper, int x) {
        return helper.absolutePos(new BlockPos(x, 1, 4));
    }

    private static <T extends TrainCarEntity> T car(GameTestHelper helper, net.minecraft.world.entity.EntityType<T> type, BlockPos at, Vec3 look) {
        return TestTracks.car(helper.getLevel(), type, at, look);
    }

    private static Player board(GameTestHelper helper, SteamLocomotive locomotive) {
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(locomotive.position());
        helper.assertTrue(driver.startRiding(locomotive), "the driver couldn't get in");
        helper.assertTrue(locomotive.driver() == driver, "the first one aboard should be the driver");
        return driver;
    }

    private static void throttle(SteamLocomotive locomotive, int notches) {
        for (int i = 0; i < Math.abs(notches); i++) {
            locomotive.control(notches > 0 ? TrainControlPayload.THROTTLE_UP : TrainControlPayload.THROTTLE_DOWN);
        }
    }

    /** The gap between two cars' buffers, in a straight line (a little less than along the track on a curve). */
    private static double bufferGap(TrainCarEntity a, TrainCarEntity b) {
        return a.distanceTo(b) - a.halfLength() - b.halfLength();
    }

    private static void assertCoupledTight(GameTestHelper helper, TrainCarEntity a, TrainCarEntity b, double tolerance) {
        double gap = bufferGap(a, b);
        helper.assertTrue(Math.abs(gap - Trains.BUFFER_GAP) < tolerance,
                String.format("%s and %s should be buffer to buffer, are %.2f apart", a.getName().getString(), b.getName().getString(), gap));
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Track

    @GameTest(template = LINE)
    public static void trackBlocksInALineJoinUp(GameTestHelper helper) {
        straight(helper, 10);
        ServerLevel level = helper.getLevel();
        BlockPos middle = onLine(helper, 5);
        helper.assertTrue(TrackGraph.edgesFrom(level, middle, 1).size() == 1, "track should run on east");
        helper.assertTrue(TrackGraph.edgesFrom(level, middle, -1).size() == 1, "track should run on west");
        helper.assertTrue(TrackGraph.edgesFrom(level, onLine(helper, 9), 1).isEmpty(), "the last block should be the end");
        TrackPos pos = TrackPos.at(level, onLine(helper, 1), EAST);
        double walked = pos.walk(level, 20, TrackPos.STRAIGHTEST);
        helper.assertTrue(Math.abs(walked - 8) < 1.0E-6, "should walk to the end of the line, 8 blocks, walked " + walked);
        helper.succeed();
    }

    @GameTest(template = YARD)
    public static void curvesFollowTheRules(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        BlockPos start = helper.absolutePos(new BlockPos(5, 1, 5));
        TestTracks.line(level, start, TrackAxis.X, 1);
        // A quarter circle, radius 10: fine, and about 15.7 blocks long.
        TrackBuilder.Plan quarter = TrackBuilder.plan(level, start, start.offset(10, 0, 10), Vec3.ZERO, false);
        helper.assertTrue(quarter.ok(), "a radius-10 bend should be allowed: " + (quarter.problem() == null ? "" : quarter.problem().getString()));
        helper.assertTrue(Math.abs(quarter.shape().length() - Math.PI * 5) < 0.4, "a quarter circle of radius 10 is about 15.7 long, was " + quarter.shape().length());
        helper.assertTrue(quarter.endAxis() == TrackAxis.Z, "the new end should face south, along z");
        helper.assertTrue(!TrackBuilder.plan(level, start, start.offset(3, 0, 3), Vec3.ZERO, false).ok(), "a radius-3 bend is too sharp");
        helper.assertTrue(!TrackBuilder.plan(level, start, start.offset(0, 0, 10), Vec3.ZERO, false).ok(), "square off to the side isn't a curve");
        helper.assertTrue(!TrackBuilder.plan(level, start, start.offset(6, 5, 0), Vec3.ZERO, false).ok(), "5 up in 6 is too steep");
        level.setBlock(start.offset(20, 2, 0), Blocks.STONE.defaultBlockState(), 2);
        TrackBuilder.Plan ramp = TrackBuilder.plan(level, start, start.offset(20, 3, 0), Vec3.ZERO, false);
        helper.assertTrue(ramp.ok(), "3 up in 20 should do: " + (ramp.problem() == null ? "" : ramp.problem().getString()));
        // Build the quarter circle; then breaking its end takes the curve off the start too, and drops its track.
        helper.assertTrue(TrackBuilder.build(level, quarter, null), "the curve should build");
        BlockPos end = start.offset(10, 0, 10);
        helper.assertTrue(TrackGraph.edgesFrom(level, start, 1).size() == 1, "the curve should leave the start");
        helper.assertTrue(level.getBlockEntity(end) instanceof TrackBlockEntity track && track.connections().size() == 1, "the end should know about it");
        level.destroyBlock(end, true);
        helper.assertTrue(level.getBlockEntity(start) instanceof TrackBlockEntity track && track.connections().isEmpty(), "the start should forget it");
        int dropped = 0;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(end).inflate(2))) {
            if (item.getItem().is(ModItems.TRACK.get())) {
                dropped += item.getItem().getCount();
            }
        }
        helper.assertTrue(dropped == TrackBuilder.cost(quarter.shape()) + 1, "the curve's track and the block should drop, got " + dropped);
        helper.succeed();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Cars on the track

    @GameTest(template = LINE)
    public static void carsSitOnTheirBogies(GameTestHelper helper) {
        straight(helper, 30);
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), onLine(helper, 10), EAST);
        Vec3 f = loco.front().position();
        Vec3 r = loco.rear().position();
        helper.assertTrue(Math.abs(f.distanceTo(r) - 2 * loco.bogieOffset()) < 1.0E-3, "the bogies should be 3.5 apart");
        helper.assertTrue(f.x > r.x, "the front bogie should be to the east");
        helper.assertTrue(loco.flatForward().x > 0.999, "it should face east");
        TrainCarEntity blocked = ModEntities.PASSENGER_COACH.get().create(helper.getLevel());
        helper.assertTrue(TrainCarItem.place(helper.getLevel(), blocked, onLine(helper, 12), EAST) == TrainCarItem.Problem.BLOCKED,
                "a car can't go on top of another");
        TrainCarEntity endOfLine = ModEntities.PASSENGER_COACH.get().create(helper.getLevel());
        helper.assertTrue(TrainCarItem.place(helper.getLevel(), endOfLine, onLine(helper, 1), EAST) == TrainCarItem.Problem.NO_ROOM,
                "a coach needs track under both bogies");
        helper.succeed();
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void locomotiveRunsOnSteam(GameTestHelper helper) {
        straight(helper, 64);
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), onLine(helper, 5), EAST);
        Player driver = board(helper, loco);
        loco.setFuel(1600);
        throttle(loco, SteamLocomotive.MAX_THROTTLE);
        double start = loco.getX();
        helper.runAfterDelay(100, () -> {
            double moved = loco.getX() - start;
            helper.assertTrue(moved > 15, "should have steamed east, moved " + moved);
            helper.assertTrue(loco.getFuel() < 1600, "should have burnt fuel");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void noFuelNoGo(GameTestHelper helper) {
        straight(helper, 64);
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), onLine(helper, 10), EAST);
        Player driver = board(helper, loco);
        throttle(loco, SteamLocomotive.MAX_THROTTLE);
        double start = loco.getX();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(Math.abs(loco.getX() - start) < 0.01, "shouldn't move without fuel");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 300)
    public static void brakeAndHandbrake(GameTestHelper helper) {
        straight(helper, 64);
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), onLine(helper, 5), EAST);
        Player driver = board(helper, loco);
        loco.setFuel(16000);
        throttle(loco, SteamLocomotive.MAX_THROTTLE);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(loco.trainSpeed() > 0.2, "should be going by now, at " + loco.trainSpeed());
            loco.control(TrainControlPayload.BRAKE_ON);
        });
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(Math.abs(loco.trainSpeed()) < 1.0E-6, "the brake should have stopped it");
            loco.control(TrainControlPayload.BRAKE_OFF);
            driver.stopRiding();
        });
        helper.runAfterDelay(160, () -> {
            helper.assertTrue(Math.abs(loco.trainSpeed()) < 1.0E-6, "with nobody aboard, the handbrake should hold it");
            helper.assertTrue(loco.getThrottle() == 0, "the throttle should close when the driver gets out");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 300)
    public static void stopsAtTheEndOfTheLine(GameTestHelper helper) {
        straight(helper, 24);
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), onLine(helper, 4), EAST);
        Player driver = board(helper, loco);
        loco.setFuel(16000);
        throttle(loco, SteamLocomotive.MAX_THROTTLE);
        helper.runAfterDelay(200, () -> {
            TrackPos front = loco.front();
            helper.assertTrue(front.edge().to().equals(onLine(helper, 23)) && Math.abs(front.distance() - front.edge().geometry().length()) < 1.0E-6,
                    "the front bogie should be at the end of the track, is at " + front);
            helper.assertTrue(Math.abs(loco.trainSpeed()) < 0.01, "it should have stopped");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 300)
    public static void reversingPushesTheTrain(GameTestHelper helper) {
        straight(helper, 64);
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), onLine(helper, 40), EAST);
        PassengerCoach coach = car(helper, ModEntities.PASSENGER_COACH.get(), onLine(helper, 33), EAST);
        helper.assertTrue(Trains.couple(loco, coach) == Trains.CoupleResult.COUPLED, "should couple");
        assertCoupledTight(helper, loco, coach, 0.01);
        Player driver = board(helper, loco);
        loco.setFuel(16000);
        throttle(loco, -2);
        double start = coach.getX();
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(start - coach.getX() > 4, "the coach should have been pushed west, moved " + (start - coach.getX()));
            helper.assertTrue(loco.flatForward().x > 0.999, "the locomotive should still face east");
            assertCoupledTight(helper, loco, coach, 0.01);
            driver.stopRiding();
            helper.succeed();
        });
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Curves, junctions, slopes

    @GameTest(template = YARD, timeoutTicks = 600)
    public static void trainGoesRoundTheLoop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        BlockPos corner = helper.absolutePos(new BlockPos(2, 1, 8));
        TestTracks.loop(level, corner, 24, 10);
        BlockPos onStraight = corner.offset(10 + 18, 0, 0);
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), onStraight, EAST);
        PassengerCoach coach = car(helper, ModEntities.PASSENGER_COACH.get(), onStraight.offset(-7, 0, 0), EAST);
        FreightWagon wagon = car(helper, ModEntities.FREIGHT_WAGON.get(), onStraight.offset(-14, 0, 0), EAST);
        helper.assertTrue(Trains.couple(loco, coach) == Trains.CoupleResult.COUPLED, "the coach should couple");
        helper.assertTrue(Trains.couple(coach, wagon) == Trains.CoupleResult.COUPLED, "the wagon should couple");
        Player driver = board(helper, loco);
        loco.setFuel(16000);
        throttle(loco, SteamLocomotive.MAX_THROTTLE);
        double[] travelled = {0};
        Vec3[] last = {loco.position()};
        double[] squeeze = {0};
        for (int t = 10; t <= 500; t += 10) {
            helper.runAfterDelay(t, () -> {
                travelled[0] += loco.position().distanceTo(last[0]);
                last[0] = loco.position();
                for (TrainCarEntity car : List.<TrainCarEntity>of(loco, coach, wagon)) {
                    helper.assertTrue(!car.isRemoved() && car.front().isValid(level) && car.rear().isValid(level), car.getName().getString() + " came off");
                    helper.assertTrue(Math.abs(car.getY() - (corner.getY() + 0.25)) < 0.01, car.getName().getString() + " isn't on the rails");
                }
                // Round a bend the cars' middles are closer than along the track, never further.
                for (TrainCarEntity[] pair : new TrainCarEntity[][]{{loco, coach}, {coach, wagon}}) {
                    double gap = bufferGap(pair[0], pair[1]);
                    helper.assertTrue(gap < Trains.BUFFER_GAP + 0.01, "a coupling stretched: " + gap);
                    squeeze[0] = Math.min(squeeze[0], gap);
                }
            });
        }
        helper.runAfterDelay(510, () -> {
            ReikoTrains.LOGGER.info("Loop test: went {} blocks; on the bends the buffers came {} blocks closer", String.format("%.1f", travelled[0]),
                    String.format("%.2f", -squeeze[0]));
            helper.assertTrue(travelled[0] > 120, "the train should have gone round the loop, went " + travelled[0]);
            helper.assertTrue(squeeze[0] > -1.0, "the cars shouldn't run into each other on the bends");
            helper.assertTrue(Trains.train(wagon).size() == 3, "the train should still be together");
            driver.stopRiding();
            helper.succeed();
        });
    }

    /** A junction: a line going east, and a curve branching off to the right (south) at x = 14. */
    private static BlockPos junction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        BlockPos west = helper.absolutePos(new BlockPos(2, 1, 10));
        TestTracks.line(level, west, TrackAxis.X, 40);
        BlockPos branchStart = west.offset(12, 0, 0);
        TrackBuilder.Plan branch = TestTracks.connect(level, branchStart, branchStart.offset(12, 0, 5));
        TestTracks.line(level, branch.end().offset(1, 0, 1), TrackAxis.SE, 8);
        return west;
    }

    @GameTest(template = YARD, timeoutTicks = 300)
    public static void steerRightTakesTheBranch(GameTestHelper helper) {
        BlockPos west = junction(helper);
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), west.offset(7, 0, 0), EAST);
        Player driver = board(helper, loco);
        loco.setFuel(16000);
        loco.control(TrainControlPayload.STEER_RIGHT);
        throttle(loco, 2);
        helper.runAfterDelay(160, () -> {
            helper.assertTrue(loco.getZ() > west.getZ() + 3, "should have turned off south onto the branch, z went " + (loco.getZ() - west.getZ()));
            helper.assertTrue(loco.rear().isValid(helper.getLevel()), "the back of it should have followed");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 300)
    public static void straightOnAtTheJunction(GameTestHelper helper) {
        BlockPos west = junction(helper);
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), west.offset(10, 0, 0), EAST);
        PassengerCoach coach = car(helper, ModEntities.PASSENGER_COACH.get(), west.offset(3, 0, 0), EAST);
        Trains.couple(loco, coach);
        Player driver = board(helper, loco);
        loco.setFuel(16000);
        throttle(loco, 2);
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(loco.getX() > west.getX() + 20, "should have gone past the junction");
            helper.assertTrue(Math.abs(loco.getZ() - (west.getZ() + 0.5)) < 0.01, "should have gone straight on");
            helper.assertTrue(Math.abs(coach.getZ() - (west.getZ() + 0.5)) < 0.01, "the coach should have followed straight on");
            driver.stopRiding();
            helper.succeed();
        });
    }

    /** A low line, a ramp up 4 blocks over 16, and a high line; returns the start of the high line. */
    private static BlockPos ramp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        BlockPos low = helper.absolutePos(new BlockPos(2, 1, 20));
        TestTracks.line(level, low, TrackAxis.X, 12);
        BlockPos high = low.offset(27, 4, 0);
        for (int x = 0; x < 15; x++) {
            level.setBlock(high.offset(x, -1, 0), Blocks.STONE.defaultBlockState(), 2);
        }
        TestTracks.line(level, high, TrackAxis.X, 14);
        TestTracks.connect(level, low.offset(11, 0, 0), high);
        return high;
    }

    @GameTest(template = YARD, timeoutTicks = 400)
    public static void carsRollDownhill(GameTestHelper helper) {
        BlockPos high = ramp(helper);
        // Half on the ramp, its front end over the edge: gravity takes it down.
        FreightWagon wagon = car(helper, ModEntities.FREIGHT_WAGON.get(), high, WEST);
        helper.assertTrue(wagon.slope() < -0.01, "the wagon should tip down the ramp, slope " + wagon.slope());
        helper.runAfterDelay(240, () -> {
            helper.assertTrue(wagon.getY() < high.getY() - 1, "the wagon should have rolled down the ramp, is " + (high.getY() - wagon.getY()) + " down");
            helper.succeed();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 300)
    public static void locomotiveClimbsTheRamp(GameTestHelper helper) {
        BlockPos high = ramp(helper);
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), high.offset(-23, -4, 0), EAST);
        Player driver = board(helper, loco);
        loco.setFuel(16000);
        throttle(loco, SteamLocomotive.MAX_THROTTLE);
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(loco.getY() > high.getY(), "the locomotive should have climbed to the top, is " + (high.getY() - loco.getY()) + " below");
            driver.stopRiding();
            helper.succeed();
        });
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Couplings

    @GameTest(template = LINE)
    public static void couplingRules(GameTestHelper helper) {
        straight(helper, 64);
        ServerLevel level = helper.getLevel();
        SteamLocomotive first = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), onLine(helper, 6), EAST);
        SteamLocomotive second = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), onLine(helper, 16), EAST);

        // Two wagons nose to nose couple, and the second is drawn up to the first.
        FreightWagon a = car(helper, ModEntities.FREIGHT_WAGON.get(), onLine(helper, 22), EAST);
        FreightWagon b = car(helper, ModEntities.FREIGHT_WAGON.get(), onLine(helper, 29), WEST);
        helper.assertTrue(Trains.couple(a, b) == Trains.CoupleResult.COUPLED, "wagons nose to nose should couple");
        assertCoupledTight(helper, a, b, 0.01);
        // A coach couples on to either end, and the locomotive to the other end.
        PassengerCoach coach = car(helper, ModEntities.PASSENGER_COACH.get(), onLine(helper, 36), EAST);
        Trains.CoupleResult coachOn = Trains.couple(coach, b);
        helper.assertTrue(coachOn == Trains.CoupleResult.COUPLED, "the coach should couple to the end wagon: " + coachOn);
        Trains.CoupleResult locoOn = Trains.couple(second, a);
        helper.assertTrue(locoOn == Trains.CoupleResult.COUPLED, "the locomotive should couple to the other end: " + locoOn);
        List<TrainCarEntity> train = Trains.train(coach);
        helper.assertTrue(train.size() == 4 && train.contains(second), "the train should be four long: " + train.size());
        assertCoupledTight(helper, second, a, 0.01);
        assertCoupledTight(helper, b, coach, 0.01);
        // A car in the middle can't be coupled to.
        Trains.CoupleResult middle = Trains.couple(first, a);
        helper.assertTrue(middle != Trains.CoupleResult.COUPLED, "a middle car shouldn't couple");
        // Taking a car out of the middle splits the train.
        a.discard();
        helper.assertTrue(second.followerId() == null && b.leaderId() == null && Trains.train(b).size() == 2, "the train should have split");
        // Cars on a parallel line aren't on the same track.
        TestTracks.line(level, helper.absolutePos(new BlockPos(0, 1, 8)), TrackAxis.X, 20);
        FreightWagon other = car(helper, ModEntities.FREIGHT_WAGON.get(), helper.absolutePos(new BlockPos(9, 1, 8)), EAST);
        helper.assertTrue(Trains.couple(other, first) == Trains.CoupleResult.NOT_ON_TRACK, "cars on another line shouldn't couple");
        helper.succeed();
    }

    @GameTest(template = LINE, timeoutTicks = 300)
    public static void aPushedTrainStaysTogether(GameTestHelper helper) {
        straight(helper, 64);
        // The locomotive at the back, facing west, pushing two cars east in reverse.
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), onLine(helper, 6), WEST);
        PassengerCoach coach = car(helper, ModEntities.PASSENGER_COACH.get(), onLine(helper, 13), EAST);
        FreightWagon wagon = car(helper, ModEntities.FREIGHT_WAGON.get(), onLine(helper, 20), WEST);
        helper.assertTrue(Trains.couple(loco, coach) == Trains.CoupleResult.COUPLED, "should couple");
        helper.assertTrue(Trains.couple(coach, wagon) == Trains.CoupleResult.COUPLED, "should couple");
        Player driver = board(helper, loco);
        loco.setFuel(16000);
        throttle(loco, -2);
        double start = wagon.getX();
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(wagon.getX() - start > 5, "the train should have been pushed east, went " + (wagon.getX() - start));
            assertCoupledTight(helper, loco, coach, 0.01);
            assertCoupledTight(helper, coach, wagon, 0.01);
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE)
    public static void trainsAreSaved(GameTestHelper helper) {
        straight(helper, 40);
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), onLine(helper, 15), EAST);
        PassengerCoach coach = car(helper, ModEntities.PASSENGER_COACH.get(), onLine(helper, 8), EAST);
        Trains.couple(loco, coach);
        loco.setFuel(3200);
        CompoundTag saved = new CompoundTag();
        coach.saveWithoutId(saved);
        PassengerCoach copy = ModEntities.PASSENGER_COACH.get().create(helper.getLevel());
        copy.load(saved);
        helper.assertTrue(loco.getUUID().equals(copy.leaderId()), "the coupling should load back");
        helper.assertTrue(copy.front().position().distanceTo(coach.front().position()) < 1.0E-6, "the bogies should load back");
        CompoundTag savedLoco = new CompoundTag();
        loco.saveWithoutId(savedLoco);
        SteamLocomotive locoCopy = ModEntities.STEAM_LOCOMOTIVE.get().create(helper.getLevel());
        locoCopy.load(savedLoco);
        helper.assertTrue(locoCopy.getFuel() == 3200, "the fuel should load back");
        helper.succeed();
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void takingTheTrackAwayDerails(GameTestHelper helper) {
        straight(helper, 30);
        PassengerCoach coach = car(helper, ModEntities.PASSENGER_COACH.get(), onLine(helper, 10), EAST);
        helper.getLevel().removeBlock(onLine(helper, 12), false);
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(coach.isRemoved(), "the coach should have come off");
            boolean dropped = !helper.getLevel().getEntitiesOfClass(ItemEntity.class, coach.getBoundingBox().inflate(4),
                    item -> item.getItem().is(ModItems.PASSENGER_COACH.get())).isEmpty();
            helper.assertTrue(dropped, "the coach should drop as an item");
            helper.succeed();
        });
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Freight

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void wagonHoldsAndShowsItsLoad(GameTestHelper helper) {
        straight(helper, 30);
        ServerLevel level = helper.getLevel();
        FreightWagon wagon = car(helper, ModEntities.FREIGHT_WAGON.get(), onLine(helper, 10), EAST);
        helper.assertTrue(wagon.getContainerSize() == 108, "the wagon should hold 108 stacks");
        for (int i = 0; i < 60; i++) {
            wagon.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        // A hopper above the wagon drops things into it.
        BlockPos hopper = onLine(helper, 11).above(3);
        level.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN), 3);
        if (level.getBlockEntity(hopper) instanceof HopperBlockEntity entity) {
            entity.setItem(0, new ItemStack(Items.IRON_INGOT, 5));
        }
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(wagon.getLoad() == 3, "60 of 108 slots used should show three crates, shows " + wagon.getLoad());
            int iron = 0;
            for (int i = 0; i < wagon.getContainerSize(); i++) {
                if (wagon.getItem(i).is(Items.IRON_INGOT)) {
                    iron += wagon.getItem(i).getCount();
                }
            }
            helper.assertTrue(iron == 5, "the hopper should have filled the wagon, it has " + iron + " iron");
            helper.succeed();
        });
    }

    @GameTest(template = LINE)
    public static void fuelFillsTheFirebox(GameTestHelper helper) {
        straight(helper, 30);
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), onLine(helper, 10), EAST);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 40));
        loco.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(loco.getFuel() == SteamLocomotive.FUEL_CAPACITY, "32 coal should fill the firebox, has " + loco.getFuel());
        helper.assertTrue(player.getMainHandItem().getCount() == 8, "8 coal should be left over");
        helper.succeed();
    }

    @GameTest(template = LINE)
    public static void recipesAndFuelLoad(GameTestHelper helper) {
        for (String name : List.of("track", "steam_locomotive", "express_locomotive", "tender", "passenger_coach", "freight_wagon", "coupler",
                "signal", "station", "timetable", "switch_stand", "wagon_loader")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ReikoTrains.id(name)).isPresent(), "no recipe for " + name);
        }
        helper.assertTrue(new ItemStack(Items.COAL).is(SteamLocomotive.FUEL), "coal should be locomotive fuel");
        helper.assertTrue(!new ItemStack(Items.OAK_PLANKS).is(SteamLocomotive.FUEL), "planks shouldn't be");
        helper.succeed();
    }
}
