package com.reiko.trains.gametest;

import buildcraftreborn.api.mj.IMjReadable;
import buildcraftreborn.api.mj.IMjReceiver;
import buildcraftreborn.api.mj.MjAPI;
import com.reiko.trains.ReikoTrains;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.power.ChargingPointBlock;
import com.reiko.trains.power.ChargingPointBlockEntity;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.DieselLocomotive;
import com.reiko.trains.train.ElectricLocomotive;
import com.reiko.trains.train.Locomotive;
import com.reiko.trains.train.PassengerCoach;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for the electric locomotive and the charging point. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class ElectricGameTests {
    private static final String LINE = "line";
    private static final String YARD = "yard";
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private ElectricGameTests() {
    }

    /** Track along x at z = 4 (y = 1), the whole 64 blocks, on a floor. */
    private static void line(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
        TestTracks.line(level, at(helper, 0), TrackAxis.X, 64);
    }

    private static BlockPos at(GameTestHelper helper, int x) {
        return helper.absolutePos(new BlockPos(x, 1, 4));
    }

    private static ElectricLocomotive electric(GameTestHelper helper, int x) {
        return TestTracks.car(helper.getLevel(), ModEntities.ELECTRIC_LOCOMOTIVE.get(), at(helper, x), EAST);
    }

    /** A charging point two blocks south of the track at {@code x}. */
    private static ChargingPointBlockEntity charger(GameTestHelper helper, int x) {
        return TestTracks.chargingPoint(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 6)), at(helper, x));
    }

    private static Player board(GameTestHelper helper, Locomotive loco) {
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(loco.position());
        driver.startRiding(loco);
        return driver;
    }

    private static void throttle(Locomotive loco, int notches) {
        for (int i = 0; i < notches; i++) {
            loco.control(TrainControlPayload.THROTTLE_UP);
        }
    }

    private static void shut(Locomotive loco) {
        for (int i = 0; i < Locomotive.MAX_THROTTLE; i++) {
            loco.control(TrainControlPayload.THROTTLE_DOWN);
        }
    }

    @GameTest(template = LINE, timeoutTicks = 300)
    public static void electricRunsOnItsBatteries(GameTestHelper helper) {
        line(helper);
        ElectricLocomotive electric = electric(helper, 12);
        Player stoker = helper.makeMockPlayer(GameType.SURVIVAL);
        stoker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 16));
        electric.interact(stoker, InteractionHand.MAIN_HAND);
        helper.assertTrue(electric.fuel() == 0 && stoker.getMainHandItem().getCount() == 16, "an electric shouldn't take coal");
        stoker.stopRiding();
        Player driver = board(helper, electric);
        throttle(electric, 4);
        double startX = electric.getX();
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(!electric.isRunning() && Math.abs(electric.getX() - startX) < 0.01, "with flat batteries it shouldn't go");
            electric.setFuel(ElectricLocomotive.BATTERY_CAPACITY);
            shut(electric);
        });
        double[] idleFrom = new double[1];
        helper.runAfterDelay(42, () -> idleFrom[0] = electric.fuel());
        helper.runAfterDelay(142, () -> {
            double idle = idleFrom[0] - electric.fuel();
            helper.assertTrue(electric.isRunning(), "charged, it should be switched on");
            helper.assertTrue(idle > 0 && idle < 100, "standing, it should use next to nothing: " + idle + " MJ in five seconds");
            throttle(electric, 4);
            idleFrom[0] = electric.fuel();
        });
        helper.runAfterDelay(242, () -> {
            double used = idleFrom[0] - electric.fuel();
            helper.assertTrue(electric.getX() > startX + 10, "at full power it should be well away");
            helper.assertTrue(used > 3000 && used < 5000, "using about 40 MJ a tick: " + used + " MJ in five seconds");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 340)
    public static void chargingPointChargesAStandingElectric(GameTestHelper helper) {
        line(helper);
        ChargingPointBlockEntity charger = charger(helper, 30);
        ElectricLocomotive electric = electric(helper, 30);
        PassengerCoach coach = TestTracks.car(helper.getLevel(), ModEntities.PASSENGER_COACH.get(), at(helper, 22), EAST);
        Trains.couple(electric, coach);
        charger.addMj(600_000);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(electric.fuel() > 0, "the electric should be charging");
            helper.assertTrue(Math.abs(charger.storedMj() + electric.fuel() - 600_000) < 2, "with the power coming out of the store");
            helper.assertTrue(charger.getBlockState().getValue(ChargingPointBlock.CHARGING), "and the lamp on top lit");
        });
        helper.runAfterDelay(200, () -> {
            helper.assertTrue(Math.abs(electric.fuel() - 600_000) < 2, "the whole store should have gone into it, has " + electric.fuel());
            helper.assertTrue(charger.storedMj() == 0 && charger.getBlockState().getValue(ChargingPointBlock.LEVEL) == 0, "leaving the store empty");
            helper.assertTrue(!charger.getBlockState().getValue(ChargingPointBlock.CHARGING), "and the lamp out");
            charger.addMj(1_000_000);
        });
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(electric.fuel() == ElectricLocomotive.BATTERY_CAPACITY, "topped up, its batteries should be full");
            helper.assertTrue(charger.storedMj() == 600_000, "taking only what they had room for, leaving " + charger.storedMj());
            helper.assertTrue(charger.getBlockState().getValue(ChargingPointBlock.LEVEL) == 3, "three bars should show");
            helper.assertTrue(charger.comparatorSignal() == 9, "and a comparator read 9, not " + charger.comparatorSignal());
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void chargingPointTakesBuildcraftPower(GameTestHelper helper) {
        line(helper);
        ChargingPointBlockEntity charger = charger(helper, 30);
        ServerLevel level = helper.getLevel();
        BlockPos pos = charger.getBlockPos();
        IMjReceiver receiver = level.getCapability(MjAPI.CAP_RECEIVER, pos, Direction.UP);
        helper.assertTrue(receiver != null && level.getCapability(MjAPI.CAP_CONNECTOR, pos, Direction.WEST) != null,
                "BuildCraft engines and pipes should be able to connect to it");
        long excess = receiver.receivePower(250_000 * MjAPI.MJ, false);
        helper.assertTrue(excess == 0 && charger.storedMj() == 250_000, "it should take 250,000 MJ, has " + charger.storedMj());
        excess = receiver.receivePower(1_000_000 * MjAPI.MJ, false);
        helper.assertTrue(excess == 250_000 * MjAPI.MJ && charger.storedMj() == ChargingPointBlockEntity.capacityMj(),
                "it should fill up and hand back the rest");
        IMjReadable gauge = level.getCapability(MjAPI.CAP_READABLE, pos, Direction.NORTH);
        helper.assertTrue(gauge != null && gauge.getStored() == gauge.getCapacity(), "a gauge should read it full");
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(charger.getBlockState().getValue(ChargingPointBlock.LEVEL) == 4, "all four bars should light");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void chargingPointLeavesMovingTrainsAndPausesForRedstone(GameTestHelper helper) {
        line(helper);
        ChargingPointBlockEntity charger = charger(helper, 30);
        charger.addMj(500_000);
        // Someone's at the controls of the passing one, so its handbrake's off and it rolls on by.
        ElectricLocomotive passing = electric(helper, 18);
        Player driver = board(helper, passing);
        passing.shove(0.6);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(passing.getX() > charger.node().getX() + 5, "the electric should have run past, it's at "
                    + (passing.getX() - charger.node().getX()));
            helper.assertTrue(passing.fuel() == 0, "without being charged on the move");
            driver.stopRiding();
            passing.discard();
            helper.getLevel().setBlock(charger.getBlockPos().east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        });
        ElectricLocomotive[] standing = new ElectricLocomotive[1];
        helper.runAfterDelay(32, () -> standing[0] = electric(helper, 30));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(standing[0].fuel() == 0, "redstone should hold the charging point off");
            helper.getLevel().setBlock(charger.getBlockPos().east(), Blocks.AIR.defaultBlockState(), 3);
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(standing[0].fuel() > 0, "with the redstone gone, it should charge");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 1200)
    public static void timetabledElectricWaitsToCharge(GameTestHelper helper) {
        line(helper);
        ServerLevel level = helper.getLevel();
        StationBlockEntity depot = TestTracks.station(level, helper.absolutePos(new BlockPos(30, 1, 7)), at(helper, 30), "Depot", 1);
        StationBlockEntity terminus = TestTracks.station(level, helper.absolutePos(new BlockPos(56, 1, 7)), at(helper, 56), "Terminus", 2);
        ChargingPointBlockEntity charger = TestTracks.chargingPoint(level, helper.absolutePos(new BlockPos(30, 1, 1)), at(helper, 30));
        charger.addMj(1_000_000);
        ElectricLocomotive electric = electric(helper, 10);
        electric.setFuel(ElectricLocomotive.BATTERY_CAPACITY * 0.1);
        electric.autopilot().start(List.of(TestTracks.stop(depot), TestTracks.stop(terminus)));
        boolean[] said = {false};
        helper.onEachTick(() -> said[0] |= electric.status().getString().contains("charging"));
        double depotX = depot.node().getX() + 0.5;
        helper.succeedWhen(() -> {
            helper.assertTrue(electric.getX() > depotX + 8, "the train should have left the depot for the terminus");
            helper.assertTrue(electric.getFuelPercent() >= 98, "it should have waited to charge up, has " + electric.getFuelPercent() + "%");
            helper.assertTrue(said[0], "the cab should have said it was charging");
        });
    }

    @GameTest(template = YARD, timeoutTicks = 200)
    public static void electricGetsAwayQuickerThanTheDiesel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        BlockPos[] lines = {helper.absolutePos(new BlockPos(0, 1, 10)), helper.absolutePos(new BlockPos(0, 1, 30))};
        for (BlockPos start : lines) {
            TestTracks.line(level, start, TrackAxis.X, 48);
        }
        ElectricLocomotive electric = TestTracks.car(level, ModEntities.ELECTRIC_LOCOMOTIVE.get(), lines[0].offset(22, 0, 0), EAST);
        DieselLocomotive diesel = TestTracks.car(level, ModEntities.DIESEL_LOCOMOTIVE.get(), lines[1].offset(22, 0, 0), EAST);
        Locomotive[] locos = {electric, diesel};
        for (int i = 0; i < 2; i++) {
            PassengerCoach first = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), lines[i].offset(14, 0, 0), EAST);
            PassengerCoach second = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), lines[i].offset(7, 0, 0), EAST);
            Trains.couple(first, second);
            Trains.couple(locos[i], first);
        }
        electric.setFuel(ElectricLocomotive.BATTERY_CAPACITY);
        diesel.setFuel(DieselLocomotive.FUEL_CAPACITY);
        Player[] drivers = {board(helper, electric), board(helper, diesel)};
        throttle(electric, 4);
        throttle(diesel, 4);
        double[] start = {electric.getX(), diesel.getX()};
        helper.runAfterDelay(80, () -> {
            double e = electric.getX() - start[0];
            double d = diesel.getX() - start[1];
            helper.assertTrue(e > d + 0.5 && electric.trainSpeed() > diesel.trainSpeed(),
                    "pulling the same, the electric should have got away quicker than the diesel: " + e + " blocks against " + d);
            helper.assertTrue(electric.maxSpeed() > diesel.maxSpeed(), "and be faster flat out");
            helper.assertTrue(level.getRecipeManager().byKey(ReikoTrains.id("electric_locomotive")).isPresent()
                    && level.getRecipeManager().byKey(ReikoTrains.id("charging_point")).isPresent(), "both should have recipes");
            for (Player driver : drivers) {
                driver.stopRiding();
            }
            helper.succeed();
        });
    }
}
