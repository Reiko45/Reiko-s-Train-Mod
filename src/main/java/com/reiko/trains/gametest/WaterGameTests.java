package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.ExpressLocomotive;
import com.reiko.trains.train.Locomotive;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.Tender;
import com.reiko.trains.train.Trains;
import com.reiko.trains.water.WaterTowerBlock;
import com.reiko.trains.water.WaterTowerBlockEntity;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for steam engines' water and the water tower. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class WaterGameTests {
    private static final String LINE = "line";
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private WaterGameTests() {
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

    /** A water tower two blocks south of the track at {@code x}. */
    private static WaterTowerBlockEntity tower(GameTestHelper helper, int x) {
        return TestTracks.waterTower(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, 6)), at(helper, x));
    }

    /** An express at {@code x}, facing east, and its tender coupled behind it. */
    private static ExpressLocomotive express(GameTestHelper helper, int x) {
        ServerLevel level = helper.getLevel();
        ExpressLocomotive express = TestTracks.car(level, ModEntities.EXPRESS_LOCOMOTIVE.get(), at(helper, x), EAST);
        Tender tender = TestTracks.car(level, ModEntities.TENDER.get(), at(helper, x - 8), EAST);
        helper.assertTrue(Trains.couple(express, tender) == Trains.CoupleResult.COUPLED, "the tender should couple");
        return express;
    }

    @GameTest(template = LINE, timeoutTicks = 260)
    public static void steamEnginesComeFullAndUseWaterAsFastAsCoal(GameTestHelper helper) {
        line(helper);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 8), EAST);
        helper.assertTrue(loco.water() == SteamLocomotive.WATER_CAPACITY, "a new tank engine should come with its tanks full");
        loco.setFuel(SteamLocomotive.FUEL_CAPACITY);
        Player driver = board(helper, loco);
        throttle(loco, 2);
        helper.runAfterDelay(200, () -> {
            double coal = 1 - loco.fuel() / SteamLocomotive.FUEL_CAPACITY;
            double water = 1 - loco.water() / SteamLocomotive.WATER_CAPACITY;
            helper.assertTrue(coal > 0.001, "it should have burnt some coal");
            helper.assertTrue(Math.abs(coal - water) < 1.0E-6, "and used as much of its water: coal " + coal + ", water " + water);
            helper.assertTrue(loco.waterPercent() > 0 && loco.waterPercent() <= 100, "with its water on the gauges");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void dryEngineWontGoUntilItsFilled(GameTestHelper helper) {
        line(helper);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 8), EAST);
        loco.setFuel(SteamLocomotive.FUEL_CAPACITY);
        loco.setWater(0);
        Player driver = board(helper, loco);
        throttle(loco, 4);
        double startX = loco.getX();
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(!loco.isRunning() && Math.abs(loco.getX() - startX) < 0.01, "with no water it shouldn't go");
            helper.assertTrue(loco.waterPercent() == 0, "and the gauges should say so");
            Player fireman = helper.makeMockPlayer(GameType.SURVIVAL);
            fireman.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            loco.interact(fireman, InteractionHand.MAIN_HAND);
            helper.assertTrue(loco.water() == 1000, "a bucket of water should go in, has " + loco.water());
            helper.assertTrue(fireman.getMainHandItem().is(Items.BUCKET), "leaving the bucket empty");
        });
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(loco.getX() > startX + 2, "with water in it should go");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void expressTakesItsWaterFromItsTender(GameTestHelper helper) {
        line(helper);
        ExpressLocomotive express = express(helper, 30);
        Tender tender = express.tender();
        helper.assertTrue(tender.water() == Tender.WATER_CAPACITY, "a new tender should come full");
        tender.setFuel(Tender.FUEL_CAPACITY);
        tender.setWater(0);
        Player fireman = helper.makeMockPlayer(GameType.SURVIVAL);
        fireman.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        express.interact(fireman, InteractionHand.MAIN_HAND);
        helper.assertTrue(tender.water() == 1000 && express.water() == 0, "water poured into the engine should go into its tender");
        IFluidHandler pipe = tender.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ENTITY, null);
        helper.assertTrue(pipe != null && pipe.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 0,
                "it should take water only");
        helper.assertTrue(pipe.fill(new FluidStack(Fluids.WATER, 100_000), IFluidHandler.FluidAction.EXECUTE) == 63_000,
                "and only as much as fits");
        tender.setWater(0);
        Player driver = board(helper, express);
        throttle(express, 4);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(!express.isRunning() && express.waterPercent() == 0, "with its tender dry, it shouldn't go");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 400)
    public static void waterTowerFillsAStandingTrain(GameTestHelper helper) {
        line(helper);
        WaterTowerBlockEntity tower = tower(helper, 22);
        BlockPos top = tower.getBlockPos().above();
        helper.assertTrue(helper.getLevel().getBlockState(top).getBlock() instanceof WaterTowerBlock
                && helper.getLevel().getBlockState(top).getValue(WaterTowerBlock.HALF) == DoubleBlockHalf.UPPER, "it should be two blocks tall");
        helper.assertTrue(tower.addWater(100_000) == 100_000, "it should take water");
        ExpressLocomotive express = express(helper, 30);
        Tender tender = express.tender();
        tender.setWater(0);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(tender.water() > 0 && Math.abs(tender.water() + tower.stored() - 100_000) < 1,
                    "the tender should be filling from the tower: " + tender.water());
            helper.assertTrue(helper.getLevel().getBlockState(top).getValue(WaterTowerBlock.FILLING), "with its spout down");
        });
        helper.runAfterDelay(240, () -> {
            helper.assertTrue(tender.water() == Tender.WATER_CAPACITY && tower.stored() == 100_000 - Tender.WATER_CAPACITY,
                    "it should fill the tender and no more, leaving " + tower.stored());
            BlockState state = helper.getLevel().getBlockState(top);
            helper.assertTrue(!state.getValue(WaterTowerBlock.FILLING) && state.getValue(WaterTowerBlock.LEVEL) == 1,
                    "spout up again, one mark lit on its gauge");
            helper.assertTrue(tower.comparatorSignal() == 2, "a comparator reads how full it is: " + tower.comparatorSignal());
            helper.succeed();
        });
    }

    @GameTest(template = LINE)
    public static void waterTowerTakesWaterFromBucketsAndPipes(GameTestHelper helper) {
        line(helper);
        ServerLevel level = helper.getLevel();
        WaterTowerBlockEntity tower = tower(helper, 22);
        BlockPos pos = tower.getBlockPos();
        IFluidHandler pipe = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.BLOCK, pos, Direction.UP);
        helper.assertTrue(pipe != null && pipe.fill(new FluidStack(Fluids.LAVA, 1000), IFluidHandler.FluidAction.EXECUTE) == 0,
                "pipes shouldn't fill it with lava");
        helper.assertTrue(pipe.fill(new FluidStack(Fluids.WATER, 5000), IFluidHandler.FluidAction.EXECUTE) == 5000, "but water, yes");
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
        BlockPos top = pos.above();
        level.getBlockState(top).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(top), Direction.NORTH, top, false));
        helper.assertTrue(tower.stored() == 6000 && player.getMainHandItem().is(Items.BUCKET), "a bucket emptied into its tank");
        level.getBlockState(pos).useItemOn(player.getMainHandItem(), level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.NORTH, pos, false));
        helper.assertTrue(tower.stored() == 5000 && player.getMainHandItem().is(Items.WATER_BUCKET), "and an empty one fills from it");
        // Breaking either block takes the other with it, and it drops once.
        level.destroyBlock(top, true);
        helper.assertTrue(level.getBlockState(pos).isAir() && level.getBlockState(top).isAir(), "breaking the tank should take the legs too");
        int items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(3))
                .stream().filter(e -> e.getItem().is(com.reiko.trains.registry.ModItems.WATER_TOWER.get())).mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(items == 1, "dropping one water tower, not " + items);
        helper.succeed();
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void waterTowerLeavesMovingTrainsAndPausesForRedstone(GameTestHelper helper) {
        line(helper);
        ServerLevel level = helper.getLevel();
        WaterTowerBlockEntity tower = tower(helper, 22);
        tower.addWater(100_000);
        level.setBlock(tower.getBlockPos().east(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        ExpressLocomotive standing = express(helper, 26);
        Tender standingTender = standing.tender();
        standingTender.setWater(0);
        SteamLocomotive passing = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 2), EAST);
        passing.setFuel(SteamLocomotive.FUEL_CAPACITY);
        passing.setWater(8000);
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(standingTender.water() == 0 && tower.stored() == 100_000, "held by redstone, it shouldn't fill anything");
            level.setBlock(tower.getBlockPos().east(), Blocks.AIR.defaultBlockState(), 3);
            standing.discard();
            standingTender.discard();
            driver.moveTo(passing.position());
            driver.startRiding(passing);
            throttle(passing, 4);
        });
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(passing.getX() > tower.getBlockPos().getX() + 4, "the moving engine should have gone by");
            helper.assertTrue(passing.water() < 8000 && tower.stored() == 100_000, "without the tower filling it on the move");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 600)
    public static void timetabledTrainWaitsWhileItTakesWater(GameTestHelper helper) {
        line(helper);
        ServerLevel level = helper.getLevel();
        StationBlockEntity halt = TestTracks.station(level, helper.absolutePos(new BlockPos(34, 1, 1)), at(helper, 34), "Tank Halt", 1);
        StationBlockEntity end = TestTracks.station(level, helper.absolutePos(new BlockPos(60, 1, 1)), at(helper, 60), "Far End", 1);
        WaterTowerBlockEntity tower = tower(helper, 28);
        tower.addWater(200_000);
        ExpressLocomotive express = express(helper, 12);
        Tender tender = express.tender();
        tender.setFuel(Tender.FUEL_CAPACITY);
        tender.setWater(4000);
        express.autopilot().start(List.of(TestTracks.stop(halt), TestTracks.stop(end)));
        boolean[] waited = {false};
        helper.onEachTick(() -> {
            if (express.status().getString().contains("water")) {
                waited[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(waited[0], "it should have waited at the halt, taking water");
            helper.assertTrue(express.getX() > at(helper, 40).getX(), "then gone on");
            helper.assertTrue(tender.water() > Tender.WATER_CAPACITY - 2000, "with its tender full: " + tender.water());
            express.autopilot().stop();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 120, batch = "water_off")
    public static void serversCanTurnWaterOff(GameTestHelper helper) {
        line(helper);
        TrainsConfig.STEAM_NEEDS_WATER.set(false);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 8), EAST);
        loco.setFuel(SteamLocomotive.FUEL_CAPACITY);
        loco.setWater(0);
        Player driver = board(helper, loco);
        throttle(loco, 4);
        double startX = loco.getX();
        helper.runAfterDelay(80, () -> {
            boolean went = loco.getX() > startX + 2;
            int gauge = loco.waterPercent();
            TrainsConfig.STEAM_NEEDS_WATER.set(true);
            driver.stopRiding();
            helper.assertTrue(went, "with water turned off, a dry engine should still go");
            helper.assertTrue(gauge == -1, "and there's no water gauge");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void tankWagonOfWaterKeepsTheEngineTopped(GameTestHelper helper) {
        line(helper);
        ServerLevel level = helper.getLevel();
        SteamLocomotive loco = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 30), EAST);
        com.reiko.trains.train.TankWagon tank = TestTracks.car(level, ModEntities.TANK_WAGON.get(), at(helper, 23), EAST);
        helper.assertTrue(Trains.couple(loco, tank) == Trains.CoupleResult.COUPLED, "the tank wagon should couple");
        loco.setFuel(SteamLocomotive.FUEL_CAPACITY);
        loco.setWater(1000);
        tank.tank().fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 50_000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        Player driver = board(helper, loco);
        throttle(loco, 2);
        helper.succeedWhen(() -> {
            helper.assertTrue(loco.water() >= SteamLocomotive.WATER_CAPACITY - 100, "the engine should be topped up from the tank wagon: " + loco.water());
            helper.assertTrue(tank.tank().getFluidAmount() < 36_000, "out of the wagon's water");
            helper.assertTrue(loco.getX() > at(helper, 31).getX(), "on the move");
            driver.stopRiding();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void tankWagonOfLavaDoesntWaterEngines(GameTestHelper helper) {
        line(helper);
        ServerLevel level = helper.getLevel();
        SteamLocomotive loco = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 30), EAST);
        com.reiko.trains.train.TankWagon tank = TestTracks.car(level, ModEntities.TANK_WAGON.get(), at(helper, 23), EAST);
        Trains.couple(loco, tank);
        loco.setWater(1000);
        tank.tank().fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 50_000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(loco.water() == 1000 && tank.tank().getFluidAmount() == 50_000, "lava isn't water");
            helper.succeed();
        });
    }
}
