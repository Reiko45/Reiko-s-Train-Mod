package com.reiko.trains.dev;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.item.TrainCarItem;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.PassengerCoach;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.Trains;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * DEV ONLY (left out of the jar by build.gradle). {@code ./gradlew runClient -Pshowcase} makes a flat creative
 * world, lays track, builds a train and drives it, saving screenshots to {@code run/screenshots/}, then quits. Used
 * to check the models, textures, seats, smoke and gauges in the real game.
 */
@EventBusSubscriber(modid = ReikoTrains.MODID, value = Dist.CLIENT)
public final class DevShowcase {
    private static final boolean ENABLED = Boolean.getBoolean("reikotrains.showcase");
    /** The flat world's ground is at y = -61, so rails go at -60. */
    private static final int RAIL_Y = -60;
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
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(player.serverLevel(), x, y, z, yaw, pitch);
        });
    }

    private static void shot(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name + ".png", minecraft.getMainRenderTarget(),
                message -> ReikoTrains.LOGGER.info("Showcase screenshot: {}", message.getString()));
    }

    private static <T extends AbstractMinecart> T car(ServerLevel level, Class<T> type, double minX, double maxX, double z) {
        return level.getEntitiesOfClass(type, new net.minecraft.world.phys.AABB(minX, RAIL_Y - 2, z - 3, maxX, RAIL_Y + 3, z + 3)).getFirst();
    }

    /** A straight line along x at z = 0, and a rectangular loop to the south (z 10 to 30). */
    private static void layTrack(ServerLevel level) {
        for (int x = -40; x <= 70; x++) {
            level.setBlock(new BlockPos(x, RAIL_Y, 0), Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.EAST_WEST), 2);
        }
        List<BlockPos> loop = new ArrayList<>();
        for (int x = 0; x <= 20; x++) {
            loop.add(new BlockPos(x, RAIL_Y, 10));
            loop.add(new BlockPos(x, RAIL_Y, 30));
        }
        for (int z = 11; z < 30; z++) {
            loop.add(new BlockPos(0, RAIL_Y, z));
            loop.add(new BlockPos(20, RAIL_Y, z));
        }
        for (BlockPos pos : loop) {
            level.setBlock(pos, Blocks.RAIL.defaultBlockState(), 2);
        }
        for (BlockPos pos : loop) {
            RailShape shape;
            if (pos.getX() == 0 && pos.getZ() == 10) {
                shape = RailShape.SOUTH_EAST;
            } else if (pos.getX() == 20 && pos.getZ() == 10) {
                shape = RailShape.SOUTH_WEST;
            } else if (pos.getX() == 20 && pos.getZ() == 30) {
                shape = RailShape.NORTH_WEST;
            } else if (pos.getX() == 0 && pos.getZ() == 30) {
                shape = RailShape.NORTH_EAST;
            } else {
                shape = pos.getZ() == 10 || pos.getZ() == 30 ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
            }
            level.setBlock(pos, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape), 2);
        }
    }

    private static void script() {
        at(20, mc -> {
            mc.options.hideGui = false;
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(mc, player -> {
                ServerLevel level = player.serverLevel();
                level.setDayTime(6000);
                layTrack(level);
                // The train: locomotive, coach, wagon, facing east
                SteamLocomotive loco = place(level, ModEntities.STEAM_LOCOMOTIVE.get(), 10, 0, RailShape.EAST_WEST, new Vec3(1, 0, 0));
                PassengerCoach coach = place(level, ModEntities.PASSENGER_COACH.get(), 7, 0, RailShape.EAST_WEST, new Vec3(1, 0, 0));
                FreightWagon wagon = place(level, ModEntities.FREIGHT_WAGON.get(), 4, 0, RailShape.EAST_WEST, new Vec3(1, 0, 0));
                Trains.couple(loco, coach);
                Trains.couple(coach, wagon);
                for (int i = 0; i < 14; i++) {
                    wagon.setItem(i, new ItemStack(Items.OAK_LOG, 64));
                }
                // A second train on the loop, just short of a corner
                SteamLocomotive loopLoco = place(level, ModEntities.STEAM_LOCOMOTIVE.get(), 20, 14, RailShape.NORTH_SOUTH, new Vec3(0, 0, -1));
                PassengerCoach loopCoach = place(level, ModEntities.PASSENGER_COACH.get(), 20, 17, RailShape.NORTH_SOUTH, new Vec3(0, 0, -1));
                Trains.couple(loopLoco, loopCoach);
            });
        });
        // Let the couplings pull the cars to their spacing, then look from the side, the front corner and behind.
        at(60, mc -> {
            mc.gameRenderer.setRenderHand(false);
            look(mc, 7.5, RAIL_Y + 1.4, 5.2, 180.0F, 8.0F);
        });
        at(130, mc -> shot(mc, "01_train_side"));
        at(135, mc -> look(mc, 14.5, RAIL_Y + 1.9, 3.2, 125.0F, 14.0F));
        at(190, mc -> shot(mc, "02_train_front"));
        at(195, mc -> look(mc, 0.5, RAIL_Y + 2.3, -3.5, -60.0F, 20.0F));
        at(250, mc -> shot(mc, "03_train_back"));
        // Into the cab of the main train, fire up and set off
        at(255, mc -> server(mc, player -> {
            SteamLocomotive loco = car(player.serverLevel(), SteamLocomotive.class, 8, 13, 0);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.startRiding(loco);
            loco.setFuel(16000);
        }));
        at(262, mc -> {
            mc.player.setYRot(-90.0F);
            mc.player.setXRot(12.0F);
            mc.gameRenderer.setRenderHand(true);
        });
        at(300, mc -> shot(mc, "04_cab_view"));
        at(302, mc -> server(mc, player -> {
            if (player.getVehicle() instanceof SteamLocomotive loco) {
                for (int i = 0; i < 3; i++) {
                    loco.control(TrainControlPayload.THROTTLE_UP);
                }
            }
        }));
        at(370, mc -> shot(mc, "05_cab_moving"));
        at(372, mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
        at(410, mc -> shot(mc, "06_third_person_moving"));
        at(412, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server(mc, player -> {
                if (player.getVehicle() instanceof SteamLocomotive loco) {
                    loco.control(TrainControlPayload.BRAKE_ON);
                }
            });
        });
        at(450, mc -> shot(mc, "07_braking"));
        // Over to the loop train, and round the corner
        at(455, mc -> server(mc, player -> {
            player.stopRiding();
            SteamLocomotive loopLoco = car(player.serverLevel(), SteamLocomotive.class, 18, 22, 15);
            player.teleportTo(player.serverLevel(), loopLoco.getX(), loopLoco.getY(), loopLoco.getZ(), 180.0F, 15.0F);
            player.startRiding(loopLoco);
            loopLoco.setFuel(16000);
            loopLoco.control(TrainControlPayload.THROTTLE_UP);
            loopLoco.control(TrainControlPayload.THROTTLE_UP);
        }));
        at(460, mc -> {
            mc.player.setYRot(180.0F);
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        });
        at(500, mc -> shot(mc, "08_corner_a"));
        at(520, mc -> shot(mc, "09_corner_b"));
        at(540, mc -> shot(mc, "10_corner_c"));
        at(545, mc -> server(mc, player -> {
            player.stopRiding();
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(player.serverLevel(), 28.0, RAIL_Y + 6.0, 2.0, 40.0F, 28.0F);
        }));
        at(548, mc -> {
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            mc.gameRenderer.setRenderHand(false);
        });
        at(600, mc -> shot(mc, "11_loop_overview"));
        at(640, Minecraft::stop);
    }

    private static <T extends AbstractMinecart> T place(ServerLevel level, net.minecraft.world.entity.EntityType<T> type, int x, int z,
                                                       RailShape shape, Vec3 look) {
        return TrainCarItem.place(level, type, new BlockPos(x, RAIL_Y, z), shape, look, ItemStack.EMPTY, null);
    }
}
