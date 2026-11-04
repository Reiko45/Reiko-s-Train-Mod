package com.reiko.trains.gametest;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.menu.TrackLayerMenu;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.TrackLayer;
import com.reiko.trains.train.Trains;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Game tests for the track-laying wagon. */
@GameTestHolder(ReikoTrains.MODID)
@PrefixGameTestTemplate(false)
public final class TrackLayerGameTests {
    private static final String LINE = "line";
    private static final String YARD = "yard";
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    /** The track's level: four blocks of ground under it, and room for the wagon's cutting over it. */
    private static final int Y = 4;
    /** Where the track starts out ending (it runs from x = 0). */
    private static final int END = 19;

    private TrackLayerGameTests() {
    }

    /** Stone ground, {@code width} by {@code depth}, four blocks deep, its top just under the track. */
    private static void ground(GameTestHelper helper, int width, int depth) {
        ServerLevel level = helper.getLevel();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                for (int y = 0; y < Y; y++) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
    }

    private static BlockPos at(GameTestHelper helper, int x, int z) {
        return helper.absolutePos(new BlockPos(x, Y, z));
    }

    /** A steam engine pushing a track-laying wagon east along the line at {@code z}, the wagon at the end of the track, carrying {@code track}. */
    private static TrackLayer train(GameTestHelper helper, int z, int track, SteamLocomotive[] engine) {
        ServerLevel level = helper.getLevel();
        TestTracks.line(level, at(helper, 0, z), TrackAxis.X, END + 1);
        SteamLocomotive loco = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 9, z), EAST);
        TrackLayer layer = TestTracks.car(level, ModEntities.TRACK_LAYER.get(), at(helper, 16, z), EAST);
        helper.assertTrue(Trains.couple(loco, layer) == Trains.CoupleResult.COUPLED, "the wagon should couple to the engine");
        loco.setFuel(16000);
        if (track > 0) {
            layer.store(new ItemStack(ModItems.TRACK.get(), track));
        }
        engine[0] = loco;
        return layer;
    }

    private static Player drive(GameTestHelper helper, SteamLocomotive loco, int notches) {
        Player driver = helper.makeMockPlayer(GameType.SURVIVAL);
        driver.moveTo(loco.position());
        driver.startRiding(loco);
        for (int i = 0; i < notches; i++) {
            loco.control(TrainControlPayload.THROTTLE_UP);
        }
        return driver;
    }

    private static boolean track(GameTestHelper helper, int x, int z, TrackAxis axis) {
        return TrackGraph.axis(helper.getLevel(), at(helper, x, z)) == axis;
    }

    @GameTest(template = LINE, timeoutTicks = 500)
    public static void laysTrackAheadWhenPushedOffTheEnd(GameTestHelper helper) {
        ground(helper, 64, 9);
        SteamLocomotive[] loco = new SteamLocomotive[1];
        TrackLayer layer = train(helper, 4, 12, loco);
        helper.assertTrue(layer.getCapability(Capabilities.ItemHandler.ENTITY, null) != null, "hoppers and pipes should reach its store");
        Player driver = drive(helper, loco[0], 3);
        double[] fastest = {0};
        helper.onEachTick(() -> {
            if (layer.isWorking()) {
                fastest[0] = Math.max(fastest[0], Math.abs(layer.trainSpeed()));
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(layer.status() == TrackLayer.Status.OUT_OF_TRACK, "it should lay all its track, then stop: " + layer.status());
            helper.assertTrue(layer.laid() == 12, "12 pieces of track, not " + layer.laid());
            for (int x = END + 1; x <= END + 12; x++) {
                helper.assertTrue(track(helper, x, 4, TrackAxis.X), "straight track at x " + x);
            }
            helper.assertTrue(TrackGraph.axis(helper.getLevel(), at(helper, END + 13, 4)) == null, "and no more");
            helper.assertTrue(layer.count(stack -> stack.is(ModItems.TRACK.get())) == 0, "all its track used");
            helper.assertTrue(fastest[0] <= TrackLayer.LAYING_SPEED + 1.0E-6, "at walking pace while laying, not " + fastest[0]);
            helper.assertTrue(layer.getX() > at(helper, END + 6, 4).getX(), "the train should have followed the new track");
            driver.stopRiding();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 600)
    public static void digsThroughHillsAndFillsDips(GameTestHelper helper) {
        ground(helper, 64, 9);
        ServerLevel level = helper.getLevel();
        // A hill: stone right up to the top of the test space
        for (int x = 24; x <= 27; x++) {
            for (int z = 2; z <= 6; z++) {
                for (int y = Y; y <= Y + 3; y++) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        // A dip, three deep
        for (int x = 30; x <= 32; x++) {
            for (int z = 3; z <= 5; z++) {
                for (int y = 1; y < Y; y++) {
                    level.setBlock(helper.absolutePos(new BlockPos(x, y, z)), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        SteamLocomotive[] loco = new SteamLocomotive[1];
        TrackLayer layer = train(helper, 4, 16, loco); // no fill: what it digs out of the hill fills the dip
        Player driver = drive(helper, loco[0], 3);
        helper.succeedWhen(() -> {
            helper.assertTrue(layer.status() == TrackLayer.Status.OUT_OF_TRACK, "it should lay all its track: " + layer.status());
            for (int x = END + 1; x <= END + 16; x++) {
                helper.assertTrue(track(helper, x, 4, TrackAxis.X), "level track at x " + x);
            }
            for (int x = 24; x <= 27; x++) {
                for (int z = 3; z <= 5; z++) {
                    for (int y = Y; y <= Y + 3; y++) {
                        BlockPos pos = helper.absolutePos(new BlockPos(x, y, z));
                        helper.assertTrue(level.getBlockState(pos).isAir() || (z == 4 && y == Y),
                                "the cutting through the hill should be dug out at " + x + "," + y + "," + z);
                    }
                }
                helper.assertTrue(!level.getBlockState(helper.absolutePos(new BlockPos(x, Y, 2))).isAir(), "but not wider than it needs");
            }
            for (int x = 30; x <= 32; x++) {
                for (int y = 1; y < Y; y++) {
                    helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(x, y, 4))).is(Blocks.COBBLESTONE),
                            "the dip should be filled under the track with what it dug, at " + x + "," + y);
                }
                helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(x, Y - 1, 3))).isAir(), "only under the track");
            }
            int cobble = layer.count(stack -> stack.is(Items.COBBLESTONE));
            helper.assertTrue(cobble > 20, "what it dug out and didn't use should be in its store: " + cobble);
            driver.stopRiding();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 400)
    public static void stopsAtWater(GameTestHelper helper) {
        ground(helper, 64, 9);
        helper.getLevel().setBlock(helper.absolutePos(new BlockPos(END + 4, Y - 1, 4)), Blocks.WATER.defaultBlockState(), 3);
        SteamLocomotive[] loco = new SteamLocomotive[1];
        TrackLayer layer = train(helper, 4, 16, loco);
        layer.store(new ItemStack(Items.GRAVEL, 64));
        Player driver = drive(helper, loco[0], 3);
        helper.succeedWhen(() -> {
            helper.assertTrue(layer.status() == TrackLayer.Status.WATER, "it should stop at the water: " + layer.status());
            helper.assertTrue(layer.laid() == 3 && track(helper, END + 3, 4, TrackAxis.X), "laying up to it, " + layer.laid());
            helper.assertTrue(TrackGraph.axis(helper.getLevel(), at(helper, END + 4, 4)) == null, "and no further");
            helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(END + 4, Y - 1, 4))).is(Blocks.WATER),
                    "leaving the water be");
            driver.stopRiding();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 400)
    public static void needsFillForADip(GameTestHelper helper) {
        ground(helper, 64, 9);
        for (int y = 1; y < Y; y++) {
            helper.getLevel().setBlock(helper.absolutePos(new BlockPos(END + 3, y, 4)), Blocks.AIR.defaultBlockState(), 2);
        }
        SteamLocomotive[] loco = new SteamLocomotive[1];
        TrackLayer layer = train(helper, 4, 16, loco);
        layer.store(new ItemStack(Items.GRAVEL, 2));
        Player driver = drive(helper, loco[0], 3);
        boolean[] topped = {false};
        helper.onEachTick(() -> {
            if (!topped[0] && layer.status() == TrackLayer.Status.NEEDS_FILL) {
                helper.assertTrue(layer.laid() == 2 && TrackGraph.axis(helper.getLevel(), at(helper, END + 3, 4)) == null, "it lays up to the dip");
                helper.assertTrue(layer.count(stack -> stack.is(Items.GRAVEL)) == 2, "three deep, two gravel isn't enough: it keeps them");
                layer.store(new ItemStack(Items.DIRT, 8));
                topped[0] = true;
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(topped[0], "it should have said it needs fill first");
            helper.assertTrue(track(helper, END + 3, 4, TrackAxis.X), "with more fill it carries on over the dip");
            for (int y = 1; y < Y; y++) {
                helper.assertTrue(!helper.getLevel().getBlockState(helper.absolutePos(new BlockPos(END + 3, y, 4))).isAir(), "filled at y " + y);
            }
            driver.stopRiding();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 300)
    public static void switchedOffItLaysNothing(GameTestHelper helper) {
        ground(helper, 64, 9);
        SteamLocomotive[] loco = new SteamLocomotive[1];
        TrackLayer layer = train(helper, 4, 16, loco);
        Player driver = drive(helper, loco[0], 3);
        TrackLayerMenu menu = new TrackLayerMenu(0, driver.getInventory(), layer);
        helper.assertTrue(menu.isOn(), "it starts switched on");
        menu.clickMenuButton(driver, TrackLayerMenu.BUTTON_SWITCH);
        helper.assertTrue(!layer.isOn() && !menu.isOn() && menu.status() == TrackLayer.Status.OFF, "the switch should turn it off");
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(layer.laid() == 0 && TrackGraph.axis(helper.getLevel(), at(helper, END + 1, 4)) == null, "switched off, it lays nothing");
            helper.assertTrue(Math.abs(layer.trainSpeed()) < 1.0E-6, "the train just runs up to the end of the line");
            helper.assertTrue(layer.count(stack -> stack.is(ModItems.TRACK.get())) == 16, "its track is still there");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = LINE, timeoutTicks = 300)
    public static void layerBehindTheEngineLaysNothing(GameTestHelper helper) {
        ground(helper, 64, 9);
        ServerLevel level = helper.getLevel();
        TestTracks.line(level, at(helper, 0, 4), TrackAxis.X, END + 1);
        TrackLayer layer = TestTracks.car(level, ModEntities.TRACK_LAYER.get(), at(helper, 9, 4), EAST);
        SteamLocomotive loco = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), at(helper, 16, 4), EAST);
        helper.assertTrue(Trains.couple(loco, layer) == Trains.CoupleResult.COUPLED, "the wagon should couple behind the engine");
        loco.setFuel(16000);
        layer.store(new ItemStack(ModItems.TRACK.get(), 16));
        Player driver = drive(helper, loco, 3);
        helper.runAfterDelay(120, () -> {
            helper.assertTrue(layer.laid() == 0 && TrackGraph.axis(level, at(helper, END + 1, 4)) == null,
                    "behind the engine it can't lay track: the engine's in the way");
            driver.stopRiding();
            helper.succeed();
        });
    }

    @GameTest(template = YARD, timeoutTicks = 600)
    public static void steeringLaysABend(GameTestHelper helper) {
        ground(helper, 48, 48);
        SteamLocomotive[] loco = new SteamLocomotive[1];
        TrackLayer layer = train(helper, 10, 12, loco);
        loco[0].control(TrainControlPayload.STEER_RIGHT);
        Player driver = drive(helper, loco[0], 3);
        helper.succeedWhen(() -> {
            helper.assertTrue(layer.status() == TrackLayer.Status.OUT_OF_TRACK, "it should use all its track: " + layer.status());
            helper.assertTrue(track(helper, END + 7, 13, TrackAxis.SE), "a 45° bend to the right, ending going south-east");
            helper.assertTrue(TrackGraph.edgesFrom(helper.getLevel(), at(helper, END, 10), 1).size() == 1, "joined on to the end of the line");
            helper.assertTrue(loco[0].getSteer() == 0, "one bend for one press: then straight on");
            for (int i = 1; i <= 3; i++) {
                helper.assertTrue(track(helper, END + 7 + i, 13 + i, TrackAxis.SE), "then straight on, south-east: " + i);
            }
            helper.assertTrue(layer.getZ() > at(helper, 0, 13).getZ(), "the train should have gone round the bend");
            driver.stopRiding();
        });
    }
}
