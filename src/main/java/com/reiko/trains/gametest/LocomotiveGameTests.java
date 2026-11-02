package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.freight.LoaderMode;
import com.reiko.trains.freight.WagonLoaderBlockEntity;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.ExpressLocomotive;
import com.reiko.trains.train.PassengerCoach;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.Tender;
import com.reiko.trains.train.Trains;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for the express engine and its tender, and for trains with more than one locomotive. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class LocomotiveGameTests {
    private static final String LINE = "line";
    private static final String YARD = "yard";
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 WEST = new Vec3(-1, 0, 0);

    private LocomotiveGameTests() {
    }

    /** Track along x at z = {@code z} (y = 1), the whole 64 blocks, on a floor. */
    private static void line(GameTestHelper helper, int z) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
        TestTracks.line(level, at(helper, 0, z), TrackAxis.X, 64);
    }

    private static BlockPos at(GameTestHelper helper, int x, int z) {
        return helper.absolutePos(new BlockPos(x, 1, z));
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

    /** An express engine facing east at {@code x}, with its tender behind it. */
    private static ExpressLocomotive express(GameTestHelper helper, int x, int z, Tender[] tender) {
        ExpressLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.EXPRESS_LOCOMOTIVE.get(), at(helper, x, z), EAST);
        tender[0] = TestTracks.car(helper.getLevel(), ModEntities.TENDER.get(), at(helper, x - 8, z), EAST);
        helper.assertTrue(Trains.couple(loco, tender[0]) == Trains.CoupleResult.COUPLED, "the tender should couple to the engine");
        return loco;
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void expressNeedsItsTender(GameTestHelper helper) {
        line(helper, 4);
        ExpressLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.EXPRESS_LOCOMOTIVE.get(), at(helper, 20, 4), EAST);
        Player driver = board(helper, loco);
        throttle(loco, 4);
        double startX = loco.getX();
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(Math.abs(loco.getX() - startX) < 0.01, "with no tender it shouldn't move");
            helper.assertTrue(loco.getFuelPercent() == -1, "the gauges should say there's no tender");
            Tender tender = TestTracks.car(helper.getLevel(), ModEntities.TENDER.get(), at(helper, 12, 4), EAST);
            helper.assertTrue(Trains.couple(loco, tender) == Trains.CoupleResult.COUPLED, "the tender should couple");
            Player fireman = helper.makeMockPlayer(GameType.SURVIVAL);
            fireman.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 64));
            loco.interact(fireman, InteractionHand.MAIN_HAND);
            helper.assertTrue(tender.fuel() == 64 * 1600, "coal shovelled into the engine should go in its tender, has " + tender.fuel());
            helper.assertTrue(fireman.getMainHandItem().isEmpty(), "all 64 coal should have gone in");
            helper.assertTrue(tender.renderLoad() == 2, "half full, the tender should show two layers of coal, not " + tender.renderLoad());
            loco.control(TrainControlPayload.THROTTLE_DOWN);
            loco.control(TrainControlPayload.THROTTLE_UP);
        });
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(loco.getX() > startX + 3, "with its tender it should go");
            helper.assertTrue(loco.getFuelPercent() > 0 && loco.isSteaming(), "on the tender's coal");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 600)
    public static void expressIsFasterThanTheTankEngine(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        TestTracks.loop(level, helper.absolutePos(new BlockPos(1, 1, 2)), 24, 10);
        Tender[] tender = new Tender[1];
        ExpressLocomotive loco = express(helper, 24, 2, tender);
        tender[0].setFuel(32000);
        Player driver = board(helper, loco);
        throttle(loco, 4);
        double tankTop = 0.6 * 20 * 3.6;
        helper.succeedWhen(() -> {
            helper.assertTrue(loco.speedKmh() > tankTop + 10, "it should get well past the tank engine's " + tankTop + " km/h, at " + loco.speedKmh());
            driver.stopRiding();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void twoEnginesPullTogether(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
        TestTracks.line(level, at(helper, 0, 1), TrackAxis.X, 64);
        TestTracks.line(level, at(helper, 0, 7), TrackAxis.X, 64);
        // On one line, an engine with four coaches; on the other the same, and a second engine at the back, facing back.
        SteamLocomotive single = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 36, 1), EAST);
        SteamLocomotive front = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 36, 7), EAST);
        for (int i = 0; i < 4; i++) {
            PassengerCoach a = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), at(helper, 29 - 7 * i, 1), EAST);
            PassengerCoach b = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), at(helper, 29 - 7 * i, 7), EAST);
            helper.assertTrue(Trains.couple(i == 0 ? single : Trains.train(single).getLast(), a) == Trains.CoupleResult.COUPLED, "coach " + i + " should couple");
            helper.assertTrue(Trains.couple(i == 0 ? front : Trains.train(front).getLast(), b) == Trains.CoupleResult.COUPLED, "coach " + i + " should couple");
        }
        SteamLocomotive banker = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 2, 7), WEST);
        helper.assertTrue(Trains.couple(Trains.train(front).getLast(), banker) == Trains.CoupleResult.COUPLED, "a second engine should couple on");
        for (SteamLocomotive loco : List.of(single, front, banker)) {
            loco.setFuel(16000);
        }
        Player a = board(helper, single);
        Player b = board(helper, front);
        throttle(single, 4);
        throttle(front, 4);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(banker.isSteaming(), "the second engine should be working too, with nobody in it");
            helper.assertTrue(banker.workingThrottle() == -4, "facing back, it should work in full reverse, at " + banker.workingThrottle());
            double one = Math.abs(single.trainSpeed());
            double two = Math.abs(front.trainSpeed());
            helper.assertTrue(two > one * 1.3, "two engines should get it going faster: " + two + " against " + one);
            helper.assertTrue(front.getX() > single.getX() + 0.5, "and so be further along");
            a.stopRiding();
            b.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 800)
    public static void doubleHeadedTrainRunsATimetable(GameTestHelper helper) {
        line(helper, 4);
        StationBlockEntity station = TestTracks.station(helper.getLevel(), helper.absolutePos(new BlockPos(52, 1, 7)), at(helper, 52, 4), "End", 5);
        SteamLocomotive pilot = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 20, 4), EAST);
        Tender[] tender = new Tender[1];
        ExpressLocomotive train = express(helper, 13, 4, tender);
        helper.assertTrue(Trains.couple(pilot, train) == Trains.CoupleResult.COUPLED, "the pilot engine should couple on the front");
        pilot.setFuel(16000);
        tender[0].setFuel(16000);
        train.autopilot().start(List.of(TestTracks.stop(station)));
        boolean[] helped = {false};
        helper.onEachTick(() -> helped[0] |= pilot.isSteaming() && pilot.helping() == train);
        helper.succeedWhen(() -> {
            helper.assertTrue(train.autopilot().isDwelling(), "the train should have reached the station");
            helper.assertTrue(helped[0], "the pilot engine should have worked with the express on its timetable");
            helper.assertTrue(!pilot.autopilot().isOn(), "only the express runs the timetable");
        });
    }

    @GameTest(template = LINE, timeoutTicks = 800)
    public static void expressRunsATimetable(GameTestHelper helper) {
        line(helper, 4);
        StationBlockEntity station = TestTracks.station(helper.getLevel(), helper.absolutePos(new BlockPos(56, 1, 7)), at(helper, 56, 4), "End", 5);
        Tender[] tender = new Tender[1];
        ExpressLocomotive loco = express(helper, 22, 4, tender);
        PassengerCoach coach = TestTracks.car(helper.getLevel(), ModEntities.PASSENGER_COACH.get(), at(helper, 7, 4), EAST);
        helper.assertTrue(Trains.couple(tender[0], coach) == Trains.CoupleResult.COUPLED, "the coach should couple to the tender");
        tender[0].setFuel(16000);
        loco.autopilot().start(List.of(TestTracks.stop(station)));
        boolean[] steamed = {false};
        helper.onEachTick(() -> steamed[0] |= loco.isSteaming() && Math.abs(loco.trainSpeed()) > 0.1);
        helper.succeedWhen(() -> {
            helper.assertTrue(loco.autopilot().isDwelling(), "the express should have reached the station");
            helper.assertTrue(steamed[0], "under steam");
            helper.assertTrue(tender[0].fuel() < 16000, "burning the tender's coal");
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void loaderFillsTheTenderWithFuelOnly(GameTestHelper helper) {
        line(helper, 4);
        Tender[] tender = new Tender[1];
        express(helper, 30, 4, tender);
        WagonLoaderBlockEntity loader = TestTracks.wagonLoader(helper.getLevel(), helper.absolutePos(new BlockPos(22, 1, 6)), at(helper, 22, 4), LoaderMode.LOAD);
        BlockPos chestPos = loader.getBlockPos().east();
        helper.getLevel().setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(chestPos);
        chest.setItem(0, new ItemStack(Items.COBBLESTONE, 64));
        chest.setItem(1, new ItemStack(Items.COAL, 64));
        chest.setItem(2, new ItemStack(Items.COAL, 64));
        helper.succeedWhen(() -> {
            helper.assertTrue(tender[0].fuel() == 128 * 1600, "the tender should have taken all the coal, has " + tender[0].fuel() / 1600);
            helper.assertTrue(chest.getItem(0).getCount() == 64 && chest.getItem(1).isEmpty() && chest.getItem(2).isEmpty(),
                    "and left the cobblestone");
        });
    }
}
