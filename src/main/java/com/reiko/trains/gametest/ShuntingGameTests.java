package com.reiko.trains.gametest;

import com.mojang.serialization.Dynamic;
import com.reiko.trains.ReikoTrains;
import com.reiko.trains.item.TimetableItem;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.Timetable;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for timetabled trains leaving wagons at stations and picking them up. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class ShuntingGameTests {
    private static final String LINE = "line";
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private ShuntingGameTests() {
    }

    /** Track along x at z = 4 (y = 1), from x = 0 to {@code length - 1}, on a floor. */
    private static void line(GameTestHelper helper, int length) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
        TestTracks.line(level, at(helper, 0), TrackAxis.X, length);
    }

    private static BlockPos at(GameTestHelper helper, int x) {
        return helper.absolutePos(new BlockPos(x, 1, 4));
    }

    private static StationBlockEntity station(GameTestHelper helper, int x, String name) {
        return TestTracks.station(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 7)), at(helper, x), name, 1);
    }

    private static <T extends TrainCarEntity> T car(GameTestHelper helper, net.minecraft.world.entity.EntityType<T> type, int x) {
        return TestTracks.car(helper.getLevel(), type, at(helper, x), EAST);
    }

    private static boolean dwellingAt(SteamLocomotive loco, StationBlockEntity station) {
        Timetable.Stop current = loco.autopilot().current();
        return loco.autopilot().isDwelling() && current != null && current.station().equals(station.getBlockPos());
    }

    @GameTest(template = LINE, timeoutTicks = 900)
    public static void trainLeavesWagonsAtAStation(GameTestHelper helper) {
        line(helper, 64);
        StationBlockEntity yard = station(helper, 30, "Yard");
        StationBlockEntity end = station(helper, 58, "End");
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 14);
        FreightWagon first = car(helper, ModEntities.FREIGHT_WAGON.get(), 8);
        FreightWagon last = car(helper, ModEntities.FREIGHT_WAGON.get(), 2);
        Trains.couple(first, last);
        Trains.couple(loco, first);
        loco.setFuel(16000);
        loco.autopilot().start(List.of(TestTracks.stop(yard).withWagons(1), TestTracks.stop(end)));
        double yardX = yard.node().getX() + 0.5;
        helper.succeedWhen(() -> {
            helper.assertTrue(dwellingAt(loco, end), "the train should get to the end of the line");
            List<TrainCarEntity> train = Trains.train(loco);
            helper.assertTrue(train.size() == 2 && train.contains(first), "it should have only its first wagon left, has " + train.size() + " cars");
            helper.assertTrue(Trains.train(last).size() == 1, "the last wagon should be on its own");
            helper.assertTrue(last.getX() < yardX && last.getX() > yardX - 20, "standing at the yard, where it was left: " + (last.getX() - yardX));
            helper.assertTrue(Math.abs(last.trainSpeed()) < 1.0E-6, "and not moving");
        });
    }

    @GameTest(template = LINE, timeoutTicks = 900)
    public static void trainPropelsIntoASidingAndLeavesItsWagons(GameTestHelper helper) {
        // A dead-end siding: the track stops at x = 40, with its station near the end; the other end of the line is
        // a dead end too, with a station there.
        line(helper, 41);
        StationBlockEntity siding = station(helper, 36, "Siding");
        StationBlockEntity main = station(helper, 4, "Main");
        FreightWagon front = car(helper, ModEntities.FREIGHT_WAGON.get(), 24);
        FreightWagon middle = car(helper, ModEntities.FREIGHT_WAGON.get(), 18);
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 11);
        Trains.couple(front, middle);
        Trains.couple(middle, loco);
        loco.setFuel(16000);
        Timetable.Stop dropAll = TestTracks.stop(siding).withWagons(Timetable.Stop.DROP_ALL);
        loco.autopilot().start(List.of(dropAll, TestTracks.stop(main)));
        double sidingX = siding.node().getX() + 0.5;
        helper.succeedWhen(() -> {
            helper.assertTrue(dwellingAt(loco, main), "the engine should get back to the main station");
            helper.assertTrue(Trains.train(loco).size() == 1, "on its own, having left both wagons");
            List<TrainCarEntity> left = Trains.train(front);
            helper.assertTrue(left.size() == 2 && left.contains(middle), "the two wagons should be together in the siding");
            helper.assertTrue(front.getX() > sidingX - 6, "up at the end of it, by the station: " + (front.getX() - sidingX));
        });
    }

    @GameTest(template = LINE, timeoutTicks = 900)
    public static void engineLeadingIntoASidingKeepsItsWagons(GameTestHelper helper) {
        // The same siding, but the engine's in front going in: its wagons are on the side it leaves by, so it can't
        // leave them (it'd need to run round them), and takes them back out with it.
        line(helper, 41);
        StationBlockEntity siding = station(helper, 36, "Siding");
        StationBlockEntity main = station(helper, 4, "Main");
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 24);
        FreightWagon wagon = car(helper, ModEntities.FREIGHT_WAGON.get(), 18);
        Trains.couple(loco, wagon);
        loco.setFuel(16000);
        loco.autopilot().start(List.of(TestTracks.stop(siding).withWagons(Timetable.Stop.DROP_ALL), TestTracks.stop(main)));
        helper.succeedWhen(() -> {
            helper.assertTrue(dwellingAt(loco, main), "the train should get back to the main station");
            helper.assertTrue(Trains.train(loco).contains(wagon), "still with its wagon");
        });
    }

    @GameTest(template = LINE, timeoutTicks = 900)
    public static void trainPicksUpWaitingWagons(GameTestHelper helper) {
        line(helper, 64);
        StationBlockEntity yard = station(helper, 30, "Yard");
        StationBlockEntity end = station(helper, 58, "End");
        // Two wagons waiting just past the yard's nameboard.
        FreightWagon near = car(helper, ModEntities.FREIGHT_WAGON.get(), 36);
        FreightWagon far = car(helper, ModEntities.FREIGHT_WAGON.get(), 42);
        Trains.couple(near, far);
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 8);
        loco.setFuel(16000);
        loco.autopilot().start(List.of(TestTracks.stop(yard).withWagons(Timetable.Stop.PICK_UP), TestTracks.stop(end)));
        int[] atYard = {0};
        helper.onEachTick(() -> {
            if (dwellingAt(loco, yard)) {
                atYard[0] = Trains.train(loco).size();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(atYard[0] == 3, "at the yard, it should have coupled on to both wagons, has " + atYard[0] + " cars");
            helper.assertTrue(dwellingAt(loco, end), "and taken them on to the end of the line");
            List<TrainCarEntity> train = Trains.train(loco);
            helper.assertTrue(train.contains(near) && train.contains(far), "with both wagons");
        });
    }

    @GameTest(template = LINE, timeoutTicks = 600)
    public static void pickUpStopWithNothingWaitingIsAnOrdinaryStop(GameTestHelper helper) {
        line(helper, 64);
        StationBlockEntity yard = station(helper, 30, "Yard");
        // A wagon well past the yard isn't waiting there: the train leaves it be.
        FreightWagon elsewhere = car(helper, ModEntities.FREIGHT_WAGON.get(), 60);
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 8);
        loco.setFuel(16000);
        loco.autopilot().start(List.of(TestTracks.stop(yard).withWagons(Timetable.Stop.PICK_UP)));
        double yardX = yard.node().getX() + 0.5;
        helper.succeedWhen(() -> {
            helper.assertTrue(dwellingAt(loco, yard), "the train should stop at the yard");
            helper.assertTrue(Math.abs(loco.getX() + loco.halfLength() - yardX) < 1, "with its front at the nameboard");
            helper.assertTrue(Trains.train(loco).size() == 1 && Trains.train(elsewhere).size() == 1, "picking nothing up");
        });
    }

    @GameTest(template = LINE)
    public static void timetableSaysWhatToDoWithWagons(GameTestHelper helper) {
        line(helper, 64);
        StationBlockEntity yard = station(helper, 30, "Yard");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack timetable = new ItemStack(ModItems.TIMETABLE.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, timetable);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(yard.getBlockPos()), Direction.UP, yard.getBlockPos(), false);
        int[] expected = {Timetable.Stop.NONE, 1, 2, 3, 4, Timetable.Stop.DROP_ALL, Timetable.Stop.PICK_UP, Timetable.Stop.NONE};
        for (int i = 0; i < expected.length; i++) {
            timetable.getItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            List<Timetable.Stop> stops = TimetableItem.stops(timetable);
            helper.assertTrue(stops.size() == 1, "clicking the same station should never add it twice");
            helper.assertTrue(stops.getFirst().wagons() == expected[i], "click " + (i + 1) + " should set " + expected[i] + ", not " + stops.getFirst().wagons());
        }
        // Saved and loaded, it keeps what to do with wagons; a stop saved before there was any loads as nothing.
        Timetable.Stop pickUp = TestTracks.stop(yard).withWagons(Timetable.Stop.PICK_UP);
        var saved = Timetable.Stop.CODEC.encodeStart(NbtOps.INSTANCE, pickUp).getOrThrow();
        helper.assertTrue(Timetable.Stop.CODEC.parse(NbtOps.INSTANCE, saved).getOrThrow().equals(pickUp), "a saved stop should load the same");
        CompoundTag old = ((CompoundTag) saved).copy();
        old.remove("wagons");
        helper.assertTrue(Timetable.Stop.CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, old)).getOrThrow().wagons() == Timetable.Stop.NONE,
                "an old stop should load with nothing to do with wagons");
        helper.succeed();
    }
}
