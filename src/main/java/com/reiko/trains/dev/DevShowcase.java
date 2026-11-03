package com.reiko.trains.dev;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.gametest.TestTracks;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModDataComponents;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.DieselLocomotive;
import com.reiko.trains.train.ElectricLocomotive;
import com.reiko.trains.power.ChargingPointBlockEntity;
import com.reiko.trains.train.ExpressLocomotive;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.GuardsVan;
import com.reiko.trains.train.TankWagon;
import com.reiko.trains.train.Tender;
import com.reiko.trains.train.PassengerCoach;
import com.reiko.trains.train.Locomotive;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import com.reiko.trains.menu.FreightWagonMenu;
import com.reiko.trains.freight.LoaderMode;
import com.reiko.trains.freight.WagonLoaderBlockEntity;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.signal.SwitchBlockEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * DEV ONLY (left out of the jar by build.gradle). {@code ./gradlew runClient -Pshowcase} makes a flat creative
 * world, lays a loop of track with a junction, builds two trains and drives one round, saving screenshots to
 * {@code run/screenshots/}, then quits. Used to check the models, track, seats, smoke, screens and gauges in the
 * real game.
 */
@EventBusSubscriber(modid = ReikoTrains.MODID, value = Dist.CLIENT)
public final class DevShowcase {
    private static final boolean ENABLED = Boolean.getBoolean("reikotrains.showcase");
    /** The flat world's ground is at y = -61, so track goes at -60. */
    private static final int Y = -60;
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static boolean worldRequested;
    private static int tick = -1;
    private static final List<Step> STEPS = new ArrayList<>();

    private record Step(int at, Consumer<Minecraft> action) {
    }

    private DevShowcase() {
    }

    private static void at(int time, Consumer<Minecraft> action) {
        STEPS.add(new Step(time, action));
    }

