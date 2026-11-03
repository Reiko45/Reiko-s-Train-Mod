package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.freight.LoaderMode;
import com.reiko.trains.freight.WagonLoaderBlockEntity;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.signal.TrackSensorBlock;
import com.reiko.trains.signal.TrackSensorBlockEntity;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.LivestockWagon;
import com.reiko.trains.train.TrainCarEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for the livestock wagon, and wagon loaders and track sensors working with it. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class LivestockGameTests {
    private static final String LINE = "line";
    private static final Vec3 EAST = new Vec3(1, 0, 0);

    private LivestockGameTests() {
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

    private static LivestockWagon wagon(GameTestHelper helper, int x) {
        return TestTracks.car(helper.getLevel(), ModEntities.LIVESTOCK_WAGON.get(), at(helper, x), EAST);
    }

    /** An animal standing at (x, z) in the test's own coordinates (y = 1, on the floor), not wandering off. */
    private static <T extends Animal> T animal(GameTestHelper helper, EntityType<T> type, double x, double z) {
        T animal = type.create(helper.getLevel());
        BlockPos at = helper.absolutePos(new BlockPos(0, 1, 0));
        animal.moveTo(at.getX() + x, at.getY(), at.getZ() + z, 0, 0);
        animal.setNoAi(true);
        helper.getLevel().addFreshEntity(animal);
        return animal;
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void animalsOnLeadsGoInAndComeOut(GameTestHelper helper) {
        line(helper);
        LivestockWagon wagon = wagon(helper, 20);
        Player farmer = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos stand = helper.absolutePos(new BlockPos(20, 1, 7));
        farmer.moveTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
        List<Animal> herd = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Animal cow = animal(helper, EntityType.COW, 14.5 + i * 2, 8.0);
            cow.setLeashedTo(farmer, true);
            herd.add(cow);
        }
        wagon.interact(farmer, InteractionHand.MAIN_HAND);
        helper.assertTrue(wagon.animals() == LivestockWagon.STALLS, "six cows should fill its six stalls, has " + wagon.animals());
        long left = herd.stream().filter(cow -> !cow.isPassenger()).count();
        helper.assertTrue(left == 1, "one should be left over, not " + left);
        helper.assertTrue(farmer.getInventory().countItem(Items.LEAD) == 6, "the farmer should have the six leads back");
        helper.assertTrue(wagon.mass() > 2.5, "a full wagon should be heavier, is " + wagon.mass());
        Player other = helper.makeMockPlayer(GameType.SURVIVAL);
        other.moveTo(wagon.position());
        wagon.interact(other, InteractionHand.MAIN_HAND);
        helper.assertTrue(!wagon.getPassengers().contains(other), "people can't ride in it");
        farmer.setShiftKeyDown(true);
        farmer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        List<Animal> aboard = wagon.livestock();
        wagon.interact(farmer, InteractionHand.MAIN_HAND);
        helper.assertTrue(wagon.animals() == 0, "sneak-clicking should let them all out");
        for (Animal cow : aboard) {
            helper.assertTrue(!cow.isPassenger() && cow.distanceTo(farmer) < 2, "and they come out by the farmer");
        }
        helper.succeed();
    }

    @GameTest(template = LINE, timeoutTicks = 200)
    public static void animalsRideSafely(GameTestHelper helper) {
        line(helper);
        LivestockWagon wagon = wagon(helper, 10);
        Animal sheep = EntityType.SHEEP.create(helper.getLevel());
        sheep.moveTo(wagon.position());
        helper.getLevel().addFreshEntity(sheep);
        Animal pig = EntityType.PIG.create(helper.getLevel());
        pig.moveTo(wagon.position());
        helper.getLevel().addFreshEntity(pig);
        helper.assertTrue(wagon.load(sheep) && wagon.load(pig), "the animals should go in");
        float health = sheep.getHealth();
        sheep.hurt(helper.getLevel().damageSources().inWall(), 4);
        sheep.hurt(helper.getLevel().damageSources().fall(), 4);
        helper.assertTrue(sheep.getHealth() == health, "aboard, walls and falls shouldn't hurt them");
        wagon.shove(0.3);
        double startX = wagon.getX();
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(wagon.getX() > startX + 10, "the wagon should have rolled on");
            helper.assertTrue(sheep.getVehicle() == wagon && pig.getVehicle() == wagon, "with the animals still aboard");
            helper.assertTrue(sheep.distanceTo(wagon) < 2.5 && pig.distanceTo(wagon) < 2.5, "riding along inside it");
            helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(sheep.getYRot() - wagon.getYRot())) < 1, "facing along the wagon");
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void loaderLoadsTheAnimalsItsFilterPicks(GameTestHelper helper) {
        line(helper);
        WagonLoaderBlockEntity loader = TestTracks.wagonLoader(helper.getLevel(), helper.absolutePos(new BlockPos(20, 1, 6)), at(helper, 20),
                LoaderMode.LOAD);
        loader.filter().setItem(0, new ItemStack(Items.PIG_SPAWN_EGG));
        List<Animal> pigs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            pigs.add(animal(helper, EntityType.PIG, 18.5 + i * 1.5, 8.0));
        }
        Animal sheep = animal(helper, EntityType.SHEEP, 22.5, 7.5);
        Animal far = animal(helper, EntityType.PIG, 40.5, 8.0);
        LivestockWagon wagon = wagon(helper, 20);
        helper.runAfterDelay(40, () -> {
            for (Animal pig : pigs) {
                helper.assertTrue(pig.getVehicle() == wagon, "the pigs by the loader should have been put in");
            }
            helper.assertTrue(!sheep.isPassenger(), "the filter's only for pigs: the sheep should stay out");
            helper.assertTrue(!far.isPassenger(), "and a pig far off isn't waiting at the loader");
            int read = helper.getLevel().getBlockState(loader.getBlockPos()).getAnalogOutputSignal(helper.getLevel(), loader.getBlockPos());
            helper.assertTrue(read > 0 && read < 15, "a comparator should read the wagon part full, not " + read);
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 100)
    public static void loaderLetsAnimalsOutBehindIt(GameTestHelper helper) {
        line(helper);
        WagonLoaderBlockEntity loader = TestTracks.wagonLoader(helper.getLevel(), helper.absolutePos(new BlockPos(20, 1, 6)), at(helper, 20),
                LoaderMode.UNLOAD);
        LivestockWagon wagon = wagon(helper, 20);
        List<Animal> cows = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            Animal cow = animal(helper, EntityType.COW, 20.5, 4.5);
            helper.assertTrue(wagon.load(cow), "the cows should go in");
            cows.add(cow);
        }
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(wagon.animals() == 0, "the loader should have let them all out");
            for (Animal cow : cows) {
                helper.assertTrue(!cow.isPassenger(), "every cow out");
                helper.assertTrue(cow.getZ() > loader.getBlockPos().getZ() + 0.9 && cow.distanceToSqr(Vec3.atCenterOf(loader.getBlockPos())) < 9,
                        "behind the loader, away from the track: " + cow.position().subtract(Vec3.atCenterOf(loader.getBlockPos())));
            }
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 60)
    public static void sensorPicksOutTrainsCarryingAnAnimal(GameTestHelper helper) {
        line(helper);
        TrackSensorBlockEntity sensor = TestTracks.trackSensor(helper.getLevel(), helper.absolutePos(new BlockPos(30, 1, 6)));
        sensor.setFilter(new ItemStack(Items.SHEEP_SPAWN_EGG));
        LivestockWagon wagon = wagon(helper, 30);
        Animal cow = animal(helper, EntityType.COW, 30.5, 4.5);
        wagon.load(cow);
        helper.runAfterDelay(4, () -> {
            helper.assertTrue(!sensor.getBlockState().getValue(TrackSensorBlock.POWERED), "a wagon of cows isn't carrying sheep");
            wagon.load(animal(helper, EntityType.SHEEP, 30.5, 4.5));
        });
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(sensor.getBlockState().getValue(TrackSensorBlock.POWERED), "with a sheep aboard, it should sense the train");
            helper.assertTrue(helper.getLevel().getRecipeManager().byKey(ReikoTrains.id("livestock_wagon")).isPresent(), "the wagon should have a recipe");
            helper.succeed();
        });
    }
}
