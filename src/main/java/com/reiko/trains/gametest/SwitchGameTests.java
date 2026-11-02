package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.signal.SwitchBlock;
import com.reiko.trains.signal.SwitchBlockEntity;
import com.reiko.trains.signal.TrackSide;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackBuilder;
import com.reiko.trains.train.PassengerCoach;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.Trains;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for switches: set by hand, by redstone, and by timetabled trains. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class SwitchGameTests {
    private static final String YARD = "yard";
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private SwitchGameTests() {
    }

    /**
     * A main line along x (z = 10), with a branch curving off south (to the right, going east) at 12 blocks along,
     * then running away south-east. Returns the west end of the main line.
     */
    private static BlockPos junction(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        BlockPos west = helper.absolutePos(new BlockPos(2, 1, 10));
        TestTracks.line(level, west, TrackAxis.X, 40);
        BlockPos branchStart = west.offset(12, 0, 0);
        TrackBuilder.Plan branch = TestTracks.connect(level, branchStart, branchStart.offset(12, 0, 5));
        TestTracks.line(level, branch.end().offset(1, 0, 1), TrackAxis.SE, 14);
        return west;
    }

    /** A switch stand two blocks north of the junction. */
    private static SwitchBlockEntity stand(GameTestHelper helper, BlockPos west) {
        return TestTracks.switchStand(helper.getLevel(), west.offset(12, 0, -2));
    }

    private static boolean thrown(SwitchBlockEntity stand) {
        return stand.getBlockState().getValue(SwitchBlock.THROWN);
    }

    private static Player board(GameTestHelper helper, SteamLocomotive loco) {
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(loco.position());
        driver.startRiding(loco);
        return driver;
    }

    private static void throttle(SteamLocomotive loco, int notches) {
        for (int i = 0; i < notches; i++) {
            loco.control(TrainControlPayload.THROTTLE_UP);
        }
    }

    private static void click(GameTestHelper helper, SwitchBlockEntity stand, Player player) {
        BlockPos pos = stand.getBlockPos();
        helper.getLevel().getBlockState(pos).useWithoutItem(helper.getLevel(), player, new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
    }

    @GameTest(template = YARD, timeoutTicks = 60)
    public static void switchStandFindsItsJunction(GameTestHelper helper) {
        BlockPos west = junction(helper);
        SwitchBlockEntity stand = stand(helper, west);
        helper.assertTrue(stand.node().equals(west.offset(12, 0, 0)), "it should work the junction, not " + stand.node().toShortString());
        helper.assertTrue(stand.ways().size() == 2, "there should be two ways out of the junction, not " + stand.ways().size());
        helper.assertTrue(stand.thrownSide() == 1, "thrown, it should send trains right, the way the branch goes");
        helper.assertTrue(stand.redstoneWay() == 1, "to start with, redstone should throw it");
        helper.assertTrue(TrackSide.switchAt(helper.getLevel(), stand.node(), stand.side()) == stand, "trains should find it at the junction");
        helper.assertTrue(TrackSide.nearestJunction(helper.getLevel(), west.offset(30, 0, -2)) == null, "there's no junction near the far end of the line");
        helper.succeed();
    }

    @GameTest(template = YARD, timeoutTicks = 60)
    public static void clickingThrowsTheSwitchUnlessRedstoneHoldsIt(GameTestHelper helper) {
        BlockPos west = junction(helper);
        SwitchBlockEntity stand = stand(helper, west);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(!thrown(stand), "it should start set normally");
        click(helper, stand, player);
        helper.assertTrue(thrown(stand), "a click should throw it");
        click(helper, stand, player);
        helper.assertTrue(!thrown(stand), "another should put it back");
        BlockPos power = stand.getBlockPos().north();
        helper.getLevel().setBlock(power, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        helper.assertTrue(thrown(stand) && stand.isLocked(), "powered, it should be thrown and held");
        click(helper, stand, player);
        helper.assertTrue(thrown(stand), "held by redstone, a click shouldn't move it");
        helper.getLevel().setBlock(power, Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(!thrown(stand) && !stand.isLocked(), "with the power off it should go back, and be free");
        helper.succeed();
    }

    @GameTest(template = YARD, timeoutTicks = 60)
    public static void redstoneCanHoldTheSwitchStraight(GameTestHelper helper) {
        BlockPos west = junction(helper);
        SwitchBlockEntity stand = stand(helper, west);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        click(helper, stand, player);
        helper.assertTrue(stand.redstoneWay() == 0, "a sneak-click should have redstone hold it straight on instead");
        helper.assertTrue(!thrown(stand), "and not move it");
        click(helper, stand, helper.makeMockPlayer(GameType.SURVIVAL));
        helper.assertTrue(thrown(stand), "a plain click should still throw it");
        BlockPos power = stand.getBlockPos().north();
        helper.getLevel().setBlock(power, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        helper.assertTrue(!thrown(stand) && stand.isLocked(), "powered, it should go straight on and be held there");
        click(helper, stand, helper.makeMockPlayer(GameType.SURVIVAL));
        helper.assertTrue(!thrown(stand), "held by redstone, a click shouldn't move it");
        click(helper, stand, player);
        helper.assertTrue(stand.redstoneWay() == 1 && thrown(stand) && stand.isLocked(),
                "a sneak-click while it's powered should change it to thrown, there and then");
        click(helper, stand, player);
        helper.assertTrue(!thrown(stand) && stand.isLocked(), "and back to straight on");
        helper.getLevel().setBlock(power, Blocks.AIR.defaultBlockState(), 3);
        helper.assertTrue(!thrown(stand) && !stand.isLocked(), "with the power off it should be free, set normally");
        helper.succeed();
    }

    @GameTest(template = YARD, timeoutTicks = 60)
    public static void markerShowsWhatRedstoneDoes(GameTestHelper helper) {
        BlockPos west = junction(helper);
        SwitchBlockEntity stand = stand(helper, west);
        helper.assertTrue(!straightMarker(stand), "a new stand's marker should say redstone throws it");
        Player sneaking = helper.makeMockPlayer(GameType.SURVIVAL);
        sneaking.setShiftKeyDown(true);
        click(helper, stand, sneaking);
        helper.assertTrue(stand.redstoneWay() == 0 && straightMarker(stand), "set to hold it straight, the marker should say so");
        click(helper, stand, sneaking);
        helper.assertTrue(!straightMarker(stand), "and back to throwing");
        click(helper, stand, sneaking);
        helper.getLevel().setBlock(stand.getBlockPos().north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        helper.assertTrue(straightMarker(stand) && stand.isLocked() && !thrown(stand), "powered, it should hold it straight, marker lit");
        // A stand from before the marker: its marker is put right when it loads.
        helper.getLevel().setBlock(stand.getBlockPos(), stand.getBlockState().setValue(SwitchBlock.REDSTONE_STRAIGHT, false), 3);
        stand.onLoad();
        helper.runAfterDelay(3, () -> {
            helper.assertTrue(straightMarker(stand), "an out-of-date marker should be put right");
            helper.succeed();
        });
    }

    private static boolean straightMarker(SwitchBlockEntity stand) {
        return stand.getBlockState().getValue(SwitchBlock.REDSTONE_STRAIGHT);
    }

    @GameTest(template = YARD, timeoutTicks = 300)
    public static void thrownSwitchSendsTheTrainDownTheBranch(GameTestHelper helper) {
        BlockPos west = junction(helper);
        SwitchBlockEntity stand = stand(helper, west);
        stand.setThrown(true);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), west.offset(5, 0, 0), EAST);
        Player driver = board(helper, loco);
        loco.setFuel(16000);
        loco.control(TrainControlPayload.STEER_LEFT); // steering doesn't matter where there's a switch
        throttle(loco, 2);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(loco.hasPointsAhead(), "the gauges should show the switch ahead");
            helper.assertTrue(loco.pointsTurn() == 1, "set for the branch, to the right, not " + loco.pointsTurn());
            helper.assertTrue(loco.pointsDistance() < 10, "a few blocks ahead, not " + loco.pointsDistance());
        });
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(loco.getZ() > west.getZ() + 4, "the train should have gone down the branch, z went " + (loco.getZ() - west.getZ()));
            helper.assertTrue(loco.rear().isValid(helper.getLevel()), "the back of it should have followed");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 300)
    public static void normalSwitchKeepsTheTrainOnTheMainLine(GameTestHelper helper) {
        BlockPos west = junction(helper);
        SwitchBlockEntity stand = stand(helper, west);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), west.offset(5, 0, 0), EAST);
        Player driver = board(helper, loco);
        loco.setFuel(16000);
        loco.control(TrainControlPayload.STEER_RIGHT); // this would take the branch, without the switch
        throttle(loco, 2);
        helper.runAfterDelay(4, () -> helper.assertTrue(loco.hasPointsAhead() && loco.pointsTurn() == 0, "the gauges should show the switch set straight on"));
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(!thrown(stand), "the switch shouldn't have moved");
            helper.assertTrue(loco.getX() > west.getX() + 20, "the train should have gone past the junction");
            helper.assertTrue(Math.abs(loco.getZ() - (west.getZ() + 0.5)) < 0.01, "and straight on");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 300)
    public static void throwingASwitchUnderATrainDoesNotSplitIt(GameTestHelper helper) {
        BlockPos west = junction(helper);
        SwitchBlockEntity stand = stand(helper, west);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), west.offset(10, 0, 0), EAST);
        PassengerCoach coach = TestTracks.car(helper.getLevel(), ModEntities.PASSENGER_COACH.get(), west.offset(3, 0, 0), EAST);
        Trains.couple(loco, coach);
        Player driver = board(helper, loco);
        loco.setFuel(16000);
        throttle(loco, 2);
        double junctionX = west.getX() + 12.5;
        helper.onEachTick(() -> {
            if (!thrown(stand) && loco.getX() > junctionX + 4) {
                stand.setThrown(true); // the engine's over the junction; the coach isn't yet
            }
        });
        helper.runAfterDelay(220, () -> {
            helper.assertTrue(thrown(stand), "the switch should have been thrown");
            helper.assertTrue(coach.getX() > junctionX + 4, "the coach should have gone over the junction");
            helper.assertTrue(Math.abs(loco.getZ() - (west.getZ() + 0.5)) < 0.01, "the engine should have gone straight on");
            helper.assertTrue(Math.abs(coach.getZ() - (west.getZ() + 0.5)) < 0.01, "the coach should have followed it, not the switch");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 800)
    public static void timetabledTrainSetsTheSwitchForItsStation(GameTestHelper helper) {
        BlockPos west = junction(helper);
        SwitchBlockEntity stand = stand(helper, west);
        BlockPos stationTrack = west.offset(12, 0, 0).offset(13, 0, 6).offset(9, 0, 9);
        StationBlockEntity station = TestTracks.station(helper.getLevel(), stationTrack.offset(2, 0, -2), stationTrack, "Branch", 5);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), west.offset(5, 0, 0), EAST);
        loco.setFuel(16000);
        loco.autopilot().start(List.of(TestTracks.stop(station)));
        helper.succeedWhen(() -> {
            helper.assertTrue(thrown(stand), "the train should have thrown the switch for the branch");
            helper.assertTrue(loco.autopilot().isDwelling(), "and reached the branch station");
            helper.assertTrue(loco.getZ() > west.getZ() + 8, "down the branch");
        });
    }

    @GameTest(template = YARD, timeoutTicks = 800)
    public static void timetabledTrainPutsTheSwitchBack(GameTestHelper helper) {
        BlockPos west = junction(helper);
        SwitchBlockEntity stand = stand(helper, west);
        stand.setThrown(true);
        StationBlockEntity station = TestTracks.station(helper.getLevel(), west.offset(32, 0, -2), west.offset(32, 0, 0), "Main", 5);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), west.offset(5, 0, 0), EAST);
        loco.setFuel(16000);
        loco.autopilot().start(List.of(TestTracks.stop(station)));
        helper.succeedWhen(() -> {
            helper.assertTrue(!thrown(stand), "the train should have set the switch straight on, for the main line");
            helper.assertTrue(loco.autopilot().isDwelling(), "and reached the station");
            helper.assertTrue(Math.abs(loco.getZ() - (west.getZ() + 0.5)) < 0.01, "on the main line");
        });
    }

    @GameTest(template = YARD, timeoutTicks = 600)
    public static void timetabledTrainObeysASwitchHeldStraight(GameTestHelper helper) {
        BlockPos west = junction(helper);
        SwitchBlockEntity stand = stand(helper, west);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setShiftKeyDown(true);
        click(helper, stand, player);
        helper.getLevel().setBlock(stand.getBlockPos().north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        BlockPos stationTrack = west.offset(12, 0, 0).offset(13, 0, 6).offset(9, 0, 9);
        StationBlockEntity station = TestTracks.station(helper.getLevel(), stationTrack.offset(2, 0, -2), stationTrack, "Branch", 5);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), west.offset(5, 0, 0), EAST);
        loco.setFuel(16000);
        loco.autopilot().start(List.of(TestTracks.stop(station)));
        helper.succeedWhen(() -> {
            helper.assertTrue(!thrown(stand) && stand.isLocked(), "the switch should still be held straight on");
            helper.assertTrue(loco.getX() > west.getX() + 28, "the train should have had to go on past the branch");
            helper.assertTrue(Math.abs(loco.getZ() - (west.getZ() + 0.5)) < 0.01, "along the main line");
        });
    }

    /** The junction, with a second branch curving off north (to the left, going east) from the same block. */
    private static BlockPos threeWay(GameTestHelper helper) {
        BlockPos west = junction(helper);
        BlockPos branchStart = west.offset(12, 0, 0);
        TrackBuilder.Plan branch = TestTracks.connect(helper.getLevel(), branchStart, branchStart.offset(12, 0, -5));
        TestTracks.line(helper.getLevel(), branch.end().offset(1, 0, -1), TrackAxis.NE, 4);
        return west;
    }

    @GameTest(template = YARD, timeoutTicks = 60)
    public static void clickingGoesRoundAThreeWayJunction(GameTestHelper helper) {
        BlockPos west = threeWay(helper);
        SwitchBlockEntity stand = TestTracks.switchStand(helper.getLevel(), west.offset(10, 0, -3));
        helper.assertTrue(stand.ways().size() == 3, "there should be three ways out, not " + stand.ways().size());
        helper.assertTrue(stand.branchSides().equals(List.of(-1, 1)), "branches both left and right, not " + stand.branchSides());
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        int[] expected = {-1, 1, 0, -1};
        for (int way : expected) {
            click(helper, stand, player);
            helper.assertTrue(stand.setting() == way, "clicks should go left, right, straight on: expected " + way + ", got " + stand.setting());
        }
        Player sneaking = helper.makeMockPlayer(GameType.SURVIVAL);
        sneaking.setShiftKeyDown(true);
        int[] redstone = {1, 0, -1}; // it starts off thrown left
        for (int way : redstone) {
            click(helper, stand, sneaking);
            helper.assertTrue(stand.redstoneWay() == way, "sneak-clicks should go round the ways for redstone: expected " + way
                    + ", got " + stand.redstoneWay());
        }
        stand.set(1);
        helper.getLevel().setBlock(stand.getBlockPos().north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        helper.assertTrue(stand.setting() == -1 && stand.isLocked(), "powered, it should be thrown left and held there");
        helper.succeed();
    }

    @GameTest(template = YARD, timeoutTicks = 300)
    public static void threeWaySwitchSendsTheTrainLeft(GameTestHelper helper) {
        BlockPos west = threeWay(helper);
        SwitchBlockEntity stand = TestTracks.switchStand(helper.getLevel(), west.offset(10, 0, -3));
        stand.set(-1);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), west.offset(5, 0, 0), EAST);
        Player driver = board(helper, loco);
        loco.setFuel(16000);
        throttle(loco, 2);
        helper.runAfterDelay(4, () -> helper.assertTrue(loco.pointsTurn() == -1, "the gauges should show the switch set left, not " + loco.pointsTurn()));
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(loco.getZ() < west.getZ() - 3, "the train should have gone off north, z went " + (loco.getZ() - west.getZ()));
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 600)
    public static void timetabledTrainObeysASwitchHeldByRedstone(GameTestHelper helper) {
        BlockPos west = junction(helper);
        SwitchBlockEntity stand = stand(helper, west);
        helper.getLevel().setBlock(stand.getBlockPos().north(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        StationBlockEntity station = TestTracks.station(helper.getLevel(), west.offset(32, 0, -2), west.offset(32, 0, 0), "Main", 5);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), west.offset(5, 0, 0), EAST);
        loco.setFuel(16000);
        loco.autopilot().start(List.of(TestTracks.stop(station)));
        helper.succeedWhen(() -> {
            helper.assertTrue(thrown(stand) && stand.isLocked(), "the switch should still be held over");
            helper.assertTrue(loco.getZ() > west.getZ() + 6, "the train should have had to go down the branch");
        });
    }
}