    @SubscribeEvent
    static void onScreen(ScreenEvent.Opening event) {
        if (!ENABLED || worldRequested || !(event.getNewScreen() instanceof TitleScreen)) {
            return;
        }
        worldRequested = true;
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            GameRules rules = new GameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
            LevelSettings settings = new LevelSettings("showcase-" + System.currentTimeMillis(), GameType.CREATIVE, false,
                    Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT);
            minecraft.createWorldOpenFlows().createFreshLevel(settings.levelName(), settings, new WorldOptions(1L, false, false),
                    registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                            .value().createWorldDimensions(), new TitleScreen());
        });
        script();
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ENABLED || minecraft.player == null || minecraft.getSingleplayerServer() == null) {
            return;
        }
        tick++;
        for (Step step : STEPS) {
            if (step.at == tick) {
                try {
                    step.action.accept(minecraft);
                } catch (RuntimeException e) {
                    ReikoTrains.LOGGER.error("Showcase step at tick {} failed", tick, e);
                }
            }
        }
    }

    private static void server(Minecraft minecraft, Consumer<ServerPlayer> action) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        server.execute(() -> action.accept(server.getPlayerList().getPlayers().getFirst()));
    }

    private static void look(Minecraft minecraft, double x, double y, double z, float yaw, float pitch) {
        server(minecraft, player -> {
            player.stopRiding();
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(player.serverLevel(), x, y, z, yaw, pitch);
        });
    }

    private static void shot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(),
                message -> ReikoTrains.LOGGER.info("Showcase screenshot: {}", message.getString()));
    }

    private static <T extends TrainCarEntity> T find(ServerLevel level, Class<T> type, double x, double z) {
        return level.getEntitiesOfClass(type, new AABB(x - 4, Y - 2, z - 4, x + 4, Y + 4, z + 4)).getFirst();
    }

    private static <T extends TrainCarEntity> T put(ServerLevel level, net.minecraft.world.entity.EntityType<T> type, int x, int z) {
        return TestTracks.car(level, type, new BlockPos(x, Y, z), EAST);
    }

    /**
     * The layout: a main line along z = 0 with a station (Riverside) and a signal, and a junction at x = 52; and to
     * the south a loop (straights at z = 14 and z = 38, x 12 to 42, radius-12 ends) signalled for trains going round
     * clockwise, with two stations (Northgate, Southport) and two trains running between them on timetables.
     */
    /** The line with the level crossings on it, along x at this z. */
    private static final int CROSSING_LINE = -48;
    /** The line with the electric and its charging point, along x at this z. */
    private static final int ELECTRIC_LINE = -62;
    /** The line where a goods train leaves wagons at a yard, along x at this z. */
    private static final int GOODS_LINE = -76;
    /** The line with a livestock wagon loading at a pen, along x at this z. */
    private static final int STOCK_LINE = -90;
    /** The line with speed limit signs on it, along x at this z. */
    private static final int SPEED_LINE = -104;
    /** The switch stand for the junction at x = 52. */
    private static final BlockPos SWITCH = new BlockPos(52, Y, -2);

    private static void build(ServerLevel level) {
        TestTracks.line(level, new BlockPos(-30, Y, 0), TrackAxis.X, 110);
        BlockPos branch = new BlockPos(52, Y, 0);
        TestTracks.connect(level, branch, branch.offset(14, 0, 6));
        TestTracks.line(level, branch.offset(15, 0, 7), TrackAxis.SE, 10);
        TestTracks.station(level, new BlockPos(32, Y, -3), new BlockPos(32, Y, 0), "Riverside", 10);
        TestTracks.signal(level, new BlockPos(46, Y, -2), new BlockPos(46, Y, 0), 1);
        TestTracks.switchStand(level, SWITCH);

        TestTracks.loop(level, new BlockPos(0, Y, 14), 30, 12);
        for (int x : new int[]{14, 26, 38}) {
            TestTracks.signal(level, new BlockPos(x, Y, 12), new BlockPos(x, Y, 14), 1);
            TestTracks.signal(level, new BlockPos(54 - x, Y, 40), new BlockPos(54 - x, Y, 38), -1);
        }
        StationBlockEntity north = TestTracks.station(level, new BlockPos(32, Y, 11), new BlockPos(32, Y, 14), "Northgate", 4);
        StationBlockEntity south = TestTracks.station(level, new BlockPos(22, Y, 41), new BlockPos(22, Y, 38), "Southport", 4);

        Locomotive loco = put(level, ModEntities.STEAM_LOCOMOTIVE.get(), 22, 0);
        PassengerCoach coach = put(level, ModEntities.PASSENGER_COACH.get(), 15, 0);
        PassengerCoach coach2 = put(level, ModEntities.PASSENGER_COACH.get(), 8, 0);
        FreightWagon wagon = put(level, ModEntities.FREIGHT_WAGON.get(), 2, 0);
        Trains.couple(loco, coach);
        Trains.couple(coach, coach2);
        Trains.couple(coach2, wagon);
        for (int i = 0; i < 70; i++) {
            wagon.setItem(i, new ItemStack(i % 3 == 0 ? Items.OAK_LOG : i % 3 == 1 ? Items.COAL : Items.IRON_INGOT, 64));
        }
        // A siding to the north with a freight wagon, and a wagon loader topping it up from two chests and a barrel
        TestTracks.line(level, new BlockPos(-6, Y, -12), TrackAxis.X, 30);
        FreightWagon sided = put(level, ModEntities.FREIGHT_WAGON.get(), 8, -12);
        for (int i = 0; i < 30; i++) {
            sided.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        BlockPos loader = new BlockPos(10, Y, -14);
        TestTracks.wagonLoader(level, loader, new BlockPos(10, Y, -12), LoaderMode.LOAD);
        for (BlockPos store : new BlockPos[]{loader.west(), loader.east(), loader.north()}) {
            level.setBlock(store, store.equals(loader.north()) ? Blocks.BARREL.defaultBlockState() : Blocks.CHEST.defaultBlockState(), 3);
            if (level.getBlockEntity(store) instanceof net.minecraft.world.Container box) {
                for (int i = 0; i < box.getContainerSize(); i++) {
                    box.setItem(i, new ItemStack(i % 2 == 0 ? Items.COAL : Items.OAK_LOG, 64));
                }
            }
        }
        // A second line to the north, with an express: engine, tender and two coaches, and a station to run to
        TestTracks.line(level, new BlockPos(-20, Y, -24), TrackAxis.X, 110);
        ExpressLocomotive express = TestTracks.car(level, ModEntities.EXPRESS_LOCOMOTIVE.get(), new BlockPos(30, Y, -24), EAST);
        Tender tender = TestTracks.car(level, ModEntities.TENDER.get(), new BlockPos(22, Y, -24), EAST);
        PassengerCoach first = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), new BlockPos(15, Y, -24), EAST);
        PassengerCoach second = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), new BlockPos(8, Y, -24), EAST);
        Trains.couple(express, tender);
        Trains.couple(tender, first);
        Trains.couple(first, second);
        tender.setFuel(Tender.FUEL_CAPACITY * 0.8);
        TestTracks.station(level, new BlockPos(85, Y, -27), new BlockPos(85, Y, -24), "Express Halt", 10);

        // A third line further north, with a diesel and three freight wagons, and a station to run to
        TestTracks.line(level, new BlockPos(-20, Y, -36), TrackAxis.X, 110);
        DieselLocomotive diesel = TestTracks.car(level, ModEntities.DIESEL_LOCOMOTIVE.get(), new BlockPos(30, Y, -36), EAST);
        Locomotive last = diesel;
        for (int i = 0; i < 3; i++) {
            FreightWagon goods = TestTracks.car(level, ModEntities.FREIGHT_WAGON.get(), new BlockPos(23 - 6 * i, Y, -36), EAST);
            for (int slot = 0; slot < 40 + 30 * i; slot++) {
                goods.setItem(slot, new ItemStack(Items.IRON_ORE, 64));
            }
            Trains.couple(Trains.train(last).getLast(), goods);
        }
        // and on the end, a tank wagon (half full of water) and a guard's van
        TankWagon tanker = TestTracks.car(level, ModEntities.TANK_WAGON.get(), new BlockPos(4, Y, -36), EAST);
        tanker.tank().fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 40_000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        GuardsVan van = TestTracks.car(level, ModEntities.GUARDS_VAN.get(), new BlockPos(-2, Y, -36), EAST);
        Trains.couple(tanker, van);
        Trains.couple(Trains.train(diesel).getLast(), tanker);
        diesel.setFuel(DieselLocomotive.FUEL_CAPACITY * 0.7);
        TestTracks.station(level, new BlockPos(85, Y, -39), new BlockPos(85, Y, -36), "Freight Yard", 10);

        // A fourth line, furthest north, with a road crossing it at x = 38 to 41 between two level crossings (their arms
        // over the road three blocks either side of the track), and a train waiting well back to run over it.
        TestTracks.line(level, new BlockPos(-60, Y, CROSSING_LINE), TrackAxis.X, 150);
        for (int x = 38; x <= 41; x++) {
            for (int z = CROSSING_LINE - 10; z <= CROSSING_LINE + 8; z++) {
                level.setBlock(new BlockPos(x, Y - 1, z), Blocks.GRAY_CONCRETE.defaultBlockState(), 2);
            }
        }
        TestTracks.levelCrossing(level, new BlockPos(42, Y, CROSSING_LINE - 3), net.minecraft.core.Direction.WEST, 4);
        TestTracks.levelCrossing(level, new BlockPos(37, Y, CROSSING_LINE + 3), net.minecraft.core.Direction.EAST, 4);
        TestTracks.station(level, new BlockPos(85, Y, CROSSING_LINE - 3), new BlockPos(85, Y, CROSSING_LINE), "Crossing Halt", 10);
        Locomotive crosser = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), new BlockPos(-40, Y, CROSSING_LINE), EAST);
        PassengerCoach crosserFirst = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), new BlockPos(-47, Y, CROSSING_LINE), EAST);
        PassengerCoach crosserSecond = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), new BlockPos(-54, Y, CROSSING_LINE), EAST);
        Trains.couple(crosserFirst, crosserSecond);
        Trains.couple(crosser, crosserFirst);
        crosser.setFuel(32000);

        // A fifth line, further north still, with an electric and two coaches standing at a charging point (fed by a
        // BuildCraft Stirling engine, though its store's filled later, for the pictures), and a station to run to.
        TestTracks.line(level, new BlockPos(0, Y, ELECTRIC_LINE), TrackAxis.X, 64);
        TestTracks.chargingPoint(level, new BlockPos(31, Y, ELECTRIC_LINE + 3), new BlockPos(31, Y, ELECTRIC_LINE));
        BlockPos engine = new BlockPos(30, Y, ELECTRIC_LINE + 3);
        level.setBlock(engine.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        net.minecraft.core.registries.BuiltInRegistries.BLOCK.getOptional(net.minecraft.resources.ResourceLocation.parse("buildcraftreborn:engine_stone"))
                .ifPresent(block -> {
                    level.setBlock(engine, block.defaultBlockState(), 3);
                    var fuel = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, engine, net.minecraft.core.Direction.UP);
                    if (fuel != null) {
                        fuel.insertItem(0, new ItemStack(Items.COAL, 64), false);
                    }
                });
        TestTracks.station(level, new BlockPos(56, Y, ELECTRIC_LINE - 3), new BlockPos(56, Y, ELECTRIC_LINE), "Power Halt", 10);
        ElectricLocomotive electric = TestTracks.car(level, ModEntities.ELECTRIC_LOCOMOTIVE.get(), new BlockPos(30, Y, ELECTRIC_LINE), EAST);
        PassengerCoach electricFirst = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), new BlockPos(22, Y, ELECTRIC_LINE), EAST);
        PassengerCoach electricSecond = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), new BlockPos(15, Y, ELECTRIC_LINE), EAST);
        Trains.couple(electricFirst, electricSecond);
        Trains.couple(electric, electricFirst);
        electric.setFuel(ElectricLocomotive.BATTERY_CAPACITY * 0.2);
        // Further along, a track sensor that only senses trains with an electric going east, lighting a lamp.
        com.reiko.trains.signal.TrackSensorBlockEntity sensor = TestTracks.trackSensor(level, new BlockPos(44, Y, ELECTRIC_LINE + 3));
        sensor.setFilter(new ItemStack(com.reiko.trains.registry.ModItems.ELECTRIC_LOCOMOTIVE.get()));
        level.setBlock(sensor.getBlockPos(), sensor.getBlockState().setValue(com.reiko.trains.signal.TrackSensorBlock.DIRECTION,
                com.reiko.trains.signal.SensorDirection.RIGHT), 3);
        level.setBlock(new BlockPos(45, Y, ELECTRIC_LINE + 3), Blocks.REDSTONE_LAMP.defaultBlockState(), 3);

        // A sixth line, with a diesel and three loaded wagons, a goods yard to leave two of them at, and a far end.
        TestTracks.line(level, new BlockPos(0, Y, GOODS_LINE), TrackAxis.X, 90);
        TestTracks.station(level, new BlockPos(40, Y, GOODS_LINE - 3), new BlockPos(40, Y, GOODS_LINE), "Goods Yard", 1);
        TestTracks.station(level, new BlockPos(85, Y, GOODS_LINE - 3), new BlockPos(85, Y, GOODS_LINE), "Far End", 10);
        DieselLocomotive shunter = TestTracks.car(level, ModEntities.DIESEL_LOCOMOTIVE.get(), new BlockPos(24, Y, GOODS_LINE), EAST);
        TrainCarEntity behind = shunter;
        for (int x : new int[]{17, 11, 5}) {
            FreightWagon goods = TestTracks.car(level, ModEntities.FREIGHT_WAGON.get(), new BlockPos(x, Y, GOODS_LINE), EAST);
            for (int slot = 0; slot < 50 + x * 3; slot++) {
                goods.setItem(slot, new ItemStack(x == 17 ? Items.IRON_ORE : Items.OAK_LOG, 64));
            }
            Trains.couple(behind, goods);
            behind = goods;
        }
        shunter.setFuel(DieselLocomotive.FUEL_CAPACITY * 0.5);

        // A seventh line: a tank engine and a livestock wagon at a wagon loader, with a pen of animals behind it.
        TestTracks.line(level, new BlockPos(0, Y, STOCK_LINE), TrackAxis.X, 40);
        TestTracks.wagonLoader(level, new BlockPos(20, Y, STOCK_LINE - 3), new BlockPos(20, Y, STOCK_LINE), LoaderMode.LOAD);
        List<BlockPos> fences = new ArrayList<>();
        for (int x = 16; x <= 24; x++) {
            for (int z = STOCK_LINE - 8; z <= STOCK_LINE - 3; z++) {
                boolean edge = x == 16 || x == 24 || z == STOCK_LINE - 8 || z == STOCK_LINE - 3;
                if (edge && !(x == 20 && z == STOCK_LINE - 3)) {
                    fences.add(new BlockPos(x, Y, z));
                    level.setBlock(new BlockPos(x, Y, z), Blocks.OAK_FENCE.defaultBlockState(), 3);
                }
            }
        }
        for (BlockPos fence : fences) {
            level.setBlock(fence, net.minecraft.world.level.block.Block.updateFromNeighbourShapes(level.getBlockState(fence), level, fence), 3);
        }
        net.minecraft.world.entity.EntityType<?>[] herd = {net.minecraft.world.entity.EntityType.SHEEP, net.minecraft.world.entity.EntityType.SHEEP,
                net.minecraft.world.entity.EntityType.COW, net.minecraft.world.entity.EntityType.PIG, net.minecraft.world.entity.EntityType.SHEEP,
                net.minecraft.world.entity.EntityType.COW, net.minecraft.world.entity.EntityType.PIG, net.minecraft.world.entity.EntityType.SHEEP};
        for (int i = 0; i < herd.length; i++) {
            net.minecraft.world.entity.Entity animal = herd[i].create(level);
            animal.moveTo(17.5 + (i % 4) * 2, Y, STOCK_LINE - 6.5 + (i / 4) * 2, i * 45, 0);
            level.addFreshEntity(animal);
        }
        TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), new BlockPos(28, Y, STOCK_LINE), EAST);
        TestTracks.car(level, ModEntities.LIVESTOCK_WAGON.get(), new BlockPos(20, Y, STOCK_LINE), EAST);

        // An eighth line: a 20 km/h speed limit from x = 40 to the end sign at x = 64, and a train to run through it.
        TestTracks.line(level, new BlockPos(0, Y, SPEED_LINE), TrackAxis.X, 90);
        TestTracks.speedSign(level, new BlockPos(40, Y, SPEED_LINE + 2), new BlockPos(40, Y, SPEED_LINE), 1, 1);
        TestTracks.speedSign(level, new BlockPos(64, Y, SPEED_LINE + 2), new BlockPos(64, Y, SPEED_LINE), 1, 5);
        TestTracks.station(level, new BlockPos(84, Y, SPEED_LINE - 3), new BlockPos(84, Y, SPEED_LINE), "Far End", 10);
        Locomotive limited = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), new BlockPos(24, Y, SPEED_LINE), EAST);
        PassengerCoach limitedCoach = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), new BlockPos(17, Y, SPEED_LINE), EAST);
        Trains.couple(limited, limitedCoach);
        limited.setFuel(32000);

        Locomotive a = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), new BlockPos(22, Y, 14), EAST);
        PassengerCoach aCoach = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), new BlockPos(15, Y, 14), EAST);
        Locomotive b = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), new BlockPos(30, Y, 38), new Vec3(-1, 0, 0));
        PassengerCoach bCoach = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), new BlockPos(37, Y, 38), new Vec3(-1, 0, 0));
        Trains.couple(a, aCoach);
        Trains.couple(b, bCoach);
        a.setFuel(32000);
        b.setFuel(32000);
        a.autopilot().start(List.of(TestTracks.stop(south), TestTracks.stop(north)));
        b.autopilot().start(List.of(TestTracks.stop(north), TestTracks.stop(south)));
    }

    private static void script() {
        at(20, mc -> {
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.gameRenderer.setRenderHand(false);
            server(mc, player -> {
                player.serverLevel().setDayTime(6000);
                build(player.serverLevel());
            });
        });
        at(24, mc -> look(mc, 14.0, Y + 1.6, -10.2, 125.0F, 26.0F));
        at(54, mc -> shot(mc, "00_wagon_loader"));
        at(60, mc -> look(mc, 22.0, Y + 1.9, 9.5, 180.0F, 4.0F));
        at(120, mc -> shot(mc, "01_train_and_station"));
        at(122, mc -> look(mc, 29.0, Y + 2.2, 1.8, -160.0F, 16.0F));
        at(165, mc -> shot(mc, "02_station_board"));
        at(167, mc -> look(mc, 9.5, Y + 2.6, 11.0, -62.0F, 10.0F));
        at(200, mc -> shot(mc, "02b_signals"));
        // The loop: trains running round between the stations, signals changing behind them
        at(205, mc -> look(mc, 27.0, Y + 32.0, 26.0, 0.0F, 90.0F));
        at(260, mc -> shot(mc, "03_loop_from_above"));
        at(262, mc -> look(mc, 2.0, Y + 6.0, 6.0, -55.0F, 22.0F));
        at(340, mc -> shot(mc, "04_loop_signals"));
        at(342, mc -> look(mc, 40.0, Y + 2.5, 8.0, 115.0F, 12.0F));
        at(420, mc -> shot(mc, "05_northgate"));
        at(500, mc -> shot(mc, "06_northgate_later"));
        at(502, mc -> look(mc, 10.0, Y + 3.0, 45.0, -140.0F, 14.0F));
        at(580, mc -> shot(mc, "07_southport"));
        // Into the cab of the main line train
        at(585, mc -> server(mc, player -> {
            Locomotive loco = find(player.serverLevel(), Locomotive.class, 22.5, 0.5);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(player.serverLevel(), loco.getX(), loco.getY(), loco.getZ(), -90.0F, 10.0F);
            player.startRiding(loco);
            loco.setFuel(16000);
        }));
        at(592, mc -> {
            mc.player.setYRot(-90.0F);
            mc.player.setXRot(6.0F);
            mc.gameRenderer.setRenderHand(true);
        });
        at(620, mc -> shot(mc, "08_cab_gauges"));
        at(622, mc -> server(mc, player -> {
            if (player.getVehicle() instanceof Locomotive loco) {
                loco.control(TrainControlPayload.THROTTLE_UP);
                loco.control(TrainControlPayload.THROTTLE_UP);
            }
        }));
        at(700, mc -> shot(mc, "09_cab_running"));
        at(702, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        at(740, mc -> shot(mc, "10_passing_riverside"));
        at(742, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(mc, player -> {
                if (player.getVehicle() instanceof Locomotive loco) {
                    loco.control(TrainControlPayload.BRAKE_ON);
                }
            });
        });
        // The station's settings screen
        at(780, mc -> mc.setScreen(new com.reiko.trains.client.StationScreen(new BlockPos(32, Y, -3), "Riverside", 10)));
        at(800, mc -> shot(mc, "11_station_screen"));
        at(805, mc -> mc.setScreen(null));
        // The switch at the junction: set normally, then thrown; then a train on a timetable sets it for itself
        at(806, mc -> {
            mc.gameRenderer.setRenderHand(false);
            mc.gameRenderer.setRenderBlockOutline(false);
            look(mc, 51.0, Y, -4.4, -28.0F, 12.0F);
        });
        at(850, mc -> shot(mc, "12_switch_normal"));
        at(852, mc -> server(mc, player -> {
            if (player.serverLevel().getBlockEntity(SWITCH) instanceof SwitchBlockEntity stand) {
                stand.setThrown(true);
            }
        }));
        at(870, mc -> shot(mc, "13_switch_thrown"));
        at(872, mc -> server(mc, player -> {
            ServerLevel level = player.serverLevel();
            if (level.getBlockEntity(SWITCH) instanceof SwitchBlockEntity stand) {
                stand.setThrown(false);
            }
            StationBlockEntity halt = TestTracks.station(level, new BlockPos(77, Y, 13), new BlockPos(75, Y, 15), "Junction Halt", 10);
            Locomotive loco = level.getEntitiesOfClass(Locomotive.class, new AABB(-40, Y - 2, -4, 80, Y + 4, 4)).getFirst();
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(level, loco.getX(), loco.getY(), loco.getZ(), -90.0F, 6.0F);
            player.startRiding(loco);
            loco.control(TrainControlPayload.BRAKE_OFF);
            loco.autopilot().start(List.of(TestTracks.stop(halt)));
        }));
        at(878, mc -> {
            mc.player.setYRot(-90.0F);
            mc.player.setXRot(6.0F);
            mc.gameRenderer.setRenderBlockOutline(true);
        });
        at(900, mc -> shot(mc, "14_cab_points"));
        at(902, mc -> {
            mc.gameRenderer.setRenderBlockOutline(false);
            look(mc, 49.0, Y + 7.0, -9.0, -40.0F, 28.0F);
        });
        at(975, mc -> shot(mc, "15_taking_the_branch"));
        at(1010, mc -> shot(mc, "16_taking_the_branch_later"));
        // The express: from the side and the front, then away on a timetable
        at(1012, mc -> look(mc, 29.0, Y + 1.6, -15.5, 180.0F, 6.0F));
        at(1050, mc -> shot(mc, "17_express"));
        at(1052, mc -> look(mc, 41.0, Y + 1.2, -19.5, 117.0F, 8.0F));
        at(1090, mc -> shot(mc, "18_express_front"));
        at(1092, mc -> server(mc, player -> {
            ServerLevel level = player.serverLevel();
            ExpressLocomotive express = level.getEntitiesOfClass(ExpressLocomotive.class, new AABB(20, Y - 2, -28, 40, Y + 4, -20)).getFirst();
            if (level.getBlockEntity(new BlockPos(85, Y, -27)) instanceof StationBlockEntity halt) {
                express.autopilot().start(List.of(TestTracks.stop(halt)));
            }
        }));
        at(1094, mc -> look(mc, 54.0, Y + 1.5, -18.0, 115.0F, 6.0F));
        at(1150, mc -> shot(mc, "19_express_running"));
        at(1190, mc -> shot(mc, "20_express_running_later"));
        // The diesel: side and front, then away on a timetable with its horn
        at(1196, mc -> look(mc, 4.0, Y + 1.6, -28.5, 180.0F, 6.0F));
        at(1236, mc -> shot(mc, "20b_tank_and_van"));
        at(1242, mc -> look(mc, 28.0, Y + 1.6, -27.5, 180.0F, 6.0F));
        at(1280, mc -> shot(mc, "21_diesel"));
        at(1282, mc -> look(mc, 40.0, Y + 1.4, -31.0, 125.0F, 10.0F));
        at(1320, mc -> shot(mc, "22_diesel_front"));
        at(1322, mc -> server(mc, player -> {
            ServerLevel level = player.serverLevel();
            DieselLocomotive diesel = level.getEntitiesOfClass(DieselLocomotive.class, new AABB(20, Y - 2, -40, 40, Y + 4, -32)).getFirst();
            if (level.getBlockEntity(new BlockPos(85, Y, -39)) instanceof StationBlockEntity yard) {
                diesel.autopilot().start(List.of(TestTracks.stop(yard)));
            }
        }));
        at(1324, mc -> look(mc, 52.0, Y + 1.5, -30.0, 115.0F, 6.0F));
        at(1400, mc -> shot(mc, "23_diesel_running"));
        // The wagon loader's screen, with a filter set
        at(1402, mc -> look(mc, 12.5, Y, -11.0, 140.0F, 20.0F));
        at(1420, mc -> server(mc, player -> {
            if (player.serverLevel().getBlockEntity(new BlockPos(10, Y, -14)) instanceof WagonLoaderBlockEntity loader) {
                loader.filter().setItem(0, new ItemStack(Items.IRON_ORE));
                loader.filter().setItem(1, new ItemStack(Items.COAL));
                loader.filter().setItem(2, new ItemStack(Items.WATER_BUCKET));
                player.openMenu(new net.minecraft.world.SimpleMenuProvider(
                        (id, inventory, p) -> new com.reiko.trains.menu.WagonLoaderMenu(id, inventory, loader),
                        net.minecraft.network.chat.Component.translatable("block.reikotrains.wagon_loader")), loader.getBlockPos());
            }
        }));
        at(1450, mc -> shot(mc, "24_loader_screen"));
        // The switch stand set to hold the line straight, and held there by a redstone block under it: its marker lit
        at(1452, mc -> {
            mc.setScreen(null);
            server(mc, player -> {
                player.closeContainer();
                if (player.serverLevel().getBlockEntity(SWITCH) instanceof SwitchBlockEntity stand) {
                    while (stand.redstoneWay() != 0) {
                        stand.cycleRedstoneWay();
                    }
                    player.serverLevel().setBlock(SWITCH.below(), Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                }
            });
        });
        at(1456, mc -> {
            mc.gameRenderer.setRenderHand(false);
            mc.gameRenderer.setRenderBlockOutline(false);
            look(mc, 51.0, Y, -4.4, -28.0F, 12.0F);
        });
        at(1500, mc -> shot(mc, "25_switch_held_straight"));
        // The level crossings: a train sets off well back; the lamps flash, the arms come down, and it runs over
        at(1502, mc -> {
            look(mc, 35.0, Y + 2.0, CROSSING_LINE + 8.0, -141.0F, 10.0F);
            server(mc, player -> {
                ServerLevel level = player.serverLevel();
                Locomotive crosser = level.getEntitiesOfClass(Locomotive.class,
                        new AABB(-46, Y - 2, CROSSING_LINE - 4, -34, Y + 4, CROSSING_LINE + 4)).getFirst();
                if (level.getBlockEntity(new BlockPos(85, Y, CROSSING_LINE - 3)) instanceof StationBlockEntity halt) {
                    crosser.autopilot().start(List.of(TestTracks.stop(halt)));
                }
            });
        });
        at(1702, mc -> shot(mc, "26_level_crossing_closed"));
        at(1742, mc -> shot(mc, "27_level_crossing_train"));
        // The electric: its charging point's store filled (as engines would), charging it; its front; then away
        at(1744, mc -> {
            look(mc, 34.5, Y + 1.4, ELECTRIC_LINE + 7.5, 143.0F, 12.0F);
            server(mc, player -> {
                if (player.serverLevel().getBlockEntity(new BlockPos(31, Y, ELECTRIC_LINE + 3)) instanceof ChargingPointBlockEntity charger) {
                    charger.addMj(600_000);
                }
            });
        });
        at(1784, mc -> shot(mc, "28_charging_point"));
        at(1786, mc -> look(mc, 38.5, Y + 0.6, ELECTRIC_LINE - 1.8, 67.0F, 4.0F));
        at(1826, mc -> shot(mc, "29_electric_front"));
        at(1828, mc -> {
            look(mc, 46.0, Y + 1.6, ELECTRIC_LINE + 7.5, 150.0F, 6.0F);
            server(mc, player -> {
                ServerLevel level = player.serverLevel();
                ElectricLocomotive electric = level.getEntitiesOfClass(ElectricLocomotive.class,
                        new AABB(20, Y - 2, ELECTRIC_LINE - 4, 40, Y + 4, ELECTRIC_LINE + 4)).getFirst();
                if (level.getBlockEntity(new BlockPos(56, Y, ELECTRIC_LINE - 3)) instanceof StationBlockEntity halt) {
                    electric.autopilot().start(List.of(TestTracks.stop(halt)));
                }
            });
        });
        at(1890, mc -> shot(mc, "30_electric_running"));
        // The track sensor lighting its lamp as the electric goes by
        at(1892, mc -> look(mc, 42.4, Y + 1.0, ELECTRIC_LINE + 6.3, 214.0F, 18.0F));
        at(1925, mc -> shot(mc, "31_track_sensor"));
        // The goods train: off to the yard, where it leaves its last two wagons, and on without them
        at(1790, mc -> server(mc, player -> {
            ServerLevel level = player.serverLevel();
            DieselLocomotive shunter = level.getEntitiesOfClass(DieselLocomotive.class,
                    new AABB(18, Y - 2, GOODS_LINE - 4, 30, Y + 4, GOODS_LINE + 4)).getFirst();
            if (level.getBlockEntity(new BlockPos(40, Y, GOODS_LINE - 3)) instanceof StationBlockEntity yard
                    && level.getBlockEntity(new BlockPos(85, Y, GOODS_LINE - 3)) instanceof StationBlockEntity far) {
                shunter.autopilot().start(List.of(TestTracks.stop(yard).withWagons(2), TestTracks.stop(far)));
            }
        }));
        at(1927, mc -> look(mc, 22.0, Y + 4.0, GOODS_LINE + 9.0, 222.0F, 16.0F));
        at(1975, mc -> shot(mc, "32_wagons_left"));
        // The livestock wagon, loaded from the pen behind the wagon loader
        at(1977, mc -> look(mc, 14.0, Y + 2.6, STOCK_LINE + 5.0, 232.0F, 14.0F));
        at(2020, mc -> shot(mc, "33_livestock_wagon"));
        // A train on a timetable slowing for a 20 km/h speed sign (taken as it comes up to it); then in its cab, the
        // limit on the gauges
        at(2022, mc -> {
            look(mc, 36.0, Y + 2.2, SPEED_LINE + 7.0, -139.0F, 10.0F);
            server(mc, player -> {
                ServerLevel level = player.serverLevel();
                Locomotive loco = find(level, Locomotive.class, 24.5, SPEED_LINE + 0.5);
                if (level.getBlockEntity(new BlockPos(84, Y, SPEED_LINE - 3)) instanceof StationBlockEntity far) {
                    loco.autopilot().start(List.of(TestTracks.stop(far)));
                }
            });
        });
        boolean[] signShot = {false};
        for (int time = 2062; time <= 2120; time++) {
            boolean last = time == 2120;
            at(time, mc -> {
                List<Locomotive> locos = mc.level.getEntitiesOfClass(Locomotive.class, new AABB(20, Y - 2, SPEED_LINE - 4, 90, Y + 4, SPEED_LINE + 4));
                if (!signShot[0] && (last || (!locos.isEmpty() && locos.getFirst().getX() + locos.getFirst().halfLength() >= 37.5))) {
                    signShot[0] = true;
                    shot(mc, "34_speed_sign");
                }
            });
        }
        at(2122, mc -> server(mc, player -> {
            Locomotive loco = player.serverLevel().getEntitiesOfClass(Locomotive.class,
                    new AABB(20, Y - 2, SPEED_LINE - 4, 90, Y + 4, SPEED_LINE + 4)).getFirst();
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(player.serverLevel(), loco.getX(), loco.getY(), loco.getZ(), -90.0F, 6.0F);
            player.startRiding(loco);
        }));
        at(2128, mc -> {
            mc.player.setYRot(-90.0F);
            mc.player.setXRot(6.0F);
        });
        at(2150, mc -> shot(mc, "35_cab_speed_limit"));
        at(2160, Minecraft::stop);
    }
}
