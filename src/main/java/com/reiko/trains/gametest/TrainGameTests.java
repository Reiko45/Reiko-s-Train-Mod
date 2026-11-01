package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.item.TrainCarItem;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.PassengerCoach;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.TrainCar;
import com.reiko.trains.train.Trains;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Game tests: ./gradlew runGameTestServer. Each test lays a straight line of rails along x (a 48-block-long test
 * area) and runs trains on it.
 */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class TrainGameTests {
    private static final String TRACK = "track";
    private static final int TRACK_Z = 3;

    private TrainGameTests() {
    }

    private static void layTrack(GameTestHelper helper) {
        for (int x = 0; x < 48; x++) {
            helper.setBlock(new BlockPos(x, 0, TRACK_Z), Blocks.STONE);
        }
        for (int x = 0; x < 48; x++) {
            helper.setBlock(new BlockPos(x, 1, TRACK_Z), Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST));
        }
    }

    private static <T extends AbstractMinecart> T place(GameTestHelper helper, EntityType<T> type, int x, boolean facingEast) {
        BlockPos pos = helper.absolutePos(new BlockPos(x, 1, TRACK_Z));
        T car = TrainCarItem.place(helper.getLevel(), type, pos, RailShape.EAST_WEST, new Vec3(facingEast ? 1 : -1, 0, 0), ItemStack.EMPTY, null);
        helper.assertTrue(car != null, "couldn't place " + type);
        return car;
    }

    private static Player board(GameTestHelper helper, SteamLocomotive locomotive) {
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(locomotive.position());
        helper.assertTrue(driver.startRiding(locomotive), "the driver couldn't get in");
        helper.assertTrue(locomotive.driver() == driver, "the first one aboard should be the driver");
        return driver;
    }

    private static void fullAhead(SteamLocomotive locomotive) {
        for (int i = 0; i < SteamLocomotive.MAX_THROTTLE; i++) {
            locomotive.control(TrainControlPayload.THROTTLE_UP);
        }
    }

    private static void assertSpacing(GameTestHelper helper, TrainCar front, TrainCar back, double tolerance) {
        double distance = front.cart().distanceTo(back.cart());
        double spacing = Trains.spacing(front, back);
        helper.assertTrue(Math.abs(distance - spacing) < tolerance,
                String.format("cars should be %.2f apart, were %.2f", spacing, distance));
    }

    @GameTest(template = TRACK, timeoutTicks = 200)
    public static void locomotiveFacesTheWayItWasPlaced(GameTestHelper helper) {
        layTrack(helper);
        SteamLocomotive east = place(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 10, true);
        SteamLocomotive west = place(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 30, false);
        helper.assertTrue(Trains.facing(east).x > 0.99, "should face east, faces " + Trains.facing(east));
        helper.assertTrue(Trains.facing(west).x < -0.99, "should face west, faces " + Trains.facing(west));
        helper.succeed();
    }

    @GameTest(template = TRACK, timeoutTicks = 200)
    public static void locomotiveRunsOnSteam(GameTestHelper helper) {
        layTrack(helper);
        SteamLocomotive locomotive = place(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 4, true);
        Player driver = board(helper, locomotive);
        locomotive.setFuel(1600);
        fullAhead(locomotive);
        double startX = locomotive.getX();
        helper.runAfterDelay(100, () -> {
            double moved = locomotive.getX() - startX;
            helper.assertTrue(moved > 8.0, "should have steamed east, moved " + moved);
            helper.assertTrue(Trains.facing(locomotive).x > 0.99, "should still face forwards");
            helper.assertTrue(locomotive.getFuel() < 1600, "should have burnt fuel");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = TRACK, timeoutTicks = 200)
    public static void locomotiveNeedsFuel(GameTestHelper helper) {
        layTrack(helper);
        SteamLocomotive locomotive = place(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 10, true);
        Player driver = board(helper, locomotive);
        fullAhead(locomotive);
        double startX = locomotive.getX();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(Math.abs(locomotive.getX() - startX) < 0.1, "shouldn't move without fuel");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = TRACK, timeoutTicks = 200)
    public static void nobodyDrivingHoldsTheTrain(GameTestHelper helper) {
        layTrack(helper);
        SteamLocomotive locomotive = place(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 10, true);
        locomotive.setFuel(1600);
        locomotive.setDeltaMovement(0.3, 0, 0);
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(locomotive.getDeltaMovement().horizontalDistance() < 0.01, "a driverless locomotive should brake to a stop");
            helper.assertTrue(locomotive.getFuel() == 1600, "a driverless locomotive shouldn't burn fuel");
            helper.succeed();
        });
    }

    @GameTest(template = TRACK, timeoutTicks = 300)
    public static void coupledCarsFollowAtTheirSpacing(GameTestHelper helper) {
        layTrack(helper);
        SteamLocomotive locomotive = place(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 14, true);
        PassengerCoach coach = place(helper, ModEntities.PASSENGER_COACH.get(), 11, true);
        FreightWagon wagon = place(helper, ModEntities.FREIGHT_WAGON.get(), 7, true);
        helper.assertTrue(Trains.couple(locomotive, coach) == Trains.CoupleResult.COUPLED, "couldn't couple the coach");
        helper.assertTrue(Trains.couple(coach, wagon) == Trains.CoupleResult.COUPLED, "couldn't couple the wagon");
        helper.assertTrue(Trains.train(wagon).equals(List.of(locomotive, coach, wagon)), "the train should be locomotive, coach, wagon");
        Player driver = board(helper, locomotive);
        locomotive.setFuel(16000);
        fullAhead(locomotive);
        double coachStart = coach.getX();
        double wagonStart = wagon.getX();
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(coach.getX() - coachStart > 8.0, "the coach should have been pulled along");
            helper.assertTrue(wagon.getX() - wagonStart > 8.0, "the wagon should have been pulled along");
            assertSpacing(helper, locomotive, coach, 0.35);
            assertSpacing(helper, coach, wagon, 0.35);
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = TRACK, timeoutTicks = 300)
    public static void reversingPushesTheTrain(GameTestHelper helper) {
        layTrack(helper);
        SteamLocomotive locomotive = place(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 34, true);
        PassengerCoach coach = place(helper, ModEntities.PASSENGER_COACH.get(), 31, true);
        Trains.couple(locomotive, coach);
        Player driver = board(helper, locomotive);
        locomotive.setFuel(16000);
        locomotive.control(TrainControlPayload.THROTTLE_DOWN);
        locomotive.control(TrainControlPayload.THROTTLE_DOWN);
        double start = locomotive.getX();
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(start - locomotive.getX() > 3.0, "should have backed up west, moved " + (start - locomotive.getX()));
            helper.assertTrue(Trains.facing(locomotive).x > 0.99, "should still face east while reversing");
            helper.assertTrue(coach.getX() < locomotive.getX(), "the coach should still be behind");
            assertSpacing(helper, locomotive, coach, 0.35);
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = TRACK, timeoutTicks = 300)
    public static void brakeStopsTheTrain(GameTestHelper helper) {
        layTrack(helper);
        SteamLocomotive locomotive = place(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 4, true);
        Player driver = board(helper, locomotive);
        locomotive.setFuel(16000);
        fullAhead(locomotive);
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(locomotive.getDeltaMovement().horizontalDistance() > 0.15, "should be going by now");
            locomotive.control(TrainControlPayload.BRAKE_ON);
        });
        helper.runAfterDelay(100, () -> {
            helper.assertTrue(locomotive.getDeltaMovement().horizontalDistance() < 0.01, "the brake should have stopped it");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = TRACK)
    public static void couplingRules(GameTestHelper helper) {
        layTrack(helper);
        SteamLocomotive first = place(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 4, true);
        SteamLocomotive second = place(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 8, true);
        helper.assertTrue(Trains.couple(first, second) == Trains.CoupleResult.TWO_LOCOMOTIVES, "two locomotives shouldn't couple");

        // A wagon pair coupled to a locomotive by its back end is turned round, so the locomotive still leads.
        FreightWagon a = place(helper, ModEntities.FREIGHT_WAGON.get(), 15, true);
        FreightWagon b = place(helper, ModEntities.FREIGHT_WAGON.get(), 12, true);
        helper.assertTrue(Trains.couple(a, b) == Trains.CoupleResult.COUPLED, "wagons should couple");
        helper.assertTrue(Trains.couple(b, second) == Trains.CoupleResult.COUPLED, "the wagons' back end should couple to the locomotive");
        helper.assertTrue(Trains.train(a).equals(List.of(second, b, a)), "the locomotive should lead, then b, then a");

        // Only the ends of a train can be coupled to, and only close by.
        PassengerCoach coach = place(helper, ModEntities.PASSENGER_COACH.get(), 17, true);
        helper.assertTrue(Trains.couple(coach, b) == Trains.CoupleResult.NOT_AN_END, "a car in the middle shouldn't couple");
        helper.assertTrue(Trains.couple(coach, first) == Trains.CoupleResult.TOO_FAR, "cars far apart shouldn't couple");
        helper.assertTrue(Trains.couple(coach, a) == Trains.CoupleResult.COUPLED, "the coach should couple to the end wagon");
        helper.assertTrue(Trains.train(coach).equals(List.of(second, b, a, coach)), "the coach should go on the end");

        // Taking a car out of the middle splits the train.
        b.discard();
        helper.assertTrue(second.trainState().follower() == null, "the locomotive should have lost its coupling");
        helper.assertTrue(a.trainState().leader() == null, "the last wagon should have lost its coupling");
        helper.succeed();
    }

    @GameTest(template = TRACK)
    public static void couplingsAreSaved(GameTestHelper helper) {
        layTrack(helper);
        SteamLocomotive locomotive = place(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 14, true);
        PassengerCoach coach = place(helper, ModEntities.PASSENGER_COACH.get(), 11, true);
        Trains.couple(locomotive, coach);
        locomotive.setFuel(3200);
        CompoundTag saved = new CompoundTag();
        coach.saveWithoutId(saved);
        PassengerCoach copy = ModEntities.PASSENGER_COACH.get().create(helper.getLevel());
        copy.load(saved);
        helper.assertTrue(locomotive.getUUID().equals(copy.trainState().leader()), "the coupling should load back");
        CompoundTag savedLoco = new CompoundTag();
        locomotive.saveWithoutId(savedLoco);
        SteamLocomotive locoCopy = ModEntities.STEAM_LOCOMOTIVE.get().create(helper.getLevel());
        locoCopy.load(savedLoco);
        helper.assertTrue(locoCopy.getFuel() == 3200, "the fuel should load back");
        helper.succeed();
    }

    @GameTest(template = TRACK, timeoutTicks = 100)
    public static void wagonShowsItsLoad(GameTestHelper helper) {
        layTrack(helper);
        FreightWagon wagon = place(helper, ModEntities.FREIGHT_WAGON.get(), 10, true);
        for (int i = 0; i < 12; i++) {
            wagon.setItem(i, new ItemStack(Items.COBBLESTONE, 64));
        }
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(wagon.getLoad() == 2, "12 of 27 slots used should show two crates, shows " + wagon.getLoad());
            helper.succeed();
        });
    }

    @GameTest(template = TRACK)
    public static void fuelFillsTheFirebox(GameTestHelper helper) {
        layTrack(helper);
        SteamLocomotive locomotive = place(helper, ModEntities.STEAM_LOCOMOTIVE.get(), 10, true);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 40));
        locomotive.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
        helper.assertTrue(locomotive.getFuel() == SteamLocomotive.FUEL_CAPACITY, "32 coal should fill the firebox, has " + locomotive.getFuel());
        helper.assertTrue(player.getMainHandItem().getCount() == 8, "8 coal should be left over, " + player.getMainHandItem().getCount() + " were");
        helper.assertTrue(locomotive.getFuelPercent() == 100, "should show full");
        helper.succeed();
    }
}
