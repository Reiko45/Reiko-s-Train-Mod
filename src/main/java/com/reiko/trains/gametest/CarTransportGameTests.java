package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.CarCargo;
import com.reiko.trains.train.CarTransporter;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.Trains;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Game tests for the car transporter and Reiko's Car Mod's cars on it. They need the car mod: without it installed
 * they pass straight away (run with {@code -PcarJars=...} to test them for real).
 */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class CarTransportGameTests {
    private static final String LINE = "line";
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    /** Yaw for a car facing west (its nose towards -x) and east. */
    private static final float FACING_WEST = 90.0F;
    private static final float FACING_EAST = -90.0F;
    private static final double SEDAN = 3.5;

    private CarTransportGameTests() {
    }

    private static boolean noCarMod(GameTestHelper helper) {
        if (!CarCargo.CAR_MOD) {
            helper.succeed();
            return true;
        }
        return false;
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

    /** A car transporter on the line, its middle at {@code x} + 0.5, facing east. */
    private static CarTransporter transporter(GameTestHelper helper, int x) {
        return TestTracks.car(helper.getLevel(), ModEntities.CAR_TRANSPORTER.get(), at(helper, x), EAST);
    }

    /** A car from the car mod ({@code type}: sedan, city_bus...) on the line, its middle at {@code x}, facing {@code yaw}. */
    private static Entity car(GameTestHelper helper, String type, double x, float yaw) {
        ServerLevel level = helper.getLevel();
        Entity car = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.fromNamespaceAndPath("reikocars", type)).create(level);
        BlockPos track = at(helper, 0);
        car.moveTo(helper.absolutePos(BlockPos.ZERO).getX() + x, track.getY() + 0.25, track.getZ() + 0.5, yaw, 0.0F);
        level.addFreshEntity(car);
        return car;
    }

    /** A car east of the transporter's east end, nose to it, ready to drive on. */
    private static Entity carAtEastEnd(GameTestHelper helper, CarTransporter wagon, String type, double length) {
        double x = wagon.getX() + wagon.halfLength() + length / 2 + 0.8 - helper.absolutePos(BlockPos.ZERO).getX();
        return car(helper, type, x, FACING_WEST);
    }

    /** {@code player} gets in {@code car} and keeps driving {@code direction} (+1 forwards, -1 in reverse). */
    private static String drive(Player player, Entity car, int direction) {
        if (player.getVehicle() != car) {
            player.stopRiding();
            player.startRiding(car, true);
        }
        Component said = CarCargo.drive(player, direction);
        return said != null && said.getContents() instanceof TranslatableContents words ? words.getKey().replace("message.reikotrains.car_transporter.", "")
                : String.valueOf(said);
    }

    private static double relX(GameTestHelper helper, Entity entity) {
        return entity.getX() - helper.absolutePos(BlockPos.ZERO).getX();
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void aCarDrivesOntoACarTransporter(GameTestHelper helper) {
        if (noCarMod(helper)) {
            return;
        }
        line(helper);
        CarTransporter wagon = transporter(helper, 20);
        Entity sedan = carAtEastEnd(helper, wagon, "sedan", SEDAN);
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.runAfterDelay(5, () -> {
            String said = drive(driver, sedan, 1);
            helper.assertTrue(said.equals("loaded"), "it should go aboard, not: " + said);
            helper.assertTrue(sedan.getVehicle() == wagon, "the car should be on the transporter");
            driver.stopRiding();
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(sedan.getVehicle() == wagon, "and stay on when its driver gets out");
            // It rolled to the far (west) end, nose first.
            double expected = wagon.getX() - (CarTransporter.DECK_HALF - SEDAN / 2);
            helper.assertTrue(Math.abs(sedan.getX() - expected) < 0.3, "it should be at the far end of the deck: " + (sedan.getX() - wagon.getX()));
            helper.assertTrue(Math.abs(sedan.getY() - (wagon.getY() + CarTransporter.DECK)) < 0.05, "standing on the deck");
            helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(sedan.getYRot() - wagon.getYRot() - 180)) < 1, "still facing west");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 80)
    public static void carsFillTheDeckEndToEnd(GameTestHelper helper) {
        if (noCarMod(helper)) {
            return;
        }
        line(helper);
        CarTransporter wagon = transporter(helper, 20);
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        String[][] steps = {{"sedan", "3.5", "loaded"}, {"sedan", "3.5", "loaded"}, {"city_bus", "7.75", "no_room"}, {"hatchback", "3.1", "loaded"}};
        for (int i = 0; i < steps.length; i++) {
            String[] step = steps[i];
            helper.runAfterDelay(5 + i * 5, () -> {
                Entity car = carAtEastEnd(helper, wagon, step[0], Double.parseDouble(step[1]));
                helper.runAfterDelay(2, () -> {
                    String said = drive(driver, car, 1);
                    helper.assertTrue(said.equals(step[2]), "a " + step[0] + " should be '" + step[2] + "', not " + said);
                    driver.stopRiding();
                    if (car.getVehicle() != wagon) {
                        car.discard();
                    }
                });
            });
        }
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(wagon.cars().size() == 3, "three cars should be aboard: " + wagon.cars().size());
            helper.assertTrue(wagon.mass() == 5.0, "and they should weigh it down");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void carsDriveOffTheEndsWhenTheWayIsClear(GameTestHelper helper) {
        if (noCarMod(helper)) {
            return;
        }
        line(helper);
        CarTransporter wagon = transporter(helper, 20);
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        Entity first = carAtEastEnd(helper, wagon, "sedan", SEDAN);
        helper.runAfterDelay(3, () -> {
            drive(driver, first, 1);
            driver.stopRiding();
        });
        Entity[] second = new Entity[1];
        helper.runAfterDelay(6, () -> second[0] = carAtEastEnd(helper, wagon, "sedan", SEDAN));
        helper.runAfterDelay(9, () -> {
            drive(driver, second[0], 1);
            // The second car, facing west, can't drive on forwards: the first car's in the way.
            String said = drive(driver, second[0], 1);
            helper.assertTrue(said.equals("in_the_way"), "the first car should be in the way, not: " + said);
            // The first car drives off forwards, off the west end.
            said = drive(driver, first, 1);
            helper.assertTrue(said.equals("unloaded"), "the first car should drive off the west end, not: " + said);
            helper.assertTrue(!first.isPassenger() && first.getX() < wagon.getX() - wagon.halfLength(), "and be off it, west of it");
            // The second backs off the east end.
            said = drive(driver, second[0], -1);
            helper.assertTrue(said.equals("unloaded"), "the second car should back off the east end, not: " + said);
            helper.assertTrue(!second[0].isPassenger() && second[0].getX() > wagon.getX() + wagon.halfLength(), "and be off it, east of it");
            helper.assertTrue(wagon.isEmpty(), "leaving the transporter empty");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void carsDriveAlongTheTrain(GameTestHelper helper) {
        if (noCarMod(helper)) {
            return;
        }
        line(helper);
        CarTransporter west = transporter(helper, 14);
        CarTransporter east = transporter(helper, 27);
        helper.assertTrue(Trains.couple(west, east) == Trains.CoupleResult.COUPLED, "the transporters should couple");
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        Entity sedan = carAtEastEnd(helper, east, "sedan", SEDAN);
        helper.runAfterDelay(4, () -> {
            String said = drive(driver, sedan, 1);
            helper.assertTrue(said.equals("loaded") && sedan.getVehicle() == west,
                    "driving on at the east end, it should go along to the far transporter: " + said);
            // Backing off, it goes back over the empty transporter and off the end of the train.
            said = drive(driver, sedan, -1);
            helper.assertTrue(said.equals("unloaded") && !sedan.isPassenger() && sedan.getX() > east.getX() + east.halfLength(),
                    "it should back off over the other transporter: " + said);
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void aTrainCarriesCarsAndWontLoadWhileMoving(GameTestHelper helper) {
        if (noCarMod(helper)) {
            return;
        }
        line(helper);
        SteamLocomotive loco = TestTracks.car(helper.getLevel(), ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 34), EAST);
        CarTransporter wagon = transporter(helper, 24);
        Trains.couple(loco, wagon);
        loco.setFuel(16000);
        Player engineer = helper.makeMockPlayer(GameType.SURVIVAL);
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        Entity sedan = car(helper, "sedan", wagon.getX() - wagon.halfLength() - SEDAN / 2 - 0.8 - helper.absolutePos(BlockPos.ZERO).getX(), FACING_EAST);
        Entity late = car(helper, "hatchback", 4, FACING_EAST);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(drive(driver, sedan, 1).equals("loaded"), "the sedan should drive on at the west end");
            driver.stopRiding();
            engineer.moveTo(loco.position());
            engineer.startRiding(loco);
            loco.control(TrainControlPayload.THROTTLE_UP);
        });
        double[] start = new double[1];
        helper.runAfterDelay(5, () -> start[0] = sedan.getX());
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(sedan.getVehicle() == wagon && sedan.getX() > start[0] + 2, "the car should go along with the train");
            CarTransporter.Slot slot = wagon.slot(sedan);
            Vec3 where = wagon.position().add(wagon.forward().scale(slot.along())).add(0, CarTransporter.DECK, 0);
            helper.assertTrue(sedan.position().distanceTo(where) < 0.3, "in its place on the deck");
            // Another car can't come aboard while it's moving (put it right at the end for the try).
            late.moveTo(wagon.getX() - wagon.halfLength() - 3.1 / 2 - 0.8, sedan.getY() - CarTransporter.DECK, wagon.getZ(), FACING_EAST, 0);
            String said = drive(driver, late, 1);
            helper.assertTrue(said.equals("moving") && !late.isPassenger(), "it should have to wait for the train to stop, not: " + said);
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 40)
    public static void carsAboardAreSaved(GameTestHelper helper) {
        if (noCarMod(helper)) {
            return;
        }
        line(helper);
        CarTransporter wagon = transporter(helper, 20);
        Entity sedan = carAtEastEnd(helper, wagon, "sedan", SEDAN);
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.runAfterDelay(3, () -> {
            drive(driver, sedan, 1);
            driver.stopRiding();
            CompoundTag saved = new CompoundTag();
            wagon.saveAsPassenger(saved);
            helper.assertTrue(saved.contains("Passengers"), "the car should be saved with the transporter, as its passenger");
            CarTransporter copy = ModEntities.CAR_TRANSPORTER.get().create(helper.getLevel());
            copy.load(saved);
            CarTransporter.Slot slot = copy.slot(sedan);
            helper.assertTrue(slot != null && slot.equals(wagon.slot(sedan)), "with its place on the deck: " + slot);
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 80)
    public static void aTrailerComesAboardAndHitchesOnTheDeck(GameTestHelper helper) {
        if (noCarMod(helper)) {
            return;
        }
        line(helper);
        CarTransporter wagon = transporter(helper, 20);
        double pickupLength = 3.75;
        Entity pickup = carAtEastEnd(helper, wagon, "pickup_truck", pickupLength);
        Entity camper = car(helper, "camper", relX(helper, pickup) + 6.5, FACING_WEST);
        helper.assertTrue(camper.startRiding(pickup, true), "the camper should hitch to the pickup");
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.runAfterDelay(4, () -> {
            String said = drive(driver, pickup, 1);
            helper.assertTrue(said.equals("loaded"), "driving on forwards, trailer and all: " + said);
            helper.assertTrue(pickup.getVehicle() == wagon && camper.getVehicle() == pickup, "the pickup aboard, the camper still hitched to it");
            CarTransporter.Slot slot = wagon.slot(pickup);
            helper.assertTrue(slot != null && slot.tail() > 4, "the camper taking up the deck behind it: " + slot);
        });
        helper.runAfterDelay(8, () -> {
            double expected = wagon.getX() - (CarTransporter.DECK_HALF - pickupLength / 2);
            helper.assertTrue(Math.abs(pickup.getX() - expected) < 0.3, "the pickup at the far end: " + (pickup.getX() - wagon.getX()));
            helper.assertTrue(camper.getX() > pickup.getX() + 3 && camper.getX() < wagon.getX() + wagon.halfLength(),
                    "the camper behind it, on the deck: " + (camper.getX() - wagon.getX()));
            camper.stopRiding(); // what the car mod's hitch key does to unhitch it
        });
        helper.runAfterDelay(11, () -> {
            helper.assertTrue(camper.getVehicle() == wagon, "unhitched, it's left standing aboard: " + camper.getVehicle());
            helper.assertTrue(wagon.slot(pickup).tail() == 0 && wagon.slot(camper) != null, "in its own place on the deck");
            helper.assertTrue(drive(driver, pickup, 1).equals("unloaded"), "the pickup can drive off without it");
        });
        helper.runAfterDelay(14, () -> {
            helper.assertTrue(!pickup.isPassenger(), "off");
            // Back aboard, and hitched up again on the deck.
            helper.assertTrue(drive(driver, pickup, -1).equals("loaded"), "it backs on again (no trailer now)");
        });
        helper.runAfterDelay(18, () -> {
            helper.assertTrue(pickup.getVehicle() == wagon && wagon.slot(pickup) != null, "aboard again");
            CarCargo.hitch(driver); // the car mod's hitch key, aboard
        });
        helper.runAfterDelay(24, () -> {
            helper.assertTrue(camper.getVehicle() == pickup, "the camper hitched up again: " + camper.getVehicle());
            helper.assertTrue(wagon.slot(camper) == null && wagon.slot(pickup).tail() > 4, "riding on the pickup again");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void carsLoadSidewaysFromACarRamp(GameTestHelper helper) {
        if (noCarMod(helper)) {
            return;
        }
        line(helper);
        ServerLevel level = helper.getLevel();
        for (int x = 16; x <= 24; x++) {
            level.setBlock(helper.absolutePos(new BlockPos(x, 1, 7)), com.reiko.trains.registry.ModBlocks.CAR_RAMP.get().defaultBlockState()
                    .setValue(com.reiko.trains.freight.CarRampBlock.FACING, net.minecraft.core.Direction.NORTH), 3);
        }
        CarTransporter wagon = transporter(helper, 20);
        Entity sedan = car(helper, "sedan", 21.5, FACING_WEST);
        sedan.moveTo(sedan.getX(), helper.absolutePos(new BlockPos(0, 2, 0)).getY(), helper.absolutePos(new BlockPos(0, 0, 7)).getZ() + 0.5,
                FACING_WEST, 0);
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(CarCargo.onRamp(sedan) && CarCargo.besideRamp(sedan) == wagon, "on the ramp, beside the transporter");
            String said = drive(driver, sedan, 1);
            helper.assertTrue(said.equals("loaded"), "it should drive aboard sideways: " + said);
            helper.assertTrue(sedan.getVehicle() == wagon, "aboard");
            helper.assertTrue(Math.abs(wagon.slot(sedan).along() - 1.0) < 0.6, "where it was, along the train: " + wagon.slot(sedan));
        });
        helper.runAfterDelay(8, () -> {
            String said = drive(driver, sedan, 1);
            helper.assertTrue(said.equals("onto_ramp"), "and off again onto the ramp: " + said);
            helper.assertTrue(!sedan.isPassenger() && Math.abs(sedan.getZ() - (helper.absolutePos(new BlockPos(0, 0, 7)).getZ() + 0.5)) < 0.6,
                    "beside the train: " + sedan.position());
            helper.succeed();
        });
    }
}
