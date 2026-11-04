package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.network.SignalBoxStatePayload;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModBlocks;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.signal.SignalBlock;
import com.reiko.trains.signal.SignalBlockEntity;
import com.reiko.trains.signal.SignalBoxBlock;
import com.reiko.trains.signal.SignalBoxBlockEntity;
import com.reiko.trains.signal.SwitchBlockEntity;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackBuilder;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.PassengerCoach;
import com.reiko.trains.train.SteamLocomotive;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for the signal box (and that every block drops something). */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class SignalBoxGameTests {
    private static final String LINE = "line";
    private static final String LONG_LINE = "long_line";
    private static final String YARD = "yard";
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private SignalBoxGameTests() {
    }

    private static SignalBoxBlockEntity box(GameTestHelper helper, BlockPos relative) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(relative);
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(pos, ModBlocks.SIGNAL_BOX.get().defaultBlockState().setValue(SignalBoxBlock.FACING, Direction.NORTH), 3);
        return (SignalBoxBlockEntity) level.getBlockEntity(pos);
    }

    private static BlockPos at(GameTestHelper helper, int x) {
        return helper.absolutePos(new BlockPos(x, 1, 4));
    }

    private static void line(GameTestHelper helper, int length) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), length, 9);
        TestTracks.line(level, at(helper, 0), TrackAxis.X, length);
    }

    @GameTest(template = YARD, timeoutTicks = 40)
    public static void itSurveysTheLineRoundIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        BlockPos west = helper.absolutePos(new BlockPos(2, 1, 10));
        TestTracks.line(level, west, TrackAxis.X, 40);
        BlockPos junction = west.offset(12, 0, 0);
        TrackBuilder.Plan branch = TestTracks.connect(level, junction, junction.offset(12, 0, 5));
        TestTracks.line(level, branch.end().offset(1, 0, 1), TrackAxis.SE, 14);
        TestTracks.signal(level, west.offset(30, 0, -2), west.offset(30, 0, 0), 1);
        TestTracks.threeLightSignal(level, west.offset(4, 0, -2), west.offset(4, 0, 0), 1);
        TestTracks.switchStand(level, junction.offset(0, 0, -2));
        TestTracks.station(level, west.offset(20, 0, -3), west.offset(20, 0, 0), "Box Halt", 5);
        PassengerCoach coach = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), west.offset(36, 0, 0), EAST);
        SignalBoxBlockEntity box = box(helper, new BlockPos(24, 1, 30));
        helper.runAfterDelay(10, () -> {
            box.survey();
            helper.assertTrue(box.lines().size() >= 3, "it should map the main line, the curve and the branch: " + box.lines().size());
            float longest = 0;
            for (float[] points : box.lines()) {
                if (points.length == 4) {
                    longest = Math.max(longest, Math.abs(points[2] - points[0]));
                }
            }
            helper.assertTrue(longest >= 38, "the main line as one straight line: " + longest);
            // (Other tests run alongside, close enough for it to see their trains and signals too.)
            helper.assertTrue(box.stations().stream().anyMatch(s -> s.name().equals("Box Halt")), "and the station");
            SignalBoxStatePayload state = box.statePayload();
            helper.assertTrue(state.signals().stream().filter(s -> s.pos().equals(west.offset(30, 0, -2)) || s.pos().equals(west.offset(4, 0, -2)))
                    .count() == 2, "it should see both signals");
            helper.assertTrue(state.switches().stream().anyMatch(s -> s.pos().equals(junction.offset(0, 0, -2))
                            && s.ways().length == 2 * 2 * SignalBoxStatePayload.WAY_POINTS),
                    "and the switch, with its two ways");
            helper.assertTrue(state.cars().stream().anyMatch(c -> c.id() == coach.getId()), "and the coach");
            SignalBoxStatePayload.SignalMark far = state.signals().stream().filter(s -> s.pos().equals(west.offset(30, 0, -2))).findFirst()
                    .orElseThrow();
            helper.assertTrue(far.aspect() == SignalBoxStatePayload.RED, "with the coach beyond it, that signal's red");
            helper.assertTrue(far.z() < far.nodeZ(), "its lamp drawn on its side of the track (north)");
            helper.assertTrue(!box.covers(box.getBlockPos().offset(SignalBoxBlockEntity.RANGE + 1, 0, 0)), "it doesn't reach past its range");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 500)
    public static void itHoldsSignalsAtRedAndLetsThemGo(GameTestHelper helper) {
        line(helper, 64);
        SignalBlockEntity signal = TestTracks.signal(helper.getLevel(), helper.absolutePos(new BlockPos(35, 1, 6)), at(helper, 35), 1);
        SignalBoxBlockEntity box = box(helper, new BlockPos(30, 1, 1));
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 6), EAST);
        loco.setFuel(16000);
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(loco.position());
        driver.startRiding(loco);
        helper.assertTrue(box.toggleSignal(signal.getBlockPos()) == SignalBoxBlockEntity.Done.HELD, "the box should hold the signal");
        helper.assertTrue(box.getBlockPos().equals(signal.heldBy()), "and the signal know which box holds it");
        for (int i = 0; i < 4; i++) {
            loco.control(TrainControlPayload.THROTTLE_UP);
        }
        double signalX = signal.node().getX() + 0.5;
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(!signal.getBlockState().getValue(SignalBlock.CLEAR), "held, the signal should be red");
            helper.assertTrue(Math.abs(loco.trainSpeed()) < 1.0E-6 && loco.getX() + loco.halfLength() < signalX, "and the train stopped at it");
            helper.assertTrue(box.toggleSignal(signal.getBlockPos()) == SignalBoxBlockEntity.Done.RELEASED, "clicked again, it lets it go");
        });
        helper.runAfterDelay(320, () -> {
            helper.assertTrue(loco.getX() > signalX + 3, "let go, the train should go on past it");
            box.toggleSignal(signal.getBlockPos());
            helper.getLevel().destroyBlock(box.getBlockPos(), false);
            helper.assertTrue(signal.heldBy() == null, "taking the box away lets go of the signals it holds");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 40)
    public static void itThrowsSwitches(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        BlockPos west = helper.absolutePos(new BlockPos(2, 1, 10));
        TestTracks.line(level, west, TrackAxis.X, 40);
        BlockPos junction = west.offset(12, 0, 0);
        TestTracks.connect(level, junction, junction.offset(12, 0, 5));
        SwitchBlockEntity stand = TestTracks.switchStand(level, junction.offset(0, 0, -2));
        SignalBoxBlockEntity box = box(helper, new BlockPos(30, 1, 30));
        helper.assertTrue(!stand.isThrown(), "the switch starts straight on");
        helper.assertTrue(box.throwSwitch(stand.getBlockPos()) == SignalBoxBlockEntity.Done.THROWN && stand.isThrown(), "the box throws it");
        box.survey();
        helper.assertTrue(box.statePayload().switches().stream().anyMatch(s -> s.pos().equals(stand.getBlockPos()) && s.set() >= 0),
                "and shows which way it's set");
        box.throwSwitch(stand.getBlockPos());
        helper.assertTrue(!stand.isThrown(), "and back");
        level.setBlockAndUpdate(stand.getBlockPos().east(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        helper.runAfterDelay(2, () -> {
            boolean thrown = stand.isThrown();
            helper.assertTrue(box.throwSwitch(stand.getBlockPos()) == SignalBoxBlockEntity.Done.LOCKED, "held by redstone, it can't be thrown");
            helper.assertTrue(stand.isThrown() == thrown, "and isn't");
            helper.succeed();
        });
    }

    @GameTest(template = LONG_LINE, timeoutTicks = 20)
    public static void itOnlyReachesSoFar(GameTestHelper helper) {
        line(helper, 256);
        SignalBlockEntity near = TestTracks.signal(helper.getLevel(), helper.absolutePos(new BlockPos(60, 1, 6)), at(helper, 60), 1);
        SignalBlockEntity far = TestTracks.signal(helper.getLevel(), helper.absolutePos(new BlockPos(200, 1, 6)), at(helper, 200), 1);
        FreightWagon wagon = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 150), EAST);
        SignalBoxBlockEntity box = box(helper, new BlockPos(10, 1, 1));
        helper.assertTrue(box.toggleSignal(far.getBlockPos()) == SignalBoxBlockEntity.Done.NOT_FOUND, "a signal 190 blocks off is out of its reach");
        helper.assertTrue(far.heldBy() == null, "and isn't held");
        helper.assertTrue(box.toggleSignal(near.getBlockPos()) == SignalBoxBlockEntity.Done.HELD, "but one 50 blocks off is in it");
        box.survey();
        helper.assertTrue(box.statePayload().cars().stream().noneMatch(c -> c.id() == wagon.getId()), "and a wagon 140 blocks off isn't on its map");
        helper.succeed();
    }

    @GameTest(template = LINE, timeoutTicks = 20)
    public static void everyBlockDropsSomething(GameTestHelper helper) {
        var tables = helper.getLevel().getServer().reloadableRegistries();
        StringBuilder missing = new StringBuilder();
        for (Block block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            ResourceKey<LootTable> key = block.getLootTable();
            // Blocks that take themselves up in code (turntables, engine sheds) or never drop (the crossing's barrier) have none.
            if (!id.getNamespace().equals(ReikoTrains.MODID) || key == BuiltInLootTables.EMPTY) {
                continue;
            }
            if (tables.getLootTable(key) == LootTable.EMPTY) {
                missing.append(' ').append(id.getPath());
            }
        }
        helper.assertTrue(missing.isEmpty(), "every block should have a loot table; missing:" + missing);
        helper.succeed();
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Routes, turntables, the comparator, and the map it remembers

    @GameTest(template = YARD, timeoutTicks = 600)
    public static void itSetsARouteAndLetsItGoAfterATrain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        BlockPos west = helper.absolutePos(new BlockPos(2, 1, 10));
        TestTracks.line(level, west, TrackAxis.X, 40);
        BlockPos junction = west.offset(14, 0, 0);
        TrackBuilder.Plan branch = TestTracks.connect(level, junction, junction.offset(12, 0, 5));
        BlockPos branchLine = branch.end().offset(1, 0, 1);
        TestTracks.line(level, branchLine, TrackAxis.SE, 14);
        SignalBlockEntity entry = TestTracks.signal(level, west.offset(8, 0, -2), west.offset(8, 0, 0), 1);
        SignalBlockEntity mainExit = TestTracks.signal(level, west.offset(32, 0, -2), west.offset(32, 0, 0), 1);
        BlockPos branchNode = branchLine.offset(4, 0, 4);
        SignalBlockEntity branchExit = TestTracks.signal(level, branchNode.offset(1, 0, -1), branchNode, 1);
        SignalBlockEntity backwards = TestTracks.signal(level, west.offset(36, 0, 2), west.offset(36, 0, 0), -1);
        SwitchBlockEntity stand = TestTracks.switchStand(level, junction.offset(0, 0, -2));
        SignalBoxBlockEntity box = box(helper, new BlockPos(30, 1, 30));
        helper.assertTrue(!stand.isThrown(), "the switch starts straight on");
        helper.assertTrue(box.setRoute(entry.getBlockPos(), backwards.getBlockPos()) == SignalBoxBlockEntity.Done.NO_ROUTE,
                "no route to a signal facing the other way");
        helper.assertTrue(box.setRoute(entry.getBlockPos(), branchExit.getBlockPos()) == SignalBoxBlockEntity.Done.ROUTE_SET, "a route to the branch");
        helper.assertTrue(stand.isThrown() && stand.isHeld() && box.getBlockPos().equals(stand.routeBy()), "throws the switch and holds it");
        helper.assertTrue(box.throwSwitch(stand.getBlockPos()) == SignalBoxBlockEntity.Done.LOCKED, "so it can't be thrown back meanwhile");
        helper.assertTrue(box.statePayload().routes().stream().anyMatch(r -> r.entry().equals(entry.getBlockPos()) && r.points().length > 10),
                "the route shows on the map");
        helper.assertTrue(box.setRoute(entry.getBlockPos(), mainExit.getBlockPos()) == SignalBoxBlockEntity.Done.ROUTE_CANCELLED && !stand.isHeld(),
                "clicking its start again cancels it, letting the switch go");
        helper.assertTrue(box.setRoute(entry.getBlockPos(), mainExit.getBlockPos()) == SignalBoxBlockEntity.Done.ROUTE_SET && !stand.isThrown(),
                "a route straight on sets it straight");
        box.setRoute(entry.getBlockPos(), mainExit.getBlockPos());
        helper.assertTrue(box.setRoute(entry.getBlockPos(), branchExit.getBlockPos()) == SignalBoxBlockEntity.Done.ROUTE_SET, "and the branch again");
        SteamLocomotive loco = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), west.offset(3, 0, 0), EAST);
        loco.setFuel(16000);
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(loco.position());
        driver.startRiding(loco);
        for (int i = 0; i < 2; i++) {
            loco.control(TrainControlPayload.THROTTLE_UP);
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(loco.getZ() > junction.getZ() + 5, "the train should take the branch the route was set for");
            helper.assertTrue(box.routes().isEmpty() && !stand.isHeld(), "and once it's gone through, the route's let go");
            driver.stopRiding();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 200)
    public static void itTurnsTurntables(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        TestTracks.line(level, helper.absolutePos(new BlockPos(0, 1, 24)), TrackAxis.X, 17);
        TestTracks.line(level, helper.absolutePos(new BlockPos(24, 1, 0)), TrackAxis.Z, 17);
        com.reiko.trains.turntable.TurntableBlockEntity table = TestTracks.turntable(level, helper.absolutePos(new BlockPos(24, 0, 24)), 0);
        SignalBoxBlockEntity box = box(helper, new BlockPos(4, 1, 4));
        float before = table.angleAt(level.getGameTime());
        helper.assertTrue(box.turnTurntable(table.getBlockPos()) == SignalBoxBlockEntity.Done.TURNING, "the box turns the turntable");
        box.survey();
        helper.assertTrue(box.statePayload().tables().stream().anyMatch(t -> t.pos().equals(table.getBlockPos()) && t.radius() > 7),
                "which is on its map, to click");
        helper.succeedWhen(() -> {
            helper.assertTrue(!table.isTurning() && Math.abs(table.angleAt(level.getGameTime()) - before) > 1, "round to the next track");
        });
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void comparatorCountsTrains(GameTestHelper helper) {
        line(helper, 64);
        TestTracks.car(helper.getLevel(), ModEntities.PASSENGER_COACH.get(), at(helper, 10), EAST);
        TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 40), EAST);
        SignalBoxBlockEntity box = box(helper, new BlockPos(30, 1, 1));
        helper.runAfterDelay(30, () -> {
            // (Other tests run alongside, close enough for it to count their trains too.)
            helper.assertTrue(box.trainCount() >= 2, "it should count the two cars as two trains, at least: " + box.trainCount());
            BlockPos pos = box.getBlockPos();
            helper.assertTrue(helper.getLevel().getBlockState(pos).getAnalogOutputSignal(helper.getLevel(), pos) == box.trainCount(),
                    "a comparator reads the count");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 20)
    public static void itRemembersTrackItHasSeen(GameTestHelper helper) {
        line(helper, 64);
        SignalBoxBlockEntity box = box(helper, new BlockPos(30, 1, 1));
        box.survey();
        int seen = box.lines().size();
        helper.assertTrue(seen > 0, "it sees the line");
        net.minecraft.nbt.CompoundTag saved = box.saveWithoutMetadata(helper.getLevel().registryAccess());
        SignalBoxBlockEntity loaded = new SignalBoxBlockEntity(box.getBlockPos(), box.getBlockState());
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());
        helper.assertTrue(loaded.faded().size() >= seen, "saved with the world, it remembers the track it saw (faded till it looks again): "
                + loaded.faded().size());
        helper.succeed();
    }
}
