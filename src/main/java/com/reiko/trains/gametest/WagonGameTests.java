package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.freight.LoaderMode;
import com.reiko.trains.freight.WagonLoaderBlockEntity;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.GuardsVan;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.TankWagon;
import com.reiko.trains.train.Trains;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for the tank wagon and the guard's van. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class WagonGameTests {
    private static final String LINE = "line";
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private WagonGameTests() {
    }

    /** Track along x at z = {@code z} (y = 1), the whole 64 blocks, on a floor. */
    private static void line(GameTestHelper helper, int... zs) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
        for (int z : zs) {
            TestTracks.line(level, at(helper, 0, z), TrackAxis.X, 64);
        }
    }

    private static BlockPos at(GameTestHelper helper, int x, int z) {
        return helper.absolutePos(new BlockPos(x, 1, z));
    }

    /** A BuildCraft tank at {@code pos}, with {@code buckets} of {@code fluid} in it. */
    private static IFluidHandler bcTank(GameTestHelper helper, BlockPos pos, FluidStack fluid) {
        helper.getLevel().setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("buildcraftreborn:tank")).defaultBlockState(), 3);
        IFluidHandler tank = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        helper.assertTrue(tank != null, "a BuildCraft tank should hold fluids");
        if (!fluid.isEmpty()) {
            helper.assertTrue(tank.fill(fluid, IFluidHandler.FluidAction.EXECUTE) == fluid.getAmount(), "the tank should take " + fluid.getAmount() + " mB");
        }
        return tank;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Tank wagon

    @GameTest(template = LINE)
    public static void tankWagonFillsAndEmptiesWithBuckets(GameTestHelper helper) {
        line(helper, 4);
        TankWagon wagon = TestTracks.car(helper.getLevel(), ModEntities.TANK_WAGON.get(), at(helper, 20, 4), EAST);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < 3; i++) {
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
            wagon.interact(player, InteractionHand.MAIN_HAND);
        }
        helper.assertTrue(wagon.tank().getFluidAmount() == 3000 && wagon.tank().getFluid().is(Fluids.WATER), "three buckets of water should go in");
        helper.assertTrue(wagon.renderLoad() == 1, "the gauge should show a quarter, not " + wagon.renderLoad());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
        wagon.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(wagon.tank().getFluidAmount() == 3000 && player.getMainHandItem().is(Items.LAVA_BUCKET), "lava shouldn't mix in with the water");
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
        wagon.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(wagon.tank().getFluidAmount() == 2000 && player.getMainHandItem().is(Items.WATER_BUCKET), "an empty bucket should draw one off");
        double light = wagon.mass();
        wagon.tank().fill(new FluidStack(Fluids.WATER, 62_000), IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(wagon.tank().getFluidAmount() == TankWagon.CAPACITY, "it holds 64 buckets");
        helper.assertTrue(wagon.renderLoad() == 4 && wagon.mass() > light * 2, "full, the gauge shows it all and it's heavier");
        IFluidHandler pipe = wagon.getCapability(Capabilities.FluidHandler.ENTITY, null);
        helper.assertTrue(pipe != null && pipe.drain(10_000, IFluidHandler.FluidAction.EXECUTE).getAmount() == 10_000, "pipes can draw from it");
        helper.succeed();
    }

    @GameTest(template = LINE, timeoutTicks = 400)
    public static void loaderFillsAndEmptiesTankWagons(GameTestHelper helper) {
        line(helper, 4);
        FluidStack oil = new FluidStack(BuiltInRegistries.FLUID.get(ResourceLocation.parse("buildcraftreborn:fuel_dense")), 12_000);
        WagonLoaderBlockEntity loader = TestTracks.wagonLoader(helper.getLevel(), helper.absolutePos(new BlockPos(30, 1, 6)), at(helper, 30, 4), LoaderMode.LOAD);
        IFluidHandler refinery = bcTank(helper, loader.getBlockPos().east(), oil);
        TankWagon first = TestTracks.car(helper.getLevel(), ModEntities.TANK_WAGON.get(), at(helper, 30, 4), EAST);
        TankWagon second = TestTracks.car(helper.getLevel(), ModEntities.TANK_WAGON.get(), at(helper, 24, 4), EAST);
        helper.assertTrue(Trains.couple(first, second) == Trains.CoupleResult.COUPLED, "the tank wagons should couple");
        boolean[] emptying = {false};
        helper.onEachTick(() -> {
            if (!emptying[0] && first.tank().getFluidAmount() + second.tank().getFluidAmount() == 12_000) {
                helper.assertTrue(refinery.getFluidInTank(0).isEmpty(), "loading, the refinery's tank should have been emptied into the train");
                helper.assertTrue(loader.comparatorSignal() > 0, "the comparator should read the tank wagons");
                emptying[0] = true;
                Player player = helper.makeMockPlayer(GameType.SURVIVAL);
                helper.getLevel().getBlockState(loader.getBlockPos()).useWithoutItem(helper.getLevel(), player,
                        new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(loader.getBlockPos()), net.minecraft.core.Direction.UP, loader.getBlockPos(), false));
                bcTank(helper, loader.getBlockPos().west(), FluidStack.EMPTY);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(emptying[0], "the train should have been filled first");
            IFluidHandler depot = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, loader.getBlockPos().west(), null);
            helper.assertTrue(first.tank().isEmpty() && second.tank().isEmpty(), "unloading, both tank wagons should empty");
            int stored = depot.getFluidInTank(0).getAmount() + refinery.getFluidInTank(0).getAmount();
            helper.assertTrue(stored == 12_000, "into the tanks beside the loader, all 12 buckets: " + stored);
        });
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Guard's van

    @GameTest(template = LINE, timeoutTicks = 300)
    public static void guardsVanStopsATrainQuicker(GameTestHelper helper) {
        line(helper, 1, 7);
        ServerLevel level = helper.getLevel();
        SteamLocomotive plain = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 30, 1), EAST);
        SteamLocomotive braked = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 30, 7), EAST);
        // The same weight behind each: a 2-mass wagon-and-a-bit against a guard's van (mass 2)
        FreightWagon wagon = TestTracks.car(level, ModEntities.FREIGHT_WAGON.get(), at(helper, 24, 1), EAST);
        for (int i = 0; i < 54; i++) {
            wagon.setItem(i, new ItemStack(Items.STONE, 64));
        }
        GuardsVan van = TestTracks.car(level, ModEntities.GUARDS_VAN.get(), at(helper, 24, 7), EAST);
        Trains.couple(plain, wagon);
        Trains.couple(braked, van);
        plain.setFuel(16000);
        braked.setFuel(16000);
        Player a = helper.makeMockPlayer(GameType.SURVIVAL);
        a.moveTo(plain.position());
        a.startRiding(plain);
        Player b = helper.makeMockPlayer(GameType.SURVIVAL);
        b.moveTo(braked.position());
        b.startRiding(braked);
        for (int i = 0; i < 4; i++) {
            plain.control(TrainControlPayload.THROTTLE_UP);
            braked.control(TrainControlPayload.THROTTLE_UP);
        }
        double[] start = new double[2];
        helper.runAfterDelay(100, () -> {
            start[0] = Math.abs(plain.trainSpeed());
            start[1] = Math.abs(braked.trainSpeed());
            plain.control(TrainControlPayload.BRAKE_ON);
            braked.control(TrainControlPayload.BRAKE_ON);
        });
        helper.runAfterDelay(104, () -> {
            double plainDrop = start[0] - Math.abs(plain.trainSpeed());
            double vanDrop = start[1] - Math.abs(braked.trainSpeed());
            helper.assertTrue(start[0] > 0.1 && start[1] > 0.1, "both trains should have been going: " + start[0] + ", " + start[1]);
            helper.assertTrue(vanDrop > plainDrop * 1.35, "the train with the van should slow quicker: " + vanDrop + " against " + plainDrop);
            a.stopRiding();
            b.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void guardsVanCarriesTwoAndHoldsALooseTrain(GameTestHelper helper) {
        line(helper, 4);
        GuardsVan van = TestTracks.car(helper.getLevel(), ModEntities.GUARDS_VAN.get(), at(helper, 20, 4), EAST);
        FreightWagon wagon = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 26, 4), EAST);
        Trains.couple(van, wagon);
        for (int i = 0; i < 3; i++) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            van.interact(player, InteractionHand.MAIN_HAND);
        }
        helper.assertTrue(van.getPassengers().size() == 2, "the van seats two, not " + van.getPassengers().size());
        // Give the loose train a shove: the van's handbrake should stop it.
        van.shove(0.3);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(Math.abs(van.trainSpeed()) < 1.0E-6, "with no engine, the van's handbrake should hold the train");
            van.ejectPassengers();
            helper.succeed();
        });
    }

    @GameTest(template = LINE)
    public static void wagonRecipes(GameTestHelper helper) {
        for (String name : List.of("tank_wagon", "guards_van")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ReikoTrains.id(name)).isPresent(), "no recipe for " + name);
        }
        helper.succeed();
    }
}
