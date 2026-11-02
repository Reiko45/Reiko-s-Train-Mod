package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.freight.LoaderMode;
import com.reiko.trains.freight.WagonLoaderBlock;
import com.reiko.trains.freight.WagonLoaderBlockEntity;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.menu.WagonLoaderMenu;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.TankWagon;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for wagon loaders: filling and emptying trains, redstone, comparators, hoppers and timetables. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class LoaderGameTests {
    private static final String LINE = "line";
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private LoaderGameTests() {
    }

    /** Track along x at z = 4 (y = 1), the whole 64 blocks. */
    private static void line(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
        TestTracks.line(level, at(helper, 0), TrackAxis.X, 64);
    }

    private static BlockPos at(GameTestHelper helper, int x) {
        return helper.absolutePos(new BlockPos(x, 1, 4));
    }

    /** A loader beside the track at {@code x}, two blocks to the south (or the north). */
    private static WagonLoaderBlockEntity loader(GameTestHelper helper, int x, LoaderMode mode, boolean north) {
        return TestTracks.wagonLoader(helper.getLevel(), helper.absolutePos(new BlockPos(x, 1, north ? 2 : 6)), at(helper, x), mode);
    }

    /** A chest at {@code pos} with {@code stacks} full stacks of {@code item}. */
    private static ChestBlockEntity chest(GameTestHelper helper, BlockPos pos, Item item, int stacks) {
        helper.getLevel().setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        ChestBlockEntity chest = (ChestBlockEntity) helper.getLevel().getBlockEntity(pos);
        for (int i = 0; i < stacks; i++) {
            chest.setItem(i, new ItemStack(item, 64));
        }
        return chest;
    }

    private static int count(Container container, Item item) {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int count(Container container) {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            count += container.getItem(i).getCount();
        }
        return count;
    }

    private static void fill(FreightWagon wagon, Item item, int stacks) {
        for (int i = 0; i < stacks; i++) {
            wagon.setItem(i, new ItemStack(item, 64));
        }
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void loaderFillsAWagonStandingAtIt(GameTestHelper helper) {
        line(helper);
        WagonLoaderBlockEntity loader = loader(helper, 20, LoaderMode.LOAD, false);
        ChestBlockEntity chest = chest(helper, loader.getBlockPos().east(), Items.COBBLESTONE, 27);
        FreightWagon wagon = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 20), EAST);
        boolean[] lit = {false};
        helper.onEachTick(() -> lit[0] |= helper.getLevel().getBlockState(loader.getBlockPos()).getValue(WagonLoaderBlock.ACTIVE));
        helper.succeedWhen(() -> {
            helper.assertTrue(count(wagon, Items.COBBLESTONE) == 27 * 64, "the wagon should have all the cobblestone, has " + count(wagon));
            helper.assertTrue(chest.isEmpty(), "and the chest be empty");
            helper.assertTrue(lit[0], "the loader's lamp should have lit while it worked");
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void loaderEmptiesAWagonIntoItsChests(GameTestHelper helper) {
        line(helper);
        WagonLoaderBlockEntity loader = loader(helper, 20, LoaderMode.UNLOAD, false);
        ChestBlockEntity east = chest(helper, loader.getBlockPos().east(), Items.COBBLESTONE, 0);
        ChestBlockEntity west = chest(helper, loader.getBlockPos().west(), Items.COBBLESTONE, 0);
        FreightWagon wagon = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 20), EAST);
        fill(wagon, Items.OAK_LOG, 40);
        helper.succeedWhen(() -> {
            helper.assertTrue(wagon.isEmpty(), "the wagon should have been emptied, still has " + count(wagon));
            helper.assertTrue(count(east, Items.OAK_LOG) + count(west, Items.OAK_LOG) == 40 * 64, "into the two chests");
        });
    }

    @GameTest(template = LINE, timeoutTicks = 300)
    public static void loaderFillsEveryWagonInTheTrain(GameTestHelper helper) {
        line(helper);
        WagonLoaderBlockEntity loader = loader(helper, 34, LoaderMode.LOAD, false);
        ChestBlockEntity east = chest(helper, loader.getBlockPos().east(), Items.STONE, 27);
        ChestBlockEntity west = chest(helper, loader.getBlockPos().west(), Items.STONE, 27);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 34), EAST);
        FreightWagon first = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 27), EAST);
        FreightWagon second = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 20), EAST);
        helper.assertTrue(Trains.couple(loco, first) == Trains.CoupleResult.COUPLED, "the first wagon should couple");
        helper.assertTrue(Trains.couple(first, second) == Trains.CoupleResult.COUPLED, "the second wagon should couple");
        fill(first, Items.DIRT, 100);
        helper.succeedWhen(() -> {
            helper.assertTrue(east.isEmpty() && west.isEmpty(), "the loader (by the engine) should have emptied both chests");
            helper.assertTrue(count(first, Items.STONE) == 8 * 64, "into the first wagon's last 8 slots, not " + count(first, Items.STONE));
            helper.assertTrue(count(second, Items.STONE) == 46 * 64, "and the rest into the second wagon, not " + count(second, Items.STONE));
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void redstonePausesTheLoader(GameTestHelper helper) {
        line(helper);
        WagonLoaderBlockEntity loader = loader(helper, 20, LoaderMode.LOAD, false);
        ChestBlockEntity chest = chest(helper, loader.getBlockPos().east(), Items.COBBLESTONE, 5);
        BlockPos power = loader.getBlockPos().south();
        helper.getLevel().setBlock(power, Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        FreightWagon wagon = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 20), EAST);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(wagon.isEmpty() && count(chest) == 5 * 64, "powered, the loader shouldn't move anything");
            helper.getLevel().setBlock(power, Blocks.AIR.defaultBlockState(), 3);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(count(wagon) == 5 * 64 && chest.isEmpty(), "with the power off it should have loaded the wagon");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void comparatorReadsHowFullTheTrainIs(GameTestHelper helper) {
        line(helper);
        WagonLoaderBlockEntity loader = loader(helper, 20, LoaderMode.UNLOAD, false);
        FreightWagon wagon = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 20), EAST);
        fill(wagon, Items.COBBLESTONE, 54);
        helper.runAfterDelay(6, () -> {
            BlockPos pos = loader.getBlockPos();
            int signal = helper.getLevel().getBlockState(pos).getAnalogOutputSignal(helper.getLevel(), pos);
            helper.assertTrue(signal == 8, "a half-full wagon should read 8, not " + signal);
            wagon.discard();
        });
        helper.runAfterDelay(12, () -> {
            BlockPos pos = loader.getBlockPos();
            helper.assertTrue(helper.getLevel().getBlockState(pos).getAnalogOutputSignal(helper.getLevel(), pos) == 0, "with no train it should read 0");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void hoppersReachTheTrainThroughTheLoader(GameTestHelper helper) {
        line(helper);
        WagonLoaderBlockEntity loader = loader(helper, 20, LoaderMode.LOAD, false);
        FreightWagon wagon = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 20), EAST);
        helper.runAfterDelay(4, () -> {
            IItemHandler port = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, loader.getBlockPos(), Direction.UP);
            helper.assertTrue(port != null && port.getSlots() == FreightWagon.SLOTS, "the loader should show the wagon's slots");
            ItemStack left = port.insertItem(0, new ItemStack(Items.IRON_INGOT, 10), false);
            helper.assertTrue(left.isEmpty() && count(wagon, Items.IRON_INGOT) == 10, "loading, goods put in should go into the wagon");
            helper.assertTrue(port.extractItem(0, 10, false).isEmpty(), "loading, nothing should come out");
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            player.setShiftKeyDown(true); // sneak-click switches loading and unloading
            helper.getLevel().getBlockState(loader.getBlockPos()).useWithoutItem(helper.getLevel(), player,
                    new net.minecraft.world.phys.BlockHitResult(Vec3.atCenterOf(loader.getBlockPos()), Direction.UP, loader.getBlockPos(), false));
            helper.assertTrue(loader.mode() == LoaderMode.UNLOAD, "a sneak-click should switch it to unloading");
            helper.assertTrue(port.insertItem(0, new ItemStack(Items.IRON_INGOT, 10), false).getCount() == 10, "unloading, nothing should go in");
            helper.assertTrue(port.extractItem(0, 4, false).getCount() == 4 && count(wagon, Items.IRON_INGOT) == 6, "unloading, goods should come out");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void movingTrainsArentLoaded(GameTestHelper helper) {
        line(helper);
        WagonLoaderBlockEntity loader = loader(helper, 30, LoaderMode.LOAD, false);
        ChestBlockEntity chest = chest(helper, loader.getBlockPos().east(), Items.COBBLESTONE, 27);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 15), EAST);
        FreightWagon wagon = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 8), EAST);
        Trains.couple(loco, wagon);
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(loco.position());
        driver.startRiding(loco);
        loco.setFuel(16000);
        for (int i = 0; i < 4; i++) {
            loco.control(TrainControlPayload.THROTTLE_UP);
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(wagon.getX() > helper.absolutePos(BlockPos.ZERO).getX() + 40, "the train should have gone past the loader");
            helper.assertTrue(count(chest) == 27 * 64 && wagon.isEmpty(), "without stopping, so nothing should have been loaded");
            driver.stopRiding();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 1600)
    public static void timetabledTrainWaitsForTheLoader(GameTestHelper helper) {
        line(helper);
        StationBlockEntity loading = TestTracks.station(helper.getLevel(), helper.absolutePos(new BlockPos(50, 1, 7)), at(helper, 50), "Quarry", 2);
        StationBlockEntity home = TestTracks.station(helper.getLevel(), helper.absolutePos(new BlockPos(14, 1, 7)), at(helper, 14), "Yard", 2);
        WagonLoaderBlockEntity loader = loader(helper, 50, LoaderMode.LOAD, true);
        List<ChestBlockEntity> chests = List.of(chest(helper, loader.getBlockPos().east(), Items.GRAVEL, 27),
                chest(helper, loader.getBlockPos().west(), Items.GRAVEL, 27), chest(helper, loader.getBlockPos().above(), Items.GRAVEL, 27));
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 30), EAST);
        FreightWagon wagon = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 23), EAST);
        Trains.couple(loco, wagon);
        loco.setFuel(16000);
        loco.autopilot().start(List.of(TestTracks.stop(loading), TestTracks.stop(home)));
        boolean[] said = {false};
        helper.onEachTick(() -> said[0] |= loco.status().getString().contains("loading"));
        double stationX = loading.node().getX() + 0.5;
        helper.succeedWhen(() -> {
            helper.assertTrue(loco.getX() < stationX - 10, "the train should have left for the yard");
            for (ChestBlockEntity chest : chests) {
                helper.assertTrue(chest.isEmpty(), "it should have waited for all the gravel to be loaded");
            }
            helper.assertTrue(count(wagon, Items.GRAVEL) == 81 * 64, "and have it all in its wagon, not " + count(wagon, Items.GRAVEL));
            helper.assertTrue(said[0], "the cab should have said it was loading");
        });
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Filters

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void filterLoadsOnlyWhatsShown(GameTestHelper helper) {
        line(helper);
        WagonLoaderBlockEntity loader = loader(helper, 20, LoaderMode.LOAD, false);
        ChestBlockEntity chest = chest(helper, loader.getBlockPos().east(), Items.COBBLESTONE, 10);
        for (int i = 10; i < 15; i++) {
            chest.setItem(i, new ItemStack(Items.IRON_INGOT, 64));
        }
        loader.filter().setItem(0, new ItemStack(Items.IRON_INGOT));
        FreightWagon wagon = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 20), EAST);
        helper.succeedWhen(() -> {
            helper.assertTrue(count(wagon, Items.IRON_INGOT) == 5 * 64, "all the iron should be loaded, " + count(wagon, Items.IRON_INGOT));
            helper.assertTrue(count(wagon, Items.COBBLESTONE) == 0 && count(chest, Items.COBBLESTONE) == 10 * 64, "and none of the cobblestone");
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void denyFilterUnloadsAllButWhatsShown(GameTestHelper helper) {
        line(helper);
        WagonLoaderBlockEntity loader = loader(helper, 20, LoaderMode.UNLOAD, false);
        ChestBlockEntity chest = chest(helper, loader.getBlockPos().east(), Items.COBBLESTONE, 0);
        loader.filter().setItem(4, new ItemStack(Items.COAL));
        loader.setDeny(true);
        FreightWagon wagon = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 20), EAST);
        for (int i = 0; i < 6; i++) {
            wagon.setItem(i, new ItemStack(i % 2 == 0 ? Items.COAL : Items.OAK_LOG, 64));
        }
        helper.succeedWhen(() -> {
            helper.assertTrue(count(chest, Items.OAK_LOG) == 3 * 64, "the logs should be unloaded, " + count(chest, Items.OAK_LOG));
            helper.assertTrue(count(wagon, Items.COAL) == 3 * 64 && count(chest, Items.COAL) == 0, "and the coal left in the wagon");
        });
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void filterAppliesToHoppersAndKeepsTheTenderFuelled(GameTestHelper helper) {
        line(helper);
        WagonLoaderBlockEntity loader = loader(helper, 20, LoaderMode.LOAD, false);
        loader.filter().setItem(0, new ItemStack(Items.IRON_INGOT));
        ChestBlockEntity chest = chest(helper, loader.getBlockPos().east(), Items.COAL, 2);
        FreightWagon wagon = TestTracks.car(helper.getLevel(), ModEntities.FREIGHT_WAGON.get(), at(helper, 26), EAST);
        com.reiko.trains.train.Tender tender = TestTracks.car(helper.getLevel(), ModEntities.TENDER.get(), at(helper, 20), EAST);
        Trains.couple(wagon, tender);
        helper.runAfterDelay(4, () -> {
            IItemHandler port = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, loader.getBlockPos(), Direction.UP);
            helper.assertTrue(port.insertItem(0, new ItemStack(Items.DIRT, 5), false).getCount() == 5, "the filter should turn dirt away at the port");
            helper.assertTrue(port.insertItem(0, new ItemStack(Items.IRON_INGOT, 5), false).isEmpty(), "and let iron in");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(chest.isEmpty() && tender.fuel() == 2 * 64 * 1600, "the tender should still take its coal: fuel isn't filtered");
            helper.assertTrue(count(wagon, Items.COAL) == 0 && count(wagon, Items.IRON_INGOT) == 5, "but no coal goes in the wagon");
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void bucketInTheFilterPicksTheFluid(GameTestHelper helper) {
        line(helper);
        WagonLoaderBlockEntity loader = loader(helper, 20, LoaderMode.LOAD, false);
        loader.filter().setItem(0, new ItemStack(Items.WATER_BUCKET));
        BlockPos lavaPos = loader.getBlockPos().east();
        BlockPos waterPos = loader.getBlockPos().west();
        for (BlockPos pos : List.of(lavaPos, waterPos)) {
            helper.getLevel().setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("buildcraftreborn:tank")).defaultBlockState(), 3);
        }
        IFluidHandler lava = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, lavaPos, null);
        IFluidHandler water = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, waterPos, null);
        lava.fill(new FluidStack(Fluids.LAVA, 8000), IFluidHandler.FluidAction.EXECUTE);
        water.fill(new FluidStack(Fluids.WATER, 8000), IFluidHandler.FluidAction.EXECUTE);
        TankWagon tank = TestTracks.car(helper.getLevel(), ModEntities.TANK_WAGON.get(), at(helper, 20), EAST);
        helper.succeedWhen(() -> {
            helper.assertTrue(tank.tank().getFluid().is(Fluids.WATER) && tank.tank().getFluidAmount() == 8000, "the tank wagon should take the water");
            helper.assertTrue(lava.getFluidInTank(0).getAmount() == 8000, "and leave the lava, which isn't in the filter");
        });
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void loaderScreenSetsTheFilter(GameTestHelper helper) {
        line(helper);
        WagonLoaderBlockEntity loader = loader(helper, 20, LoaderMode.LOAD, false);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getInventory().setItem(5, new ItemStack(Items.IRON_INGOT, 32));
        WagonLoaderMenu menu = new WagonLoaderMenu(1, player.getInventory(), loader);
        menu.setCarried(new ItemStack(Items.GOLD_INGOT, 10));
        menu.clicked(3, 0, ClickType.PICKUP, player);
        helper.assertTrue(loader.filter().getItem(3).is(Items.GOLD_INGOT) && loader.filter().getItem(3).getCount() == 1, "clicking with gold should show gold");
        helper.assertTrue(menu.getCarried().getCount() == 10, "and keep all ten in hand");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(3, 0, ClickType.PICKUP, player);
        helper.assertTrue(loader.filter().getItem(3).isEmpty(), "clicking empty-handed should clear it");
        int inventorySlot = 9 + 27 + 5; // the hotbar's sixth slot, after the filter and the main inventory
        menu.quickMoveStack(player, inventorySlot);
        helper.assertTrue(loader.filter().getItem(0).is(Items.IRON_INGOT) && player.getInventory().getItem(5).getCount() == 32,
                "shift-clicking iron in the inventory should add it to the filter, and leave the iron where it was");
        helper.assertTrue(menu.clickMenuButton(player, WagonLoaderMenu.BUTTON_FILTER) && loader.isDeny(), "the filter button should switch to all-but");
        helper.assertTrue(menu.clickMenuButton(player, WagonLoaderMenu.BUTTON_MODE) && loader.mode() == LoaderMode.UNLOAD, "the mode button should switch to unloading");
        helper.succeed();
    }

    /** All the cars of the train {@code car} is in (for debugging failures). */
    @SuppressWarnings("unused")
    private static String describe(TrainCarEntity car) {
        StringBuilder text = new StringBuilder();
        for (TrainCarEntity c : Trains.train(car)) {
            text.append(c.getType().getDescription().getString()).append(" at ").append(String.format("%.1f", c.getX())).append("; ");
        }
        return text.toString();
    }
}
