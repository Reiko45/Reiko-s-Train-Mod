package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.registry.ModDataComponents;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.signal.SensorDirection;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.signal.TrackSensorBlock;
import com.reiko.trains.signal.TrackSensorBlockEntity;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.PassengerCoach;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.TankWagon;
import com.reiko.trains.train.Timetable;
import com.reiko.trains.train.TrainCarEntity;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Game tests for track sensors. The track runs along x at z = 4; the sensor stands two blocks south of it at x = 30,
 * looking north at it, so trains going east pass it left to right (as you stand behind it), and trains going west
 * right to left.
 */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class SensorGameTests {
    private static final String LINE = "line";
    private static final String YARD = "yard";
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final int SENSOR_X = 30;

    private SensorGameTests() {
    }

    private static void line(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
        TestTracks.line(level, at(helper, 0), TrackAxis.X, 64);
    }

    private static BlockPos at(GameTestHelper helper, int x) {
        return helper.absolutePos(new BlockPos(x, 1, 4));
    }

    private static TrackSensorBlockEntity sensor(GameTestHelper helper) {
        return TestTracks.trackSensor(helper.getLevel(), helper.absolutePos(new BlockPos(SENSOR_X, 1, 6)));
    }

    private static boolean powered(TrackSensorBlockEntity sensor) {
        return sensor.getBlockState().getValue(TrackSensorBlock.POWERED);
    }

    private static <T extends TrainCarEntity> T car(GameTestHelper helper, net.minecraft.world.entity.EntityType<T> type, int x) {
        return TestTracks.car(helper.getLevel(), type, at(helper, x), EAST);
    }

    /** Clicks the sensor, empty-handed (sneaking or not) or with an item. */
    private static void click(GameTestHelper helper, TrackSensorBlockEntity sensor, ItemStack held, boolean sneaking) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);
        player.setShiftKeyDown(sneaking);
        BlockPos pos = sensor.getBlockPos();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.SOUTH, pos, false);
        var state = helper.getLevel().getBlockState(pos);
        if (held.isEmpty()) {
            state.useWithoutItem(helper.getLevel(), player, hit);
        } else {
            state.useItemOn(held, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        }
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void sensorSignalsWhileATrainPasses(GameTestHelper helper) {
        line(helper);
        TrackSensorBlockEntity sensor = sensor(helper);
        PassengerCoach coach = car(helper, ModEntities.PASSENGER_COACH.get(), 10);
        double sensorX = sensor.node().getX() + 0.5;
        int[] onFor = {0};
        boolean[] early = {false};
        helper.onEachTick(() -> {
            if (powered(sensor)) {
                onFor[0]++;
                // Only while some of the coach is alongside it.
                early[0] |= Math.abs(coach.getX() - sensorX) > coach.halfLength() + 0.6;
            }
        });
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(!powered(sensor), "with no train over it, it should be off");
            coach.shove(0.3);
        });
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(coach.getX() > sensorX + 10, "the coach should be well past");
            helper.assertTrue(onFor[0] >= 15, "it should have been on while the coach went over it, " + onFor[0] + " ticks");
            helper.assertTrue(!early[0], "and only while the coach was over it");
            helper.assertTrue(!powered(sensor), "with it gone, off again");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void sensorStaysOnUnderAStandingTrainAndCountsItsCars(GameTestHelper helper) {
        line(helper);
        TrackSensorBlockEntity sensor = sensor(helper);
        ServerLevel level = helper.getLevel();
        FreightWagon first = car(helper, ModEntities.FREIGHT_WAGON.get(), SENSOR_X);
        FreightWagon second = car(helper, ModEntities.FREIGHT_WAGON.get(), SENSOR_X - 6);
        FreightWagon third = car(helper, ModEntities.FREIGHT_WAGON.get(), SENSOR_X - 12);
        Trains.couple(second, third);
        Trains.couple(first, second);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(powered(sensor), "a train standing over it should keep it on");
            helper.assertTrue(level.hasNeighborSignal(sensor.getBlockPos().west()), "giving off a redstone signal");
            int read = level.getBlockState(sensor.getBlockPos()).getAnalogOutputSignal(level, sensor.getBlockPos());
            helper.assertTrue(read == 3, "a comparator should count its three cars, not " + read);
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 360)
    public static void sensorSensesOnlyTrainsGoingItsWay(GameTestHelper helper) {
        line(helper);
        TrackSensorBlockEntity sensor = sensor(helper);
        click(helper, sensor, ItemStack.EMPTY, false);
        click(helper, sensor, ItemStack.EMPTY, false);
        helper.assertTrue(sensor.getBlockState().getValue(TrackSensorBlock.DIRECTION) == SensorDirection.RIGHT,
                "two clicks should set it to sense trains going right (east, from behind it)");
        boolean[] on = {false};
        helper.onEachTick(() -> on[0] |= powered(sensor));
        // West first: the coach faces east, so a shove backwards sends it west.
        PassengerCoach westbound = car(helper, ModEntities.PASSENGER_COACH.get(), 52);
        westbound.shove(-0.3);
        double sensorX = sensor.node().getX() + 0.5;
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(westbound.getX() < sensorX - 10, "the westbound coach should be well past");
            helper.assertTrue(!on[0], "a train going left shouldn't set it off");
            westbound.discard();
        });
        PassengerCoach[] eastbound = new PassengerCoach[1];
        helper.runAfterDelay(142, () -> {
            eastbound[0] = car(helper, ModEntities.PASSENGER_COACH.get(), 8);
            eastbound[0].shove(0.3);
        });
        helper.runAfterDelay(300, () -> {
            helper.assertTrue(eastbound[0].getX() > sensorX + 10, "the eastbound coach should be well past");
            helper.assertTrue(on[0], "a train going right should have set it off");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void sensorFiltersByCarAndCargo(GameTestHelper helper) {
        line(helper);
        TrackSensorBlockEntity sensor = sensor(helper);
        click(helper, sensor, new ItemStack(ModItems.DIESEL_LOCOMOTIVE.get()), false);
        helper.assertTrue(sensor.filter().is(ModItems.DIESEL_LOCOMOTIVE.get()), "clicking with a diesel should make it look for diesels");
        FreightWagon wagon = car(helper, ModEntities.FREIGHT_WAGON.get(), SENSOR_X);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(!powered(sensor), "a wagon on its own isn't a train with a diesel");
            click(helper, sensor, new ItemStack(Items.COAL), false);
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(!powered(sensor), "looking for coal, an empty wagon shouldn't set it off");
            wagon.setItem(40, new ItemStack(Items.COAL, 3));
        });
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(powered(sensor), "a wagon with coal in it should");
            wagon.discard();
            TestTracks.car(helper.getLevel(), ModEntities.TANK_WAGON.get(), at(helper, SENSOR_X), EAST);
            click(helper, sensor, new ItemStack(Items.WATER_BUCKET), false);
        });
        helper.runAfterDelay(16, () -> {
            helper.assertTrue(!powered(sensor), "looking for water, an empty tank wagon shouldn't set it off");
            TankWagon tank = helper.getLevel().getEntitiesOfClass(TankWagon.class, new net.minecraft.world.phys.AABB(sensor.node()).inflate(4)).getFirst();
            tank.tank().fill(new FluidStack(Fluids.WATER, 5000), IFluidHandler.FluidAction.EXECUTE);
        });
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(powered(sensor), "one with water in it should");
            click(helper, sensor, ItemStack.EMPTY, true);
            helper.assertTrue(sensor.filter().isEmpty(), "sneak-clicking should clear the filter");
        });
        helper.runAfterDelay(24, () -> {
            helper.assertTrue(powered(sensor), "and then it senses every train");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void sensorFiltersByTimetable(GameTestHelper helper) {
        line(helper);
        ServerLevel level = helper.getLevel();
        StationBlockEntity a = TestTracks.station(level, helper.absolutePos(new BlockPos(10, 1, 7)), at(helper, 10), "Ashby", 5);
        StationBlockEntity b = TestTracks.station(level, helper.absolutePos(new BlockPos(55, 1, 7)), at(helper, 55), "Brook", 5);
        List<Timetable.Stop> ab = List.of(TestTracks.stop(a), TestTracks.stop(b));
        ItemStack timetable = new ItemStack(ModItems.TIMETABLE.get());
        timetable.set(ModDataComponents.TIMETABLE.get(), ab);
        TrackSensorBlockEntity sensor = sensor(helper);
        click(helper, sensor, timetable, false);
        // No fire, so it stays where it is over the sensor, timetable or not.
        SteamLocomotive loco = car(helper, ModEntities.STEAM_LOCOMOTIVE.get(), SENSOR_X);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(!powered(sensor), "a train with no timetable shouldn't set it off");
            loco.autopilot().start(List.of(TestTracks.stop(a)));
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(!powered(sensor), "nor one running another timetable");
            loco.autopilot().start(List.of(TestTracks.stop(b), TestTracks.stop(a)));
        });
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(powered(sensor), "one running the same stations round, from either end, should");
            click(helper, sensor, new ItemStack(ModItems.TIMETABLE.get()), false);
        });
        helper.runAfterDelay(16, () -> {
            helper.assertTrue(powered(sensor), "a blank timetable should pick out any train on a timetable");
            loco.autopilot().stop();
        });
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(!powered(sensor), "and not one that's stopped running it");
            helper.succeed();
        });
    }

    /**
     * A sensor at a junction, filtered for freight wagons and wired to the switch stand beside it: a goods train takes
     * the branch, any other goes straight on. The main line runs along x (z = 10) with the branch curving off south at
     * x = 24; the stand is two blocks north of the junction and the sensor right next to it.
     */
    private static void runAtJunction(GameTestHelper helper, boolean goods) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        BlockPos west = helper.absolutePos(new BlockPos(2, 1, 10));
        TestTracks.line(level, west, TrackAxis.X, 44);
        BlockPos junction = west.offset(22, 0, 0);
        var branch = TestTracks.connect(level, junction, junction.offset(12, 0, 5));
        TestTracks.line(level, branch.end().offset(1, 0, 1), TrackAxis.SE, 10);
        TestTracks.switchStand(level, junction.offset(0, 0, -2));
        TrackSensorBlockEntity sensor = TestTracks.trackSensor(level, junction.offset(-1, 0, -2));
        click(helper, sensor, new ItemStack(ModItems.FREIGHT_WAGON.get()), false);
        SteamLocomotive loco = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), west.offset(10, 0, 0), EAST);
        TrainCarEntity behind = goods ? TestTracks.car(level, ModEntities.FREIGHT_WAGON.get(), west.offset(4, 0, 0), EAST)
                : TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), west.offset(3, 0, 0), EAST);
        helper.assertTrue(Trains.couple(loco, behind) == Trains.CoupleResult.COUPLED, "the train should couple up");
        loco.setFuel(16000);
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(loco.position());
        driver.startRiding(loco);
        for (int i = 0; i < 2; i++) {
            loco.control(TrainControlPayload.THROTTLE_UP);
        }
        helper.runAfterDelay(160, () -> {
            helper.assertTrue(loco.getX() > junction.getX() + 6, "the train should be well past the junction");
            if (goods) {
                helper.assertTrue(loco.getZ() > west.getZ() + 2, "a goods train should have been sent down the branch");
            } else {
                helper.assertTrue(Math.abs(loco.getZ() - (west.getZ() + 0.5)) < 0.1, "any other train should have gone straight on");
            }
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 200)
    public static void sensorSendsGoodsTrainsDownTheBranch(GameTestHelper helper) {
        runAtJunction(helper, true);
    }

    @GameTest(template = YARD, timeoutTicks = 200)
    public static void sensorLetsOtherTrainsGoStraightOn(GameTestHelper helper) {
        runAtJunction(helper, false);
    }
}
