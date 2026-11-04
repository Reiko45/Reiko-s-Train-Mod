package com.reiko.trains.dev;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.gametest.TestTracks;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModDataComponents;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackBuilder;
import com.reiko.trains.train.DieselLocomotive;
import com.reiko.trains.train.ElectricLocomotive;
import com.reiko.trains.power.ChargingPointBlockEntity;
import com.reiko.trains.train.ExpressLocomotive;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.GuardsVan;
import com.reiko.trains.train.TankWagon;
import com.reiko.trains.train.Tender;
import com.reiko.trains.train.PassengerCoach;
import com.reiko.trains.train.CarTransporter;
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
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.entity.Entity;
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
    /**
     * {@code -PshowcaseFrom=<tick>}: after building the world, skip straight to the steps from that tick (they run from
     * tick 100), to check one scene without waiting through the rest.
     */
    private static final int FROM = Integer.getInteger("reikotrains.showcaseFrom", 0);
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
        if (FROM <= 0 || time < 60) {
            STEPS.add(new Step(time, action));
        } else if (time >= FROM) {
            STEPS.add(new Step(time - FROM + 100, action));
        }
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
    /** The Bullet Train's line, along x at this z, from Capital (x = 10) to Coast (x = 180). */
    private static final int BULLET_LINE = -120;
    /** The overhead line for the electric (along x at this z, curving away south-east at its east end) ... */
    private static final int WIRE_LINE = -156;
    /** ... and the one for a Bullet Train, beside it. */
    private static final int BULLET_WIRE_LINE = -170;
    /** A street with trams, along x at this z (the track down its middle), turning north at its east end. */
    private static final int STREET = -200;
    /** A line with a water tower at its halt, along x at this z. */
    private static final int WATER_LINE = -262;
    /** A turntable yard: the turntable's pivot is at x = 40 on this z, with an engine shed east of it. */
    private static final int TURNTABLE_YARD = -300;
    private static final BlockPos TURNTABLE = new BlockPos(40, Y - 1, TURNTABLE_YARD);
    /** A line with three-light signals on it, along x at this z. */
    private static final int SIGNAL_LINE = -340;
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
        // A ninth line: the Bullet Train, two driving cars and two coaches, standing at Capital.
        TestTracks.line(level, new BlockPos(0, Y, BULLET_LINE), TrackAxis.X, 192);
        TestTracks.station(level, new BlockPos(14, Y, BULLET_LINE - 3), new BlockPos(14, Y, BULLET_LINE), "Capital", 3);
        TestTracks.station(level, new BlockPos(180, Y, BULLET_LINE - 3), new BlockPos(180, Y, BULLET_LINE), "Coast", 3);
        com.reiko.trains.train.BulletTrain bulletFront = TestTracks.car(level, ModEntities.BULLET_TRAIN.get(), new BlockPos(40, Y, BULLET_LINE), EAST);
        com.reiko.trains.train.BulletCoach bulletCoach1 = TestTracks.car(level, ModEntities.BULLET_COACH.get(), new BlockPos(31, Y, BULLET_LINE), EAST);
        com.reiko.trains.train.BulletCoach bulletCoach2 = TestTracks.car(level, ModEntities.BULLET_COACH.get(), new BlockPos(23, Y, BULLET_LINE), EAST);
        com.reiko.trains.train.BulletTrain bulletBack = TestTracks.car(level, ModEntities.BULLET_TRAIN.get(), new BlockPos(14, Y, BULLET_LINE),
                new Vec3(-1, 0, 0));
        Trains.couple(bulletFront, bulletCoach1);
        Trains.couple(bulletCoach1, bulletCoach2);
        Trains.couple(bulletCoach2, bulletBack);
        bulletFront.setFuel(com.reiko.trains.train.BulletTrain.BATTERY_CAPACITY);
        bulletBack.setFuel(com.reiko.trains.train.BulletTrain.BATTERY_CAPACITY);

        // A tenth line, with Reiko's Car Mod: a goods train with two car transporters, and a car to drive on.
        if (com.reiko.trains.train.CarCargo.CAR_MOD) {
            CarShowcase.build(level, Y);
        }

        // An eleventh line, under overhead wires: masts on its north side (the outside of the curve at its east end), a
        // substation feeding it, and an electric with a coach, its batteries flat, running from the wire.
        TestTracks.line(level, new BlockPos(0, Y, WIRE_LINE), TrackAxis.X, 60);
        BlockPos bend = new BlockPos(59, Y, WIRE_LINE);
        TestTracks.connect(level, bend, bend.offset(14, 0, 6));
        TestTracks.line(level, bend.offset(15, 0, 7), TrackAxis.SE, 10);
        List<BlockPos> wireMasts = new ArrayList<>();
        for (BlockPos mast : new BlockPos[]{new BlockPos(4, Y, WIRE_LINE - 2), new BlockPos(22, Y, WIRE_LINE - 2), new BlockPos(40, Y, WIRE_LINE - 2),
                new BlockPos(56, Y, WIRE_LINE - 2), new BlockPos(67, Y, WIRE_LINE - 1), new BlockPos(80, Y, WIRE_LINE + 10)}) {
            wireMasts.add(TestTracks.mast(level, mast).getBlockPos());
        }
        TestTracks.wire(level, wireMasts.toArray(BlockPos[]::new));
        TestTracks.substation(level, new BlockPos(22, Y, WIRE_LINE - 5)).addMj(1_000_000);
        TestTracks.station(level, new BlockPos(52, Y, WIRE_LINE + 3), new BlockPos(52, Y, WIRE_LINE), "Wire End", 10);
        ElectricLocomotive wired = TestTracks.car(level, ModEntities.ELECTRIC_LOCOMOTIVE.get(), new BlockPos(30, Y, WIRE_LINE), EAST);
        PassengerCoach wiredCoach = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), new BlockPos(22, Y, WIRE_LINE), EAST);
        Trains.couple(wired, wiredCoach);
        // And a twelfth beside it, for a Bullet Train: a driving car at each end, a coach between, under the wire.
        TestTracks.line(level, new BlockPos(0, Y, BULLET_WIRE_LINE), TrackAxis.X, 90);
        List<BlockPos> bulletMasts = new ArrayList<>();
        for (int x = 4; x <= 84; x += 20) {
            bulletMasts.add(TestTracks.mast(level, new BlockPos(x, Y, BULLET_WIRE_LINE + 2)).getBlockPos());
        }
        TestTracks.wire(level, bulletMasts.toArray(BlockPos[]::new));
        TestTracks.substation(level, new BlockPos(64, Y, BULLET_WIRE_LINE + 5)).addMj(1_000_000);
        com.reiko.trains.train.BulletTrain wiredFront = TestTracks.car(level, ModEntities.BULLET_TRAIN.get(), new BlockPos(40, Y, BULLET_WIRE_LINE), EAST);
        com.reiko.trains.train.BulletCoach wiredMiddle = TestTracks.car(level, ModEntities.BULLET_COACH.get(), new BlockPos(31, Y, BULLET_WIRE_LINE), EAST);
        com.reiko.trains.train.BulletTrain wiredBack = TestTracks.car(level, ModEntities.BULLET_TRAIN.get(), new BlockPos(22, Y, BULLET_WIRE_LINE),
                new Vec3(-1, 0, 0));
        Trains.couple(wiredFront, wiredMiddle);
        Trains.couple(wiredMiddle, wiredBack);

        // A street, with trams: road along x (asphalt, with Reiko's Car Mod), pavements, street track down the middle
        // turning north at a corner into a side street, wires overhead, a vintage tram and a low-floor one, and (with the
        // car mod) a traffic light and a parked car.
        streetScene(level);

        // A line with a water tower beside its halt, and an express standing there with its tender by the tower.
        TestTracks.line(level, new BlockPos(0, Y, WATER_LINE), TrackAxis.X, 80);
        TestTracks.station(level, new BlockPos(40, Y, WATER_LINE - 3), new BlockPos(40, Y, WATER_LINE), "Water Stop", 10);
        TestTracks.waterTower(level, new BlockPos(28, Y, WATER_LINE + 2), new BlockPos(28, Y, WATER_LINE)).addWater(200_000);
        ExpressLocomotive thirsty = TestTracks.car(level, ModEntities.EXPRESS_LOCOMOTIVE.get(), new BlockPos(36, Y, WATER_LINE), EAST);
        Tender thirstyTender = TestTracks.car(level, ModEntities.TENDER.get(), new BlockPos(28, Y, WATER_LINE), EAST);
        Trains.couple(thirsty, thirstyTender);
        thirstyTender.setFuel(Tender.FUEL_CAPACITY * 0.7);

        turntableYard(level);
        signalLine(level);

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

    /**
     * With Reiko's Car Mod: driving a sedan onto a car transporter (seen from behind it: really driving, the forward key
     * held down), then the train with its cars aboard from beside the line, and going by on its way to Docks.
     */
    private static void carScene() {
        int line = CarShowcase.LINE;
        at(2812, mc -> server(mc, player -> CarShowcase.boardSedan(player, Y)));
        at(2818, mc -> {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            mc.player.setYRot(-90.0F);
            mc.player.setXRot(12.0F);
        });
        at(2840, mc -> mc.options.keyUp.setDown(true));
        at(2875, mc -> mc.options.keyUp.setDown(false));
        at(2895, mc -> shot(mc, "41_driving_on"));
        at(2897, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            look(mc, 30.0, Y + 4.0, line + 9.0, -128.0F, 16.0F);
        });
        at(2940, mc -> shot(mc, "42_car_transporters"));
        at(2942, mc -> {
            look(mc, 86.0, Y + 2.2, line + 6.0, 115.0F, 6.0F);
            server(mc, player -> CarShowcase.depart(player, Y));
        });
        boolean[] passing = {false};
        for (int time = 2990; time <= 3100; time++) {
            boolean last = time == 3100;
            at(time, mc -> {
                List<CarTransporter> wagons = mc.level.getEntitiesOfClass(CarTransporter.class,
                        new AABB(0, Y - 2, line - 4, 120, Y + 4, line + 4));
                double east = wagons.stream().mapToDouble(Entity::getX).max().orElse(0);
                if (!passing[0] && (last || east >= 66)) {
                    passing[0] = true;
                    shot(mc, "43_cars_by_rail");
                }
            });
        }
    }

    private static void streetScene(ServerLevel level) {
        net.minecraft.world.level.block.state.BlockState road = com.reiko.trains.train.CarCargo.CAR_MOD
                ? BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("reikocars", "asphalt")).defaultBlockState()
                : Blocks.GRAY_CONCRETE.defaultBlockState();
        net.minecraft.world.level.block.state.BlockState pavement = Blocks.SMOOTH_STONE.defaultBlockState();
        for (int x = -4; x <= 96; x++) {
            for (int z = STREET - 9; z <= STREET + 9; z++) {
                level.setBlock(new BlockPos(x, Y - 1, z), Math.abs(z - STREET) <= 6 ? road : pavement, 2);
            }
        }
        for (int z = STREET - 48; z < STREET - 6; z++) {
            for (int x = 64; x <= 82; x++) {
                level.setBlock(new BlockPos(x, Y - 1, z), Math.abs(x - 73) <= 6 ? road : pavement, 2);
            }
        }
        TrackBuilder.streetLine(level, new BlockPos(-4, Y - 1, STREET), TrackAxis.X, 74);
        BlockPos corner = new BlockPos(69, Y - 1, STREET);
        TrackBuilder.build(level, TrackBuilder.plan(level, corner, corner.offset(4, 0, -4), Vec3.ZERO, false, true), null);
        TrackBuilder.streetLine(level, new BlockPos(73, Y - 1, STREET - 46), TrackAxis.Z, 42);
        // Masts on the south side of the street and the outside of the corner, then up the side street's east side.
        List<BlockPos> masts = new ArrayList<>();
        for (BlockPos at : new BlockPos[]{new BlockPos(2, Y, STREET + 3), new BlockPos(22, Y, STREET + 3), new BlockPos(42, Y, STREET + 3),
                new BlockPos(60, Y, STREET + 3), new BlockPos(74, Y, STREET + 1), new BlockPos(76, Y, STREET - 12),
                new BlockPos(76, Y, STREET - 30)}) {
            masts.add(TestTracks.mast(level, at).getBlockPos());
            level.setBlock(at.below(), road, 2);
        }
        TestTracks.wire(level, masts.toArray(BlockPos[]::new));
        BlockPos substation = new BlockPos(22, Y, STREET + 7);
        TestTracks.substation(level, substation).addMj(1_000_000);
        level.setBlock(substation.below(), pavement, 2);
        TestTracks.station(level, new BlockPos(10, Y, STREET - 7), new BlockPos(10, Y - 1, STREET), "Market Street", 4);
        level.setBlock(new BlockPos(10, Y - 1, STREET - 7), pavement, 2);
        com.reiko.trains.train.VintageTram vintage = TestTracks.car(level, ModEntities.VINTAGE_TRAM.get(), new BlockPos(14, Y - 1, STREET), EAST);
        vintage.setFuel(com.reiko.trains.train.VintageTram.BATTERY_CAPACITY);
        com.reiko.trains.train.LowFloorTram.place(level, new BlockPos(73, Y - 1, STREET - 4), new Vec3(0, 0, -1));
        for (com.reiko.trains.train.TramCab cab : level.getEntitiesOfClass(com.reiko.trains.train.TramCab.class,
                new AABB(60, Y - 3, STREET - 20, 90, Y + 5, STREET + 10))) {
            cab.setFuel(com.reiko.trains.train.TramCab.BATTERY_CAPACITY);
        }
        if (com.reiko.trains.train.CarCargo.CAR_MOD) {
            // A traffic light on a post on the north pavement, facing west, held on red (set powered, as if by redstone);
            // and a parked car.
            net.minecraft.world.level.block.Block light = BuiltInRegistries.BLOCK.get(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("reikocars", "traffic_light"));
            BlockPos post = new BlockPos(44, Y, STREET - 4);
            level.setBlock(post, Blocks.ANDESITE_WALL.defaultBlockState(), 3);
            level.setBlock(post.above(), Blocks.ANDESITE_WALL.defaultBlockState(), 3);
            net.minecraft.world.level.block.state.BlockState lightState = light.defaultBlockState();
            for (net.minecraft.world.level.block.state.properties.Property<?> property : lightState.getProperties()) {
                if (property.getName().equals("facing")) {
                    lightState = withValue(lightState, property, "west");
                } else if (property.getName().equals("powered")) {
                    lightState = withValue(lightState, property, "true");
                }
            }
            level.setBlock(post.above(2), lightState, 3);
            CarShowcase.car(level, "sedan", new Vec3(30.5, Y, STREET + 4.5), 90.0F);
        }
    }

    private static <T extends Comparable<T>> net.minecraft.world.level.block.state.BlockState withValue(
            net.minecraft.world.level.block.state.BlockState state, net.minecraft.world.level.block.state.properties.Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }

    /**
     * Overhead wires: looking back along the electric's line from beyond its curve, the masts' arms reaching over the
     * track; its pantograph up against the wire, from beside it; the substation feeding the line; the Bullet Train's
     * pantographs up under its wire; and the electric setting off, running from the wire.
     */
    private static void wireScene() {
        at(3112, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.fov().set(70);
            look(mc, 72.0, Y + 4.5, WIRE_LINE + 12.0, 128.0F, 10.0F);
        });
        at(3160, mc -> shot(mc, "44_overhead_line"));
        at(3200, mc -> look(mc, 33.0, Y + 2.0, WIRE_LINE + 6.5, 172.0F, -8.0F));
        at(3238, mc -> shot(mc, "45_pantograph"));
        at(3240, mc -> look(mc, 24.2, Y + 1.5, WIRE_LINE - 8.2, 26.0F, 24.0F));
        at(3278, mc -> shot(mc, "46_substation"));
        at(3280, mc -> look(mc, 48.0, Y + 3.0, BULLET_WIRE_LINE - 7.0, 55.0F, 2.0F));
        at(3318, mc -> shot(mc, "47_bullet_under_wire"));
        at(3320, mc -> {
            look(mc, 62.0, Y + 2.6, WIRE_LINE + 4.5, 105.0F, 6.0F);
            server(mc, player -> {
                ServerLevel level = player.serverLevel();
                ElectricLocomotive electric = find(level, ElectricLocomotive.class, 30.5, WIRE_LINE + 0.5);
                if (level.getBlockEntity(new BlockPos(52, Y, WIRE_LINE + 3)) instanceof StationBlockEntity end) {
                    electric.autopilot().start(List.of(TestTracks.stop(end)));
                }
            });
        });
        boolean[] running = {false};
        for (int time = 3360; time <= 3500; time++) {
            boolean last = time == 3500;
            at(time, mc -> {
                if (running[0]) {
                    return;
                }
                List<ElectricLocomotive> engines = mc.level.getEntitiesOfClass(ElectricLocomotive.class,
                        new AABB(0, Y - 2, WIRE_LINE - 4, 100, Y + 4, WIRE_LINE + 4));
                double front = engines.stream().mapToDouble(e -> e.getX() + e.halfLength()).max().orElse(0);
                if (last || front >= 46) {
                    running[0] = true;
                    ReikoTrains.LOGGER.info("Showcase: electric under the wire shot at x = {} (last: {})", front, last);
                    shot(mc, "48_electric_running_under_wire");
                }
            });
        }
    }

    /**
     * Trams: street track set into the road round the corner; the vintage tram under the wires, its trolley pole up; the
     * low-floor tram bending round the corner; and (with the car mod) the vintage tram waiting at a red light.
     */
    private static void tramScene() {
        at(3506, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.fov().set(70);
            look(mc, 64.0, Y + 2.2, STREET + 5.0, -135.0F, 24.0F);
        });
        at(3562, mc -> shot(mc, "49_street_track"));
        at(3564, mc -> look(mc, 26.0, Y + 1.6, STREET - 6.5, 58.0F, 4.0F));
        at(3604, mc -> shot(mc, "50_vintage_tram"));
        at(3606, mc -> look(mc, 63.0, Y + 3.5, STREET - 15.0, -42.0F, 14.0F));
        at(3646, mc -> shot(mc, "51_low_floor_tram"));
        at(3648, mc -> {
            look(mc, 50.0, Y + 1.8, STREET + 6.0, 125.0F, 4.0F);
            server(mc, player -> {
                ServerLevel level = player.serverLevel();
                com.reiko.trains.train.VintageTram tram = find(level, com.reiko.trains.train.VintageTram.class, 14.5, STREET + 0.5);
                if (level.getBlockEntity(new BlockPos(10, Y, STREET - 7)) instanceof StationBlockEntity market) {
                    // Off east, away from its stop and round the corner: it'll stop for the red light on the way.
                    tram.autopilot().start(List.of(new com.reiko.trains.train.Timetable.Stop(new BlockPos(73, Y - 1, STREET - 40),
                            new BlockPos(73, Y - 1, STREET - 40), "Side Street")));
                }
            });
        });
        boolean[] waiting = {false};
        for (int time = 3700; time <= 3900; time++) {
            boolean last = time == 3900;
            at(time, mc -> {
                if (waiting[0]) {
                    return;
                }
                List<com.reiko.trains.train.VintageTram> trams = mc.level.getEntitiesOfClass(com.reiko.trains.train.VintageTram.class,
                        new AABB(0, Y - 3, STREET - 4, 100, Y + 5, STREET + 4));
                boolean stopped = !trams.isEmpty() && trams.getFirst().getX() > 30 && Math.abs(trams.getFirst().getX() - trams.getFirst().xo) < 0.001;
                if (last || stopped) {
                    waiting[0] = true;
                    ReikoTrains.LOGGER.info("Showcase: tram at the light shot (stopped: {}, last: {})", stopped, last);
                    shot(mc, "52_tram_at_red_light");
                }
            });
        }
    }

    /** Water: an express's tender filling at a water tower, its spout down; then the cab gauges, coal and water. */
    private static void waterScene() {
        at(3930, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.options.fov().set(70);
            look(mc, 24.0, Y + 2.6, WATER_LINE + 5.5, -134.0F, 18.0F);
        });
        // Once it's loaded: the tender's run dry, and the tower fills it.
        boolean[] emptied = {false};
        for (int time = 3955; time <= 3995; time++) {
            at(time, mc -> server(mc, player -> {
                List<Tender> tenders = player.serverLevel().getEntitiesOfClass(Tender.class,
                        new AABB(20, Y - 2, WATER_LINE - 4, 40, Y + 4, WATER_LINE + 4));
                if (!emptied[0] && !tenders.isEmpty()) {
                    emptied[0] = true;
                    tenders.getFirst().setWater(0);
                }
            }));
        }
        at(4005, mc -> shot(mc, "53_water_tower"));
        at(4007, mc -> look(mc, 22.5, Y + 2.0, WATER_LINE + 1.5, -98.0F, 12.0F));
        at(4040, mc -> shot(mc, "53b_water_tower_spout"));
        at(4042, mc -> server(mc, player -> {
            ServerLevel level = player.serverLevel();
            ExpressLocomotive express = find(level, ExpressLocomotive.class, 36.5, WATER_LINE + 0.5);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(level, express.getX(), express.getY(), express.getZ(), -90.0F, 10.0F);
            player.startRiding(express);
        }));
        at(4048, mc -> {
            mc.player.setYRot(-90.0F);
            mc.player.setXRot(10.0F);
        });
        at(4075, mc -> shot(mc, "54_water_gauge"));
    }


    /**
     * A turntable with track off it every way but south-west and west-north-west: the line in from the west, sidings
     * north and south, two diagonal ones, and off east, the road into a brick engine shed with its doors over it. An
     * express and its tender stand on the turntable; a tank engine waits outside the shed; a diesel on the north siding.
     */
    private static void turntableYard(ServerLevel level) {
        int z = TURNTABLE_YARD;
        for (int cx = (TURNTABLE.getX() - 9) >> 4; cx <= (TURNTABLE.getX() + 9) >> 4; cx++) {
            for (int cz = (z - 9) >> 4; cz <= (z + 9) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        TestTracks.line(level, new BlockPos(0, Y, z), TrackAxis.X, 33);
        TestTracks.line(level, new BlockPos(48, Y, z), TrackAxis.X, 34);
        TestTracks.line(level, new BlockPos(40, Y, z - 22), TrackAxis.Z, 15);
        TestTracks.line(level, new BlockPos(40, Y, z + 8), TrackAxis.Z, 14);
        TestTracks.line(level, new BlockPos(46, Y, z - 6), TrackAxis.NE, 8);
        TestTracks.line(level, new BlockPos(46, Y, z + 6), TrackAxis.SE, 8);
        com.reiko.trains.turntable.TurntableBlockEntity table = TestTracks.turntable(level, TURNTABLE, 0);
        ExpressLocomotive express = TestTracks.car(level, ModEntities.EXPRESS_LOCOMOTIVE.get(), new BlockPos(42, Y, z), EAST);
        Tender tender = TestTracks.car(level, ModEntities.TENDER.get(), new BlockPos(34, Y, z), EAST);
        Trains.couple(express, tender);
        DieselLocomotive diesel = TestTracks.car(level, ModEntities.DIESEL_LOCOMOTIVE.get(), new BlockPos(40, Y, z - 14), new Vec3(0, 0, 1));
        diesel.setFuel(DieselLocomotive.FUEL_CAPACITY);
        // The engine shed: brick walls and a roof behind its doors, which face west.
        int doors = 64;
        TestTracks.engineShed(level, new BlockPos(doors, Y, z), net.minecraft.core.Direction.WEST);
        for (int x = doors + 1; x <= doors + 17; x++) {
            for (int y = Y; y < Y + 5; y++) {
                level.setBlock(new BlockPos(x, y, z - 2), Blocks.BRICKS.defaultBlockState(), 2);
                level.setBlock(new BlockPos(x, y, z + 2), Blocks.BRICKS.defaultBlockState(), 2);
                if (x == doors + 17) {
                    for (int dz = -1; dz <= 1; dz++) {
                        level.setBlock(new BlockPos(x, y, z + dz), Blocks.BRICKS.defaultBlockState(), 2);
                    }
                }
                if ((x - doors) % 4 == 2 && y == Y + 2) {
                    level.setBlock(new BlockPos(x, y, z - 2), Blocks.GLASS_PANE.defaultBlockState(), 2);
                    level.setBlock(new BlockPos(x, y, z + 2), Blocks.GLASS_PANE.defaultBlockState(), 2);
                }
            }
        }
        // A low pitched roof of slabs, half a block up each step, with the gable ends bricked up.
        net.minecraft.world.level.block.state.BlockState bottom = Blocks.DARK_OAK_SLAB.defaultBlockState();
        net.minecraft.world.level.block.state.BlockState top = bottom.setValue(net.minecraft.world.level.block.SlabBlock.TYPE,
                net.minecraft.world.level.block.state.properties.SlabType.TOP);
        for (int x = doors - 1; x <= doors + 18; x++) {
            for (int dz = -3; dz <= 3; dz++) {
                int step = 3 - Math.abs(dz);
                level.setBlock(new BlockPos(x, Y + 5 + step / 2, z + dz), step % 2 == 0 ? bottom : top, 2);
            }
            if (x == doors || x == doors + 17) {
                for (int dz = -1; dz <= 1; dz++) {
                    level.setBlock(new BlockPos(x, Y + 5, z + dz), Blocks.BRICKS.defaultBlockState(), 2);
                }
            }
        }
        TestTracks.station(level, new BlockPos(76, Y, z - 3), new BlockPos(76, Y, z), "Engine Shed", 10);
        com.reiko.trains.shed.EngineShedBlockEntity shed = (com.reiko.trains.shed.EngineShedBlockEntity) level.getBlockEntity(
                new BlockPos(doors, Y + 4, z));
        shed.items().insertItem(0, new ItemStack(Items.COAL, 64), false);
        shed.fluids().fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 64_000),
                net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        Locomotive shunter = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), new BlockPos(51, Y, z), EAST);
        shunter.setFuel(16000);
    }

    /**
     * The turntable turning the express round from the line in to the north siding, seen from above; then close up
     * once it's there; then the tank engine going into the engine shed (its doors opening for it), and the doors shut
     * behind it.
     */
    private static void turntableScene() {
        int z = TURNTABLE_YARD;
        at(4100, mc -> look(mc, 29.5, Y + 6.5, z + 10.5, -135.0F, 28.0F));
        at(4145, mc -> server(mc, player -> {
            if (player.serverLevel().getBlockEntity(TURNTABLE) instanceof com.reiko.trains.turntable.TurntableBlockEntity table) {
                ReikoTrains.LOGGER.info("Showcase: turntable turning: {}", table.turn(-2));
            }
        }));
        at(4178, mc -> shot(mc, "55_turntable_turning"));
        at(4180, mc -> look(mc, 48.5, Y + 1.4, z + 8.5, 140.0F, 10.0F));
        at(4230, mc -> shot(mc, "56_turntable"));
        at(4232, mc -> look(mc, 55.0, Y + 1.0, z + 6.0, -121.0F, 6.0F));
        at(4240, mc -> server(mc, player -> {
            Locomotive shunter = find(player.serverLevel(), com.reiko.trains.train.SteamLocomotive.class, 51.5, z + 0.5);
            StationBlockEntity shedStop = (StationBlockEntity) player.serverLevel().getBlockEntity(new BlockPos(76, Y, z - 3));
            shunter.autopilot().start(List.of(TestTracks.stop(shedStop)));
        }));
        boolean[] entering = {false};
        for (int time = 4260; time <= 4380; time++) {
            boolean last = time == 4380;
            at(time, mc -> {
                if (entering[0]) {
                    return;
                }
                List<com.reiko.trains.train.SteamLocomotive> locos = mc.level.getEntitiesOfClass(com.reiko.trains.train.SteamLocomotive.class,
                        new AABB(44, Y - 2, z - 3, 90, Y + 4, z + 3));
                double front = locos.stream().mapToDouble(car -> car.getX() + car.halfLength()).max().orElse(0);
                if (last || front >= 62.0) {
                    entering[0] = true;
                    shot(mc, "57_engine_shed");
                }
            });
        }
        at(4440, mc -> look(mc, 55.5, Y + 0.6, z + 3.5, -110.0F, 2.0F));
        at(4470, mc -> shot(mc, "57b_engine_shed_shut"));
    }


    /**
     * Three-light signals: three in a row (x = 60, 80, 100) guarding the way east, a goods train standing beyond the
     * last, so it's red, the one before it yellow and the first green; and an electric with a coach to come along.
     */
    private static void signalLine(ServerLevel level) {
        int z = SIGNAL_LINE;
        TestTracks.line(level, new BlockPos(0, Y, z), TrackAxis.X, 200);
        for (int x : new int[] {60, 80, 100}) {
            TestTracks.threeLightSignal(level, new BlockPos(x, Y, z + 2), new BlockPos(x, Y, z), 1);
        }
        Locomotive goods = TestTracks.car(level, ModEntities.DIESEL_LOCOMOTIVE.get(), new BlockPos(150, Y, z), EAST);
        FreightWagon first = TestTracks.car(level, ModEntities.FREIGHT_WAGON.get(), new BlockPos(142, Y, z), EAST);
        FreightWagon second = TestTracks.car(level, ModEntities.FREIGHT_WAGON.get(), new BlockPos(136, Y, z), EAST);
        Trains.couple(goods, first);
        Trains.couple(first, second);
        TestTracks.station(level, new BlockPos(185, Y, z - 3), new BlockPos(185, Y, z), "Goods Yard", 10);
        ElectricLocomotive electric = TestTracks.car(level, ModEntities.ELECTRIC_LOCOMOTIVE.get(), new BlockPos(13, Y, z), EAST);
        PassengerCoach coach = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), new BlockPos(5, Y, z), EAST);
        Trains.couple(electric, coach);
        electric.setFuel(electric.batteryCapacity());
    }

    /**
     * Three-light signals: the three in a row, green, yellow and red; then in the electric's cab coming up to the yellow
     * one, slowing to caution speed (the gauges show it); then the train stopped at the red one.
     */
    private static void signalScene() {
        int z = SIGNAL_LINE;
        at(4480, mc -> {
            mc.options.fov().set(30); // a long lens, to see the three in a row
            look(mc, 40.5, Y + 0.6, z + 9.5, -100.8F, 2.0F);
        });
        at(4530, mc -> shot(mc, "58_three_light_signals"));
        at(4532, mc -> server(mc, player -> {
            ServerLevel level = player.serverLevel();
            ElectricLocomotive electric = find(level, ElectricLocomotive.class, 13.5, z + 0.5);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(level, electric.getX(), electric.getY(), electric.getZ(), -90.0F, 4.0F);
            player.startRiding(electric);
            StationBlockEntity yard = (StationBlockEntity) level.getBlockEntity(new BlockPos(185, Y, z - 3));
            electric.autopilot().start(List.of(TestTracks.stop(yard)));
        }));
        at(4538, mc -> {
            mc.options.fov().set(70);
            mc.player.setYRot(-90.0F);
            mc.player.setXRot(4.0F);
        });
        boolean[] cab = {false};
        for (int time = 4560; time <= 4760; time++) {
            boolean last = time == 4760;
            at(time, mc -> {
                if (cab[0] || !(mc.player.getVehicle() instanceof Locomotive loco)) {
                    return;
                }
                if (last || (loco.signalAspect() == 3 && loco.getX() + loco.halfLength() >= 66)) {
                    cab[0] = true;
                    shot(mc, "59_caution_cab");
                }
            });
        }
        at(4765, mc -> look(mc, 89.5, Y + 0.6, z + 7.5, -118.0F, 3.0F));
        boolean[] stopped = {false};
        for (int time = 4800; time <= 4950; time++) {
            boolean last = time == 4950;
            at(time, mc -> {
                if (stopped[0]) {
                    return;
                }
                List<ElectricLocomotive> trains = mc.level.getEntitiesOfClass(ElectricLocomotive.class, new AABB(60, Y - 2, z - 3, 110, Y + 4, z + 3));
                if (last || (!trains.isEmpty() && Math.abs(trains.getFirst().getX() - trains.getFirst().xo) < 1.0E-4)) {
                    stopped[0] = true;
                    shot(mc, "58b_three_light_red");
                }
            });
        }
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
        // With Xaero's Minimap (run with -PxaeroJars=...): the trains on the minimap, and marked in the world, looking
        // out over the crossing line to the main line and the loop
        at(2152, mc -> look(mc, 22.0, Y + 12.0, -52.0, -35.0F, 22.0F));
        at(2200, mc -> shot(mc, "36_trains_on_minimap"));
        // The Bullet Train: standing at Capital, then its nose close up
        at(2202, mc -> look(mc, 52.0, Y + 3.0, BULLET_LINE + 6.0, 116.0F, 12.0F));
        at(2245, mc -> shot(mc, "37_bullet_train"));
        at(2247, mc -> look(mc, 48.0, Y + 1.3, BULLET_LINE + 3.0, 130.0F, 6.0F));
        at(2287, mc -> shot(mc, "38_bullet_nose"));
        // In its cab, off to Coast on a timetable; then from beside the line as it comes back, the other car leading
        at(2289, mc -> server(mc, player -> {
            ServerLevel level = player.serverLevel();
            com.reiko.trains.train.BulletTrain front = find(level, com.reiko.trains.train.BulletTrain.class, 40.5, BULLET_LINE + 0.5);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(level, front.getX(), front.getY(), front.getZ(), -90.0F, 8.0F);
            player.startRiding(front);
            if (level.getBlockEntity(new BlockPos(14, Y, BULLET_LINE - 3)) instanceof StationBlockEntity capital
                    && level.getBlockEntity(new BlockPos(180, Y, BULLET_LINE - 3)) instanceof StationBlockEntity coast) {
                front.autopilot().start(List.of(TestTracks.stop(coast), TestTracks.stop(capital)));
            }
        }));
        at(2295, mc -> {
            mc.player.setYRot(-90.0F);
            mc.player.setXRot(8.0F);
        });
        at(2400, mc -> shot(mc, "39_bullet_cab"));
        at(2402, mc -> {
            look(mc, 90.0, Y + 1.8, BULLET_LINE + 5.5, -104.0F, 3.0F);
            mc.options.fov().set(30); // a long lens, to fill the picture with the train
        });
        boolean[] bulletShot = {false};
        for (int time = 2450; time <= 2800; time++) {
            boolean last = time == 2800;
            at(time, mc -> {
                if (bulletShot[0]) {
                    return;
                }
                List<com.reiko.trains.train.BulletTrain> cars = mc.level.getEntitiesOfClass(com.reiko.trains.train.BulletTrain.class,
                        new AABB(0, Y - 2, BULLET_LINE - 4, 200, Y + 4, BULLET_LINE + 4));
                double west = cars.stream().mapToDouble(car -> car.getX() - car.halfLength()).min().orElse(1000);
                boolean back = cars.stream().anyMatch(car -> car.getX() - car.xo < -0.2);
                if (last || (back && west <= 112)) {
                    bulletShot[0] = true;
                    ReikoTrains.LOGGER.info("Showcase: Bullet Train shot at x = {} (moving back: {}, last: {})", west, back, last);
                    shot(mc, "40_bullet_running");
                }
            });
        }
        at(2805, mc -> mc.options.fov().set(70));
        if (com.reiko.trains.train.CarCargo.CAR_MOD) {
            carScene();
        }
        wireScene();
        tramScene();
        waterScene();
        turntableScene();
        signalScene();
        at(4955, Minecraft::stop);
    }
}
