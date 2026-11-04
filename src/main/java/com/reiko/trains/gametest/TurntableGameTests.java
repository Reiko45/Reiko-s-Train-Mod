package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModBlocks;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.track.EdgeRef;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.train.ExpressLocomotive;
import com.reiko.trains.train.Locomotive;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.Tender;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import com.reiko.trains.turntable.TurntableBlock;
import com.reiko.trains.turntable.TurntableBlockEntity;
import com.reiko.trains.turntable.TurntableItem;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for the turntable. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class TurntableGameTests {
    private static final String YARD = "yard";
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 WEST = new Vec3(-1, 0, 0);
    /** The turntable's pivot, in the middle of the yard, a block below the track. */
    private static final BlockPos PIVOT = new BlockPos(24, 0, 24);

    private TurntableGameTests() {
    }

    /**
     * A floor all over the yard and a turntable in the middle of it, its bridge east-west; track runs off the pit's
     * west edge to the yard's edge, and off its east edge if {@code east}, and its north edge if {@code north}.
     */
    private static TurntableBlockEntity yard(GameTestHelper helper, boolean east, boolean north) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        TestTracks.line(level, at(helper, 0, 24), TrackAxis.X, 17);
        if (east) {
            TestTracks.line(level, at(helper, 32, 24), TrackAxis.X, 16);
        }
        if (north) {
            TestTracks.line(level, at(helper, 24, 0), TrackAxis.Z, 17);
        }
        return TestTracks.turntable(level, helper.absolutePos(PIVOT), 0);
    }

    private static BlockPos at(GameTestHelper helper, int x, int z) {
        return helper.absolutePos(new BlockPos(x, 1, z));
    }

    private static Player board(GameTestHelper helper, Locomotive loco) {
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(loco.position());
        driver.startRiding(loco);
        return driver;
    }

    private static void throttle(Locomotive loco, int notches) {
        int step = notches > 0 ? TrainControlPayload.THROTTLE_UP : TrainControlPayload.THROTTLE_DOWN;
        for (int i = 0; i < Math.abs(notches); i++) {
            loco.control(step);
        }
    }

    private static SteamLocomotive engine(GameTestHelper helper, int x, int z, Vec3 look) {
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, x, z), look);
        loco.setFuel(SteamLocomotive.FUEL_CAPACITY);
        return loco;
    }

    @GameTest(template = YARD, timeoutTicks = 300)
    public static void itDigsItsPitAndItsBridgeIsTrack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TurntableBlockEntity table = yard(helper, true, false);
        BlockPos pivot = helper.absolutePos(PIVOT);
        helper.assertTrue(level.getBlockState(pivot.offset(7, 0, 0)).is(ModBlocks.TURNTABLE.get()), "the pit should reach 7 blocks out");
        helper.assertTrue(level.getBlockState(pivot.offset(5, 0, 5)).is(ModBlocks.TURNTABLE.get()), "and 5 diagonally");
        helper.assertTrue(level.getBlockState(pivot.offset(6, 0, 6)).is(Blocks.STONE), "but no further: it's round");
        helper.assertTrue(level.getBlockState(pivot.offset(8, 0, 0)).is(Blocks.STONE), "or straight");
        BlockPos hub = table.hub();
        helper.assertTrue(TrackGraph.axis(level, hub) == TrackAxis.X, "the middle of the bridge should be track");
        helper.assertTrue(TrackGraph.axis(level, hub.offset(-7, 0, 0)) == TrackAxis.X, "all along it");
        helper.assertTrue(TrackGraph.axis(level, hub.offset(0, 0, 3)) == null, "but not off to the side of it");
        List<EdgeRef> on = TrackGraph.edgesFrom(level, at(helper, 16, 24), 1);
        helper.assertTrue(on.size() == 1 && on.getFirst().to().equals(hub.offset(-7, 0, 0)), "the track off the west edge should join it: " + on);
        // A train runs right across it, west to east.
        SteamLocomotive loco = engine(helper, 6, 24, EAST);
        Player driver = board(helper, loco);
        throttle(loco, 4);
        helper.succeedWhen(() -> {
            helper.assertTrue(loco.getX() > at(helper, 36, 24).getX(), "it should cross the turntable onto the track beyond");
            helper.assertTrue(loco.isOnTrack() && loco.isAlive(), "still on the rails");
            driver.stopRiding();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 360)
    public static void turningItTurnsTheTrainOnIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TurntableBlockEntity table = yard(helper, false, true);
        SteamLocomotive loco = engine(helper, 26, 24, EAST);
        Vec3 before = loco.position();
        TurntableBlockEntity.Result result = table.turn(2);
        helper.assertTrue(result == TurntableBlockEntity.Result.TURNING, "it should turn with the engine all on it: " + result);
        helper.assertTrue(loco.carriedBy() != null, "taking the engine with it");
        helper.assertTrue(TrackGraph.axis(level, table.hub()) == null, "and while it turns, it isn't track");
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(loco.isOnTrack() && loco.isAlive(), "half way round, the engine should still be on it");
            helper.assertTrue(Math.abs(loco.position().distanceTo(table.centre()) - before.distanceTo(table.centre())) < 0.05,
                    "the same distance from the middle");
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(!table.isTurning() && table.way() == 2, "a quarter turn on, it should point south: " + table.way());
            helper.assertTrue(loco.carriedBy() == null && loco.front().isValid(level) && loco.rear().isValid(level), "with the engine back on track");
            helper.assertTrue(loco.flatForward().z > 0.99, "facing south now: " + loco.flatForward());
            helper.assertTrue(Math.abs(loco.getZ() - (table.centre().z + 2)) < 0.05 && Math.abs(loco.getX() - table.centre().x) < 0.05,
                    "two blocks out from the middle, as it was: " + loco.position());
            // Half way round again and it runs off up the line to the north, chimney first.
            helper.assertTrue(table.turn(4) == TurntableBlockEntity.Result.TURNING, "it should turn half way round");
        });
        helper.runAfterDelay(210, () -> {
            helper.assertTrue(loco.flatForward().z < -0.99, "now it faces north: " + loco.flatForward());
            Player driver = board(helper, loco);
            throttle(loco, 4);
            helper.runAfterDelay(100, () -> {
                helper.assertTrue(loco.getZ() < at(helper, 24, 14).getZ() && loco.isAlive(), "and it should run off to the north");
                driver.stopRiding();
                helper.succeed();
            });
        });
    }

    @GameTest(template = YARD, timeoutTicks = 160)
    public static void itWontTurnUnderATrainHalfOnOrMoving(GameTestHelper helper) {
        TurntableBlockEntity table = yard(helper, false, false);
        // Standing with its back half over the pit's edge
        SteamLocomotive loco = engine(helper, 18, 24, EAST);
        helper.assertTrue(table.turn(4) == TurntableBlockEntity.Result.IN_THE_WAY, "a train half on should stop it turning");
        helper.assertTrue(!table.isTurning() && loco.carriedBy() == null, "and it shouldn't have");
        Player driver = board(helper, loco);
        throttle(loco, 1);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(table.turn(4) == TurntableBlockEntity.Result.MOVING, "a train moving on it should stop it turning");
            throttle(loco, -1);
            loco.control(TrainControlPayload.BRAKE_ON);
        });
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(Math.abs(loco.trainSpeed()) < 0.001, "the train should have stopped");
            driver.stopRiding();
            helper.assertTrue(table.turn(4) == TurntableBlockEntity.Result.TURNING, "and stopped all on it, it turns: " + loco.position());
            helper.succeed();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 240)
    public static void clicksAndRedstoneTurnItToTheNextTrack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TurntableBlockEntity table = yard(helper, false, true);
        BlockPos pivot = helper.absolutePos(PIVOT);
        helper.assertTrue(table.comparatorSignal() == 1, "pointing east, a comparator should read 1");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos floor = pivot.offset(3, 0, 3);
        level.getBlockState(floor).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false));
        helper.assertTrue(table.isTurning(), "clicking it should turn it");
        helper.runAfterDelay(70, () -> {
            // From east-west, clockwise, the next way with track off either end is north-south (pointing south).
            helper.assertTrue(table.way() == 2 && table.comparatorSignal() == 3, "it should have gone a quarter turn: " + table.way());
            // A redstone block beside the pit's east edge turns it on again, clockwise: back to east-west, pointing west.
            level.setBlockAndUpdate(pivot.offset(8, 0, 0), Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.assertTrue(table.isTurning(), "redstone should turn it");
        });
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(table.way() == 4, "pointing west: " + table.way());
            player.setShiftKeyDown(true);
            level.getBlockState(floor).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(floor), Direction.UP, floor, false));
            helper.assertTrue(table.isTurning(), "sneak-clicking should turn it the other way");
        });
        helper.runAfterDelay(215, () -> {
            helper.assertTrue(table.way() == 2, "back a quarter, pointing south: " + table.way());
            helper.succeed();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 1400)
    public static void timetabledTrainIsTurnedRoundAtTheEndOfTheLine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TurntableBlockEntity table = yard(helper, false, false);
        // The far end's station is by the bridge's east end; the other's on the line to the west.
        StationBlockEntity shed = TestTracks.station(level, at(helper, 32, 27), table.hub().offset(7, 0, 0), "Turntable", 1);
        StationBlockEntity town = TestTracks.station(level, at(helper, 4, 21), at(helper, 4, 24), "Town", 1);
        ExpressLocomotive express = TestTracks.car(level, ModEntities.EXPRESS_LOCOMOTIVE.get(), at(helper, 13, 24), EAST);
        Tender tender = TestTracks.car(level, ModEntities.TENDER.get(), at(helper, 5, 24), EAST);
        helper.assertTrue(Trains.couple(express, tender) == Trains.CoupleResult.COUPLED, "the tender should couple");
        tender.setFuel(Tender.FUEL_CAPACITY);
        express.autopilot().start(List.of(TestTracks.stop(shed), TestTracks.stop(town)));
        boolean[] turned = {false};
        helper.onEachTick(() -> {
            if (express.carriedBy() != null) {
                turned[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(turned[0], "it should have been turned round on the turntable");
            helper.assertTrue(express.flatForward().x < -0.99, "and be heading back west, chimney first");
            helper.assertTrue(express.getX() < at(helper, 12, 24).getX(), "back at the town: " + express.position());
            helper.assertTrue(express.getX() < tender.getX(), "with the engine in front");
            express.autopilot().stop();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 40)
    public static void breakingAnyOfItTakesItAllUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        yard(helper, false, false);
        BlockPos pivot = helper.absolutePos(PIVOT);
        helper.assertTrue(!TurntableItem.fits(level, pivot.offset(4, 0, 0)), "there's no room for another on top of it");
        level.destroyBlock(pivot.offset(-6, 0, 2), true);
        helper.assertTrue(level.getBlockState(pivot).isAir() && level.getBlockState(pivot.offset(5, 0, -5)).isAir(), "the whole pit should go");
        helper.assertTrue(level.getBlockState(pivot.offset(0, 1, 0)).isAir(), "with its bridge");
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pivot).inflate(9));
        int count = drops.stream().filter(e -> e.getItem().is(ModItems.TURNTABLE.get())).mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(count == 1, "and give back one turntable, gave " + count);
        helper.succeed();
    }

    @GameTest(template = YARD, timeoutTicks = 400)
    public static void smallTurntableTurnsATankEngine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        TestTracks.line(level, at(helper, 0, 24), TrackAxis.X, 19);
        TestTracks.line(level, at(helper, 24, 0), TrackAxis.Z, 19);
        BlockPos pivot = helper.absolutePos(PIVOT);
        TurntableBlock small = ModBlocks.SMALL_TURNTABLE.get();
        helper.assertTrue(TurntableItem.place(level, pivot, 0, small), "a small turntable should fit");
        TurntableBlockEntity table = (TurntableBlockEntity) level.getBlockEntity(pivot);
        helper.assertTrue(table != null && table.radius() == 5.5, "its pit is 11 blocks across");
        helper.assertTrue(level.getBlockState(pivot.offset(5, 0, 0)).is(small) && level.getBlockState(pivot.offset(6, 0, 0)).is(Blocks.STONE),
                "reaching 5 blocks out");
        helper.assertTrue(level.getBlockState(pivot.offset(3, 0, 3)).is(small) && level.getBlockState(pivot.offset(4, 0, 4)).is(Blocks.STONE),
                "and 3 diagonally");
        helper.assertTrue(level.getBlockState(pivot.offset(5, 0, 3)).is(small) && level.getBlockState(pivot.offset(5, 0, 4)).is(Blocks.STONE),
                "and round");
        helper.assertTrue(!level.getBlockState(pivot.offset(5, 0, 3)).getShape(level, pivot.offset(5, 0, 3)).isEmpty()
                && level.getBlockState(pivot.offset(5, 0, 3)).getShape(level, pivot.offset(5, 0, 3)).max(Direction.Axis.Y) == 1,
                "with a curved wall at its edge");
        BlockPos hub = table.hub();
        helper.assertTrue(TrackGraph.axis(level, hub.offset(-5, 0, 0)) == TrackAxis.X && TrackGraph.axis(level, hub.offset(-6, 0, 0)) == TrackAxis.X,
                "its bridge is track, up to the track beyond");
        List<EdgeRef> on = TrackGraph.edgesFrom(level, at(helper, 18, 24), 1);
        helper.assertTrue(on.size() == 1 && on.getFirst().to().equals(hub.offset(-5, 0, 0)), "joined up: " + on);
        SteamLocomotive loco = engine(helper, 24, 24, EAST);
        helper.assertTrue(table.turn(-2) == TurntableBlockEntity.Result.TURNING, "a tank engine fits on it");
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(!table.isTurning() && table.way() == 6 && loco.flatForward().z < -0.99, "it should face north now: " + loco.flatForward());
            Player driver = board(helper, loco);
            throttle(loco, 4);
            helper.runAfterDelay(100, () -> {
                helper.assertTrue(loco.getZ() < at(helper, 24, 14).getZ() && loco.isOnTrack(), "and run off to the north");
                driver.stopRiding();
                level.destroyBlock(pivot.offset(-4, 0, 2), true);
                helper.assertTrue(level.getBlockState(pivot).isAir(), "the whole pit should go");
                List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pivot).inflate(7));
                helper.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(ModItems.SMALL_TURNTABLE.get()))
                        && drops.stream().noneMatch(e -> e.getItem().is(ModItems.TURNTABLE.get())), "giving back a small turntable");
                helper.succeed();
            });
        });
    }

    @GameTest(template = YARD, timeoutTicks = 2000)
    public static void timetabledTrainsLineTheBridgeUpThemselves(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TurntableBlockEntity table = yard(helper, false, true);
        table.setWay(2); // north to south: not lined up with the line it comes in on, from the west
        StationBlockEntity shed = TestTracks.station(level, at(helper, 27, 6), at(helper, 24, 6), "Shed", 1);
        StationBlockEntity town = TestTracks.station(level, at(helper, 4, 21), at(helper, 4, 24), "Town", 1);
        SteamLocomotive loco = engine(helper, 8, 24, EAST);
        loco.autopilot().start(List.of(TestTracks.stop(shed), TestTracks.stop(town)));
        boolean[] atShed = {false};
        int[] turns = {0};
        boolean[] carried = {false};
        helper.onEachTick(() -> {
            if (loco.autopilot().isDwelling() && Math.abs(loco.getZ() - at(helper, 24, 6).getZ() - 0.5) < 4) {
                atShed[0] = true;
            }
            if (loco.carriedBy() != null && !carried[0]) {
                turns[0]++;
            }
            carried[0] = loco.carriedBy() != null;
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(atShed[0], "it should have got to the shed, off the turntable's north track");
            helper.assertTrue(loco.autopilot().isDwelling() && loco.getX() < at(helper, 10, 24).getX(), "and back to the town: " + loco.position());
            helper.assertTrue(loco.flatForward().x < -0.99, "chimney first");
            helper.assertTrue(turns[0] == 2, "going round on the bridge twice, it went round " + turns[0]);
            loco.autopilot().stop();
        });
    }
}
