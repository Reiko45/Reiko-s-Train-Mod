package com.reiko.trains.dev;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.gametest.TestTracks;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModDataComponents;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.PassengerCoach;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import com.reiko.trains.menu.FreightWagonMenu;
import com.reiko.trains.signal.StationBlockEntity;
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
    private static void build(ServerLevel level) {
        TestTracks.line(level, new BlockPos(-30, Y, 0), TrackAxis.X, 110);
        BlockPos branch = new BlockPos(52, Y, 0);
        TestTracks.connect(level, branch, branch.offset(14, 0, 6));
        TestTracks.line(level, branch.offset(15, 0, 7), TrackAxis.SE, 10);
        TestTracks.station(level, new BlockPos(32, Y, -3), new BlockPos(32, Y, 0), "Riverside", 10);
        TestTracks.signal(level, new BlockPos(46, Y, -2), new BlockPos(46, Y, 0), 1);

        TestTracks.loop(level, new BlockPos(0, Y, 14), 30, 12);
        for (int x : new int[]{14, 26, 38}) {
            TestTracks.signal(level, new BlockPos(x, Y, 12), new BlockPos(x, Y, 14), 1);
            TestTracks.signal(level, new BlockPos(54 - x, Y, 40), new BlockPos(54 - x, Y, 38), -1);
        }
        StationBlockEntity north = TestTracks.station(level, new BlockPos(32, Y, 11), new BlockPos(32, Y, 14), "Northgate", 4);
        StationBlockEntity south = TestTracks.station(level, new BlockPos(22, Y, 41), new BlockPos(22, Y, 38), "Southport", 4);

        SteamLocomotive loco = put(level, ModEntities.STEAM_LOCOMOTIVE.get(), 22, 0);
        PassengerCoach coach = put(level, ModEntities.PASSENGER_COACH.get(), 15, 0);
        PassengerCoach coach2 = put(level, ModEntities.PASSENGER_COACH.get(), 8, 0);
        FreightWagon wagon = put(level, ModEntities.FREIGHT_WAGON.get(), 2, 0);
        Trains.couple(loco, coach);
        Trains.couple(coach, coach2);
        Trains.couple(coach2, wagon);
        for (int i = 0; i < 70; i++) {
            wagon.setItem(i, new ItemStack(i % 3 == 0 ? Items.OAK_LOG : i % 3 == 1 ? Items.COAL : Items.IRON_INGOT, 64));
        }
        SteamLocomotive a = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), new BlockPos(22, Y, 14), EAST);
        PassengerCoach aCoach = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), new BlockPos(15, Y, 14), EAST);
        SteamLocomotive b = TestTracks.car(level, ModEntities.STEAM_LOCOMOTIVE.get(), new BlockPos(30, Y, 38), new Vec3(-1, 0, 0));
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
            SteamLocomotive loco = find(player.serverLevel(), SteamLocomotive.class, 22.5, 0.5);
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
            if (player.getVehicle() instanceof SteamLocomotive loco) {
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
                if (player.getVehicle() instanceof SteamLocomotive loco) {
                    loco.control(TrainControlPayload.BRAKE_ON);
                }
            });
        });
        // The station's settings screen
        at(780, mc -> mc.setScreen(new com.reiko.trains.client.StationScreen(new BlockPos(32, Y, -3), "Riverside", 10)));
        at(800, mc -> shot(mc, "11_station_screen"));
        at(805, mc -> mc.setScreen(null));
        at(830, Minecraft::stop);
    }
}
