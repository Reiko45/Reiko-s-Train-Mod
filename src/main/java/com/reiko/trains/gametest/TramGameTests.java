package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModBlocks;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackBuilder;
import com.reiko.trains.track.TrackEdge;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.train.ElectricLocomotive;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.LowFloorTram;
import com.reiko.trains.train.Locomotive;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import com.reiko.trains.train.TramCab;
import com.reiko.trains.train.TramMiddle;
import com.reiko.trains.train.VintageTram;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for street track and trams: the two trams, running from the wire, and street rules. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class TramGameTests {
    private static final String LINE = "line";
    private static final String YARD = "yard";
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static final Vec3 WEST = new Vec3(-1, 0, 0);

    private TramGameTests() {
    }

    /** Street track along x at z = 4, set into the floor (y = 0), the whole 64 blocks. */
    private static void street(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 64, 9);
        TrackBuilder.streetLine(level, at(helper, 0), TrackAxis.X, 64);
    }

    /** The street track block at {@code x}. */
    private static BlockPos at(GameTestHelper helper, int x) {
        return helper.absolutePos(new BlockPos(x, 0, 4));
    }

    private static VintageTram vintage(GameTestHelper helper, int x) {
        return TestTracks.car(helper.getLevel(), ModEntities.VINTAGE_TRAM.get(), at(helper, x), EAST);
    }

    private static Player board(GameTestHelper helper, Locomotive loco) {
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(loco.position());
        driver.startRiding(loco);
        return driver;
    }

    private static void throttle(Locomotive loco, int notches) {
        for (int i = 0; i < Math.abs(notches); i++) {
            loco.control(notches > 0 ? TrainControlPayload.THROTTLE_UP : TrainControlPayload.THROTTLE_DOWN);
        }
    }

    private static void shut(Locomotive loco) {
        loco.setThrottle(0);
    }

    /** Where the front of a car is (its leading buffer, going east), along x. */
    private static double frontX(TrainCarEntity car) {
        return car.getX() + car.halfLength();
    }

    private static ArmorStand standIn(GameTestHelper helper, int x) {
        ArmorStand stand = EntityType.ARMOR_STAND.create(helper.getLevel());
        BlockPos pos = at(helper, x);
        stand.moveTo(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 0, 0);
        helper.getLevel().addFreshEntity(stand);
        return stand;
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void streetTrackIsRoadWithTheRailsInIt(GameTestHelper helper) {
        street(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = at(helper, 20);
        helper.assertTrue(TrackGraph.street(level, pos) && TrackGraph.axis(level, pos) == TrackAxis.X, "it should be street track, along x");
        helper.assertTrue(Math.abs(TrackGraph.point(level, pos).y - (pos.getY() + TrackEdge.STREET_RAIL_HEIGHT)) < 1.0E-9,
                "its rails should be level with its top");
        helper.assertTrue(level.getBlockState(pos).isCollisionShapeFullBlock(level, pos), "it should be a whole, solid block, to drive over");
        helper.assertTrue(TrackGraph.edgesFrom(level, pos, 1).size() == 1 && TrackGraph.edgesFrom(level, pos, -1).size() == 1,
                "blocks in a line should join up");
        VintageTram tram = vintage(helper, 20);
        helper.assertTrue(Math.abs(tram.getY() - (pos.getY() + 1.0)) < 1.0E-6, "a tram on it should stand on the road, not " + tram.getY());
        ArmorStand stand = standIn(helper, 40);
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(Math.abs(stand.getY() - (pos.getY() + 1.0)) < 0.01, "and things stand on it, not fall through: " + stand.getY());
            helper.succeed();
        });
    }

    @GameTest(template = YARD)
    public static void streetCornersCanBeTighterAndSetIntoTheRoad(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        BlockPos start = helper.absolutePos(new BlockPos(10, 0, 10));
        level.setBlock(start, ModBlocks.STREET_TRACK.get().defaultBlockState(), 3);
        // A radius-4 quarter turn, its end set into the road block clicked.
        BlockPos road = start.offset(4, 0, 4);
        TrackBuilder.Plan corner = TrackBuilder.plan(level, start, road, Vec3.ZERO, false, true);
        helper.assertTrue(corner.ok() && corner.newEnd() && corner.endStreet(), "a radius-4 street corner should be fine: " + corner.problem());
        helper.assertTrue(corner.payItem() == ModItems.STREET_TRACK.get(), "paid for in street track");
        helper.assertTrue(TrackBuilder.build(level, corner, null), "it should build");
        helper.assertTrue(TrackGraph.street(level, road) && TrackGraph.axis(level, road) == TrackAxis.Z, "its end set into the road, along z");
        helper.assertTrue(TrackBuilder.plan(level, road, road.offset(2, 0, 2), Vec3.ZERO, false, true).problem() != null,
                "a radius-2 corner is too tight even for street track");
        // The same corner in train track is too tight.
        BlockPos track = helper.absolutePos(new BlockPos(30, 1, 10));
        level.setBlock(track, ModBlocks.TRACK.get().defaultBlockState(), 3);
        helper.assertTrue(!TrackBuilder.plan(level, track, track.offset(4, 0, 4), Vec3.ZERO, false).ok(), "a radius-4 train track bend is too sharp");
        // A ramp from street track up to train track is train track.
        BlockPos ramp = helper.absolutePos(new BlockPos(10, 1, 30));
        level.setBlock(ramp, ModBlocks.TRACK.get().defaultBlockState(), 3);
        level.setBlock(ramp.offset(-12, -1, 0), ModBlocks.STREET_TRACK.get().defaultBlockState(), 3);
        TrackBuilder.Plan up = TrackBuilder.plan(level, ramp.offset(-12, -1, 0), ramp, Vec3.ZERO, false, true);
        helper.assertTrue(up.ok() && up.payItem() == ModItems.TRACK.get(), "street track to train track is paid in train track: " + up.problem());
        helper.succeed();
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void vintageTramRunsFromTheWireWithItsPoleTrailing(GameTestHelper helper) {
        street(helper);
        ServerLevel level = helper.getLevel();
        List<BlockPos> masts = List.of(TestTracks.mast(level, helper.absolutePos(new BlockPos(2, 1, 6))).getBlockPos(),
                TestTracks.mast(level, helper.absolutePos(new BlockPos(30, 1, 6))).getBlockPos(),
                TestTracks.mast(level, helper.absolutePos(new BlockPos(58, 1, 6))).getBlockPos());
        TestTracks.wire(level, masts.toArray(BlockPos[]::new));
        TestTracks.substation(level, helper.absolutePos(new BlockPos(30, 1, 8))).addMj(1_000_000);
        VintageTram tram = vintage(helper, 20);
        helper.assertTrue(tram.fuel() == 0, "its battery should start flat");
        Player driver = board(helper, tram);
        double startX = tram.getX();
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(tram.isOnWire() && tram.fuel() > 0, "under a live wire its pole should be up and its battery charging");
            throttle(tram, 4);
        });
        helper.runAfterDelay(80, () -> {
            helper.assertTrue(tram.getX() > startX + 3, "it should run from the wire");
            helper.assertTrue(tram.isPoleBack() && tram.isOnWire(), "with its pole trailing behind it, on the wire");
            double kmh = Math.abs(tram.trainSpeed()) * 72;
            helper.assertTrue(kmh < 45, "a tram's no express: " + kmh + " km/h");
            shut(tram);
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void vintageTramIsDrivenFromWhicheverEndYouGetInAt(GameTestHelper helper) {
        street(helper);
        VintageTram tram = vintage(helper, 30);
        tram.setFuel(VintageTram.BATTERY_CAPACITY);
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(tram.position().add(-4, 0, 1.5));
        tram.interact(driver, InteractionHand.MAIN_HAND);
        helper.assertTrue(tram.driver() == driver && tram.cabSign() == -1, "getting in at the back end, it should be driven from there");
        helper.assertTrue(tram.seats()[0].forward() < 0, "with the driver at the back controller");
        double startX = tram.getX();
        throttle(tram, 4);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(tram.getX() < startX - 2, "the throttle on should take it off backwards (west), the way the driver faces");
            helper.assertTrue(!tram.isPoleBack(), "its pole swung round to trail behind (off its front)");
            shut(tram);
            tram.control(TrainControlPayload.BRAKE_ON);
        });
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(Math.abs(tram.trainSpeed()) < 0.001, "it should have stopped");
            driver.stopRiding();
            Player other = helper.makeMockPlayer(GameType.SURVIVAL);
            other.moveTo(tram.position().add(4, 0, 1.5));
            tram.interact(other, InteractionHand.MAIN_HAND);
            helper.assertTrue(tram.cabSign() == 1, "getting in at the front, the front leads again");
            other.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void lowFloorTramGoesOnAndComesOffAsOne(GameTestHelper helper) {
        street(helper);
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(Vec3.atCenterOf(at(helper, 25)).add(-3, 1, 0));
        player.setYRot(-90);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.LOW_FLOOR_TRAM.get()));
        BlockPos pos = at(helper, 30);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the item should be used up");
        List<TramMiddle> middles = level.getEntitiesOfClass(TramMiddle.class, new AABB(pos).inflate(3));
        helper.assertTrue(middles.size() == 1, "its middle section should be on the track there");
        TramMiddle middle = middles.getFirst();
        List<TrainCarEntity> cars = Trains.train(middle);
        helper.assertTrue(cars.size() == 3 && cars.get(0) instanceof TramCab && cars.get(2) instanceof TramCab, "a driving section either side, coupled");
        TramCab east = (TramCab) (cars.get(0).getX() > middle.getX() ? cars.get(0) : cars.get(2));
        TramCab west = (TramCab) (east == cars.get(0) ? cars.get(2) : cars.get(0));
        helper.assertTrue(east.flatForward().x > 0.99 && west.flatForward().x < -0.99, "noses out, the front one facing the way the player looked");
        double gap = (east.getX() - east.halfLength()) - (middle.getX() + middle.halfLength());
        helper.assertTrue(gap > 0 && gap < 0.2, "the sections close together, bellows to bellows: " + gap);
        middle.derail();
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(east.isRemoved() && west.isRemoved() && middle.isRemoved(), "breaking one section should take up the whole tram");
            int items = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(8)).stream()
                    .filter(e -> e.getItem().is(ModItems.LOW_FLOOR_TRAM.get())).mapToInt(e -> e.getItem().getCount()).sum();
            helper.assertTrue(items == 1, "as one tram, dropped " + items);
            helper.succeed();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 400)
    public static void lowFloorTramBendsRoundAStreetCorner(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestTracks.floor(level, helper.absolutePos(new BlockPos(0, 1, 0)), 48, 48);
        BlockPos corner = helper.absolutePos(new BlockPos(24, 0, 6));
        TrackBuilder.streetLine(level, helper.absolutePos(new BlockPos(2, 0, 6)), TrackAxis.X, 23);
        BlockPos down = corner.offset(4, 0, 4);
        helper.assertTrue(TrackBuilder.build(level, TrackBuilder.plan(level, corner, down, Vec3.ZERO, false, true), null), "the corner should build");
        TrackBuilder.streetLine(level, down.offset(0, 0, 1), TrackAxis.Z, 30);
        helper.assertTrue(LowFloorTram.place(level, helper.absolutePos(new BlockPos(12, 0, 6)), EAST) == null, "the tram should go on");
        List<TramCab> cabs = level.getEntitiesOfClass(TramCab.class, new AABB(helper.absolutePos(new BlockPos(12, 0, 6))).inflate(8));
        TramCab front = cabs.stream().filter(c -> c.flatForward().x > 0.5).findFirst().orElseThrow();
        front.setFuel(TramCab.BATTERY_CAPACITY);
        for (TramCab cab : cabs) {
            cab.setFuel(TramCab.BATTERY_CAPACITY);
        }
        Player driver = board(helper, front);
        throttle(front, 2);
        helper.succeedWhen(() -> {
            List<TrainCarEntity> cars = Trains.train(front);
            helper.assertTrue(cars.size() == 3 && cars.stream().allMatch(c -> !c.isRemoved() && c.isOnTrack()), "the tram should stay together, on the track");
            helper.assertTrue(front.getZ() > down.getZ() + 6, "until it's round the corner and away down the next street");
            driver.stopRiding();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 400)
    public static void tramStopsForSomethingOnTheTrack(GameTestHelper helper) {
        street(helper);
        VintageTram tram = vintage(helper, 8);
        tram.setFuel(VintageTram.BATTERY_CAPACITY);
        Player driver = board(helper, tram);
        throttle(tram, 4);
        ArmorStand stand = standIn(helper, 40);
        double standX = stand.getX();
        helper.runAfterDelay(240, () -> {
            double gap = standX - 0.25 - frontX(tram);
            helper.assertTrue(Math.abs(tram.trainSpeed()) < 0.001, "it should have stopped for the armour stand on the track");
            helper.assertTrue(gap > 0.4 && gap < 2.5, "a block or so short of it, not " + gap);
            helper.assertTrue(Math.abs(stand.getX() - standX) < 0.01, "without touching it");
            stand.discard();
        });
        helper.runAfterDelay(320, () -> {
            helper.assertTrue(frontX(tram) > standX + 3, "with it gone, it should go on");
            shut(tram);
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 400)
    public static void timetabledTramWaitsForATrainInTheWay(GameTestHelper helper) {
        street(helper);
        ServerLevel level = helper.getLevel();
        StationBlockEntity stop = TestTracks.station(level, helper.absolutePos(new BlockPos(56, 1, 1)), at(helper, 56), "Terminus", 5);
        FreightWagon wagon = TestTracks.car(level, ModEntities.FREIGHT_WAGON.get(), at(helper, 36), EAST);
        LowFloorTram.place(level, at(helper, 12), EAST);
        TramCab front = level.getEntitiesOfClass(TramCab.class, new AABB(at(helper, 12)).inflate(8)).stream()
                .filter(c -> c.flatForward().x > 0.5).findFirst().orElseThrow();
        for (TrainCarEntity car : Trains.train(front)) {
            if (car instanceof TramCab cab) {
                cab.setFuel(TramCab.BATTERY_CAPACITY);
            }
        }
        front.autopilot().start(List.of(TestTracks.stop(stop)));
        double wagonBack = wagon.getX() - wagon.halfLength();
        helper.runAfterDelay(240, () -> {
            double gap = wagonBack - frontX(front);
            helper.assertTrue(Math.abs(front.trainSpeed()) < 0.001 && gap > 0.4 && gap < 2.5, "it should wait short of the wagon, not " + gap);
            helper.assertTrue(Trains.train(front).size() == 3, "without coupling to it");
            helper.assertTrue(front.status().getString().contains("something"), "saying why: " + front.status().getString());
            wagon.discard();
        });
        helper.runAfterDelay(380, () -> {
            helper.assertTrue(frontX(front) > wagonBack + 8, "with the wagon gone, it should carry on to its stop");
            front.autopilot().stop();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void trainsDontKeepToStreetRules(GameTestHelper helper) {
        street(helper);
        ElectricLocomotive electric = TestTracks.car(helper.getLevel(), ModEntities.ELECTRIC_LOCOMOTIVE.get(), at(helper, 10), EAST);
        electric.setFuel(ElectricLocomotive.BATTERY_CAPACITY);
        Player driver = board(helper, electric);
        throttle(electric, 2);
        ArmorStand stand = standIn(helper, 30);
        double standX = stand.getX();
        helper.succeedWhen(() -> {
            helper.assertTrue(frontX(electric) > standX + 2, "an ordinary train should push on past, as before");
            driver.stopRiding();
            shut(electric);
        });
    }

    /** A traffic light from Reiko's Car Mod, if it's loaded: facing {@code facing}, held on red by redstone if {@code red}. */
    private static BlockPos trafficLight(GameTestHelper helper, Block light, int x, int z, Direction facing, boolean red) {
        BlockPos pos = helper.absolutePos(new BlockPos(x, 3, z));
        ServerLevel level = helper.getLevel();
        level.setBlock(pos.below(), red ? Blocks.REDSTONE_BLOCK.defaultBlockState() : Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(pos.below(2), Blocks.STONE.defaultBlockState(), 3);
        BlockState state = light.defaultBlockState();
        for (Property<?> property : state.getProperties()) {
            if (property.getName().equals("facing")) {
                state = with(state, property, facing.getSerializedName());
            } else if (property.getName().equals("powered")) {
                state = with(state, property, red ? "true" : "false");
            } else if (property.getName().equals("signal") && red) {
                state = with(state, property, "red");
            }
        }
        level.setBlock(pos, state, 3);
        return pos;
    }

    private static <T extends Comparable<T>> BlockState with(BlockState state, Property<T> property, String value) {
        return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
    }

    @GameTest(template = LINE, timeoutTicks = 1000)
    public static void tramStopsAtARedTrafficLight(GameTestHelper helper) {
        Block light = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("reikocars", "traffic_light"));
        if (light == Blocks.AIR) {
            helper.succeed(); // needs Reiko's Car Mod
            return;
        }
        street(helper);
        // One facing the other way (for traffic going west) doesn't concern it; one facing it, held red, does.
        trafficLight(helper, light, 22, 1, Direction.EAST, true);
        BlockPos red = trafficLight(helper, light, 40, 1, Direction.WEST, true);
        VintageTram tram = vintage(helper, 8);
        tram.setFuel(VintageTram.BATTERY_CAPACITY);
        Player driver = board(helper, tram);
        throttle(tram, 4);
        helper.runAfterDelay(240, () -> {
            double gap = red.getX() + 0.5 - frontX(tram);
            helper.assertTrue(Math.abs(tram.trainSpeed()) < 0.001 && gap > 0.8 && gap < 3, "it should wait short of the red light, not " + gap);
            helper.getLevel().setBlock(red.below(), Blocks.STONE.defaultBlockState(), 3);
        });
        // Let go, the light goes round its phases to green, and the tram goes on.
        helper.runAfterDelay(260, () -> helper.succeedWhen(() -> {
            helper.assertTrue(frontX(tram) > red.getX() + 3, "on green it should go on");
            shut(tram);
            driver.stopRiding();
        }));
    }

    @GameTest(template = LINE)
    public static void tramsAreCraftable(GameTestHelper helper) {
        for (String name : List.of("street_track", "vintage_tram", "low_floor_tram")) {
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ReikoTrains.id(name)).isPresent(), "no recipe for " + name);
        }
        helper.assertTrue(TrainsConfig.WIRE_CHARGE_RATE.get() > 0, "trams charge from the wire");
        helper.succeed();
    }
}
