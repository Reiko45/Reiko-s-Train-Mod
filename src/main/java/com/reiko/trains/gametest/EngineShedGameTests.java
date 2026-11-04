package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModBlocks;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.shed.EngineShedBlock;
import com.reiko.trains.shed.EngineShedBlockEntity;
import com.reiko.trains.shed.EngineShedItem;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.DieselLocomotive;
import com.reiko.trains.train.ElectricLocomotive;
import com.reiko.trains.train.Locomotive;
import com.reiko.trains.train.SteamLocomotive;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for engine shed doors and the topping up of engines in the shed. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class EngineShedGameTests {
    private static final String LINE = "line";
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private EngineShedGameTests() {
    }

    /** Track along x at z = 4 (y = 1), the whole 64 blocks, on a floor; shed doors over it at {@code doors}, the shed to the east. */
    private static EngineShedBlockEntity line(GameTestHelper helper, int doors) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
        TestTracks.line(level, at(helper, 0), TrackAxis.X, 64);
        return TestTracks.engineShed(level, at(helper, doors), Direction.WEST);
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

    private static boolean doorwayShut(GameTestHelper helper, int doors) {
        BlockState state = helper.getLevel().getBlockState(at(helper, doors).above(2));
        return state.is(ModBlocks.ENGINE_SHED.get()) && !state.getValue(EngineShedBlock.OPEN);
    }

    private static Fluid dieselFluid() {
        return BuiltInRegistries.FLUID.get(ResourceLocation.parse("buildcraftreborn:fuel_dense"));
    }

    @GameTest(template = LINE, timeoutTicks = 400)
    public static void doorsOpenForATrainAndShutBehindIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EngineShedBlockEntity shed = line(helper, 30);
        BlockPos track = at(helper, 30);
        helper.assertTrue(level.getBlockState(track.north(2).above(4)).is(ModBlocks.ENGINE_SHED.get()), "the frame should stand over the track");
        helper.assertTrue(level.getBlockState(track).is(ModBlocks.TRACK.get()), "with the track still under the doorway");
        helper.assertTrue(!shed.isOpen() && doorwayShut(helper, 30), "and the doors shut");
        helper.assertTrue(!level.getBlockState(track.above()).getCollisionShape(level, track.above()).isEmpty(), "shut, they stop people");
        SteamLocomotive loco = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 6), EAST);
        loco.setFuel(SteamLocomotive.FUEL_CAPACITY);
        Player driver = board(helper, loco);
        for (int i = 0; i < 3; i++) {
            loco.control(TrainControlPayload.THROTTLE_UP);
        }
        boolean[] openedInTime = {false};
        helper.onEachTick(() -> {
            if (shed.isOpen() && loco.getX() + loco.halfLength() < track.getX()) {
                openedInTime[0] = true;
            }
            if (loco.getX() > at(helper, 50).getX() && loco.getThrottle() > 0) {
                for (int i = 0; i < 3; i++) {
                    loco.control(TrainControlPayload.THROTTLE_DOWN);
                }
                loco.control(TrainControlPayload.BRAKE_ON);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(openedInTime[0], "the doors should have opened before the engine got to them");
            helper.assertTrue(loco.getX() > at(helper, 45).getX() && Math.abs(loco.trainSpeed()) < 0.001, "it should be in the shed, stopped");
            helper.assertTrue(!shed.isOpen() && doorwayShut(helper, 30), "with the doors shut behind it");
            driver.stopRiding();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void enginesInsideAreToppedUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EngineShedBlockEntity shed = line(helper, 12);
        IFluidHandler fluids = shed.fluids();
        helper.assertTrue(fluids.fill(new FluidStack(Fluids.WATER, 64_000), IFluidHandler.FluidAction.EXECUTE) == 64_000, "it should take 64 buckets of water");
        Fluid diesel = dieselFluid();
        helper.assertTrue(diesel != Fluids.EMPTY, "BuildCraft's dense fuel should be there");
        helper.assertTrue(fluids.fill(new FluidStack(diesel, 64_000), IFluidHandler.FluidAction.EXECUTE) == 64_000, "and 64 of diesel");
        ItemStack coal = new ItemStack(Items.COAL, 64);
        helper.assertTrue(shed.items().insertItem(0, coal, false).isEmpty(), "and coal");
        helper.assertTrue(!shed.items().insertItem(1, new ItemStack(Items.DIRT), true).isEmpty(), "but not dirt");
        shed.addMj(1_000_000);
        SteamLocomotive steam = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 20), EAST);
        DieselLocomotive dieselLoco = TestTracks.car(level, ModEntities.DIESEL_LOCOMOTIVE.get(), at(helper, 32), EAST);
        ElectricLocomotive electric = TestTracks.car(level, ModEntities.ELECTRIC_LOCOMOTIVE.get(), at(helper, 42), EAST);
        SteamLocomotive outside = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 5), EAST);
        steam.setWater(0);
        for (Locomotive loco : List.of(steam, dieselLoco, electric, outside)) {
            loco.setFuel(0);
        }
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(steam.fuel() > SteamLocomotive.FUEL_CAPACITY * 0.9, "the tank engine should have been coaled: " + steam.fuel());
            helper.assertTrue(steam.water() == SteamLocomotive.WATER_CAPACITY, "and watered: " + steam.water());
            helper.assertTrue(dieselLoco.fuel() > DieselLocomotive.FUEL_CAPACITY * 0.9, "the diesel filled up: " + dieselLoco.fuel());
            helper.assertTrue(electric.fuel() > 0, "the electric charging: " + electric.fuel());
            helper.assertTrue(outside.fuel() == 0, "and the engine outside left alone");
            helper.assertTrue(shed.fuelCount() < 64 && shed.water() < 64_000 && shed.diesel() < 64_000 && shed.storedMj() < 1_000_000,
                    "all out of the store");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 800)
    public static void timetabledTrainWaitsToBeToppedUp(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EngineShedBlockEntity shed = line(helper, 30);
        shed.fluids().fill(new FluidStack(Fluids.WATER, 64_000), IFluidHandler.FluidAction.EXECUTE);
        shed.items().insertItem(0, new ItemStack(Items.COAL, 64), false);
        StationBlockEntity inside = TestTracks.station(level, helper.absolutePos(new BlockPos(48, 1, 1)), at(helper, 48), "Shed", 1);
        StationBlockEntity yard = TestTracks.station(level, helper.absolutePos(new BlockPos(10, 1, 1)), at(helper, 10), "Yard", 1);
        SteamLocomotive loco = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 16), EAST);
        loco.setFuel(SteamLocomotive.FUEL_CAPACITY * 0.4);
        loco.setWater(2000);
        loco.autopilot().start(List.of(TestTracks.stop(inside), TestTracks.stop(yard)));
        boolean[] waited = {false};
        helper.onEachTick(() -> {
            if (loco.status().getString().contains("topping up")) {
                waited[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(waited[0], "it should have waited in the shed to be topped up");
            helper.assertTrue(loco.getX() < at(helper, 20).getX(), "then gone back out to the yard: " + loco.position());
            helper.assertTrue(loco.water() > SteamLocomotive.WATER_CAPACITY - 2000, "full of water: " + loco.water());
            loco.autopilot().stop();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 120)
    public static void clicksAndRedstoneHoldTheDoorsOpen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EngineShedBlockEntity shed = line(helper, 30);
        BlockPos post = at(helper, 30).north(2);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        level.getBlockState(post).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(post), Direction.WEST, post, false));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(shed.isOpen() && !doorwayShut(helper, 30), "a click should open the doors");
            helper.assertTrue(level.getBlockState(at(helper, 30).above()).getCollisionShape(level, at(helper, 30).above()).isEmpty(),
                    "and open, they let people through");
            level.getBlockState(post).useWithoutItem(level, player, new BlockHitResult(Vec3.atCenterOf(post), Direction.WEST, post, false));
        });
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(!shed.isOpen(), "another should let them shut");
            level.setBlockAndUpdate(post.north(), Blocks.REDSTONE_BLOCK.defaultBlockState());
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(shed.isOpen(), "a redstone signal should hold them open");
            level.setBlockAndUpdate(post.north(), Blocks.AIR.defaultBlockState());
        });
        helper.runAfterDelay(11, () -> {
            helper.assertTrue(!shed.isOpen(), "until it goes off");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 40)
    public static void breakingAnyOfItTakesItAllDown(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        EngineShedBlockEntity shed = line(helper, 30);
        shed.items().insertItem(0, new ItemStack(Items.COAL, 20), false);
        BlockPos track = at(helper, 30);
        helper.assertTrue(!EngineShedItem.fits(level, track, Direction.EAST), "there's no room for more doors in the same place");
        level.destroyBlock(track.south(2).above(1), true);
        for (int up = 0; up <= 4; up++) {
            for (int across = -2; across <= 2; across++) {
                BlockPos at = track.north(across).above(up);
                helper.assertTrue(!level.getBlockState(at).is(ModBlocks.ENGINE_SHED.get()), "all of it should come down: " + at.toShortString());
            }
        }
        helper.assertTrue(level.getBlockState(track).is(ModBlocks.TRACK.get()), "but not the track");
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(track).inflate(4));
        int doors = drops.stream().filter(e -> e.getItem().is(ModItems.ENGINE_SHED.get())).mapToInt(e -> e.getItem().getCount()).sum();
        int coal = drops.stream().filter(e -> e.getItem().is(Items.COAL)).mapToInt(e -> e.getItem().getCount()).sum();
        helper.assertTrue(doors == 1 && coal == 20, "giving back the doors and the coal in its store: " + doors + ", " + coal);
        helper.succeed();
    }
}
