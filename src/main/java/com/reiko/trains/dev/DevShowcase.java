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
     * The layout: a main line along z = 0 with a junction at x = 52 (a branch curving off south-east), and a loop
     * (straights at z = 14 and z = 38, x 12 to 42, with radius-12 ends) to the south.
     */
    private static void build(ServerLevel level) {
        TestTracks.line(level, new BlockPos(-30, Y, 0), TrackAxis.X, 110);
        BlockPos branch = new BlockPos(52, Y, 0);
        TestTracks.connect(level, branch, branch.offset(14, 0, 6));
        TestTracks.line(level, branch.offset(15, 0, 7), TrackAxis.SE, 10);
        TestTracks.loop(level, new BlockPos(0, Y, 14), 30, 12);

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
        SteamLocomotive loopLoco = put(level, ModEntities.STEAM_LOCOMOTIVE.get(), 30, 14);
        PassengerCoach loopCoach = put(level, ModEntities.PASSENGER_COACH.get(), 23, 14);
        Trains.couple(loopLoco, loopCoach);
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
        at(60, mc -> look(mc, 12.5, Y + 1.9, 13.5, 180.0F, 6.0F));
        at(130, mc -> shot(mc, "01_train_side"));
        at(135, mc -> look(mc, 31.0, Y + 2.6, 6.5, 125.0F, 12.0F));
        at(190, mc -> shot(mc, "02_train_front"));
        at(195, mc -> look(mc, 58.0, Y + 14.0, 6.0, 40.0F, 40.0F));
        at(250, mc -> shot(mc, "03_loop_curve"));
        at(255, mc -> look(mc, 47.0, Y + 7.0, -8.0, -30.0F, 30.0F));
        at(310, mc -> shot(mc, "04_junction"));
        // The wagon's screen
        at(315, mc -> server(mc, player -> {
            player.teleportTo(player.serverLevel(), 2.5, Y + 1.0, 4.0, 180.0F, 20.0F);
            FreightWagon wagon = find(player.serverLevel(), FreightWagon.class, 2.5, 0.5);
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new FreightWagonMenu(id, inventory, wagon), wagon.getDisplayName()));
        }));
        at(345, mc -> shot(mc, "05_wagon_screen"));
        at(347, mc -> server(mc, ServerPlayer::closeContainer));
        // Drawing a curve: the preview
        at(350, mc -> server(mc, player -> {
            ItemStack track = new ItemStack(ModItems.TRACK.get(), 64);
            track.set(ModDataComponents.TRACK_START.get(), new BlockPos(-20, Y, 0));
            player.setItemInHand(InteractionHand.MAIN_HAND, track);
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.teleportTo(player.serverLevel(), -10.0, Y + 1.5, 13.0, 160.0F, 50.0F);
        }));
        at(352, mc -> mc.gameRenderer.setRenderHand(true));
        at(400, mc -> shot(mc, "06_track_preview"));
        at(402, mc -> server(mc, player -> player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY)));
        // The loop train sets off on its own; watch it round the bend.
        at(405, mc -> server(mc, player -> {
            SteamLocomotive loco = find(player.serverLevel(), SteamLocomotive.class, 30.5, 14.5);
            loco.setFuel(16000);
            loco.setAutopilot(true);
            loco.control(TrainControlPayload.THROTTLE_UP);
            loco.control(TrainControlPayload.THROTTLE_UP);
            player.teleportTo(player.serverLevel(), 47.0, Y + 2.2, 9.0, 60.0F, 8.0F);
        }));
        at(455, mc -> shot(mc, "07_curve_a"));
        at(470, mc -> shot(mc, "08_curve_b"));
        at(485, mc -> shot(mc, "09_curve_c"));
        at(500, mc -> shot(mc, "10_curve_d"));
        // Into the main train's cab, and off down the line
        at(505, mc -> server(mc, player -> {
            SteamLocomotive loco = find(player.serverLevel(), SteamLocomotive.class, 22.5, 0.5);
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
            player.teleportTo(player.serverLevel(), loco.getX(), loco.getY(), loco.getZ(), -90.0F, 10.0F);
            player.startRiding(loco);
            loco.setFuel(16000);
        }));
        at(512, mc -> {
            mc.player.setYRot(-90.0F);
            mc.player.setXRot(6.0F);
            mc.gameRenderer.setRenderHand(true);
        });
        at(540, mc -> shot(mc, "11_cab_view"));
        at(542, mc -> server(mc, player -> {
            if (player.getVehicle() instanceof SteamLocomotive loco) {
                for (int i = 0; i < 3; i++) {
                    loco.control(TrainControlPayload.THROTTLE_UP);
                }
            }
        }));
        at(620, mc -> shot(mc, "12_cab_moving"));
        at(622, mc -> server(mc, player -> {
            if (player.getVehicle() instanceof SteamLocomotive loco) {
                loco.control(TrainControlPayload.BRAKE_ON);
            }
        }));
        at(660, mc -> look(mc, 27.0, Y + 22.0, 26.0, 0.0F, 60.0F));
        at(720, mc -> shot(mc, "13_overview"));
        at(760, Minecraft::stop);
    }
}
