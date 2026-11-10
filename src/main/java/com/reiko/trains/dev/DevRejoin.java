package com.reiko.trains.dev;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.gametest.TestTracks;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.train.CarPart;
import com.reiko.trains.train.TrainCarEntity;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
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
 * Development only ({@code ./gradlew runClient -PrejoinTest}): builds a train in a new flat world, gets in it, saves
 * and quits, opens the world again, and then does what a player does through the real client (gets in, uses the
 * coupler, hits a car), logging what worked as {@code REJOIN:} lines, with screenshots before and after.
 */
@EventBusSubscriber(modid = ReikoTrains.MODID, value = Dist.CLIENT)
public final class DevRejoin {
    private static final boolean ENABLED = Boolean.getBoolean("reikotrains.rejoinTest");
    private static final int Y = -60;
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    private static int phase;
    private static boolean quitting;
    private static String levelName;
    private static int tick = -1;
    private static UUID loco;
    private static UUID tender;
    private static UUID coach;
    private static UUID curveCoach;
    private static final List<String> RESULTS = new ArrayList<>();

    private DevRejoin() {
    }

    @SubscribeEvent
    static void onScreen(ScreenEvent.Opening event) {
        if (!ENABLED || !(event.getNewScreen() instanceof TitleScreen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (phase == 0) {
            phase = 1;
            minecraft.execute(() -> {
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                levelName = "rejoin-" + System.currentTimeMillis();
                LevelSettings settings = new LevelSettings(levelName, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                        WorldDataConfiguration.DEFAULT);
                minecraft.createWorldOpenFlows().createFreshLevel(levelName, settings, new WorldOptions(1L, false, false),
                        registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                                .value().createWorldDimensions(), new TitleScreen());
            });
        } else if (phase == 1 && quitting) {
            phase = 2;
            tick = -1;
            ReikoTrains.LOGGER.info("REJOIN: opening {} again", levelName);
            minecraft.execute(() -> minecraft.createWorldOpenFlows().openWorld(levelName, () -> ReikoTrains.LOGGER.error("REJOIN: couldn't open it")));
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!ENABLED || mc.player == null || mc.getSingleplayerServer() == null || quitting && phase == 1) {
            return;
        }
        tick++;
        try {
            if (phase == 1) {
                firstVisit(mc);
            } else if (phase == 2) {
                secondVisit(mc);
            }
        } catch (RuntimeException e) {
            ReikoTrains.LOGGER.error("REJOIN: step at tick {} failed", tick, e);
        }
    }

    private static void firstVisit(Minecraft mc) {
        if (tick == 20) {
            server(mc, (level, player) -> {
                TestTracks.line(level, new BlockPos(0, Y, 0), TrackAxis.X, 61);
                TestTracks.connect(level, new BlockPos(60, Y, 0), new BlockPos(72, Y, 12));
                TrainCarEntity express = TestTracks.car(level, ModEntities.EXPRESS_LOCOMOTIVE.get(), new BlockPos(32, Y, 0), EAST);
                int tenderX = 32 - (int) Math.ceil(express.halfLength() + ModEntities.TENDER.get().create(level).halfLength() + 0.3);
                TrainCarEntity tenderCar = TestTracks.car(level, ModEntities.TENDER.get(), new BlockPos(tenderX, Y, 0), EAST);
                int coachX = tenderX - (int) Math.ceil(tenderCar.halfLength() + ModEntities.PASSENGER_COACH.get().create(level).halfLength() + 0.3);
                TrainCarEntity coachCar = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), new BlockPos(coachX, Y, 0), EAST);
                com.reiko.trains.train.Trains.couple(express, tenderCar);
                com.reiko.trains.train.Trains.couple(tenderCar, coachCar);
                TrainCarEntity onCurve = TestTracks.car(level, ModEntities.PASSENGER_COACH.get(), new BlockPos(60, Y, 0), EAST);
                loco = express.getUUID();
                tender = tenderCar.getUUID();
                coach = coachCar.getUUID();
                curveCoach = onCurve.getUUID();
                player.teleportTo(level, 32, Y + 1, -4, 0, 10);
                player.startRiding(express);
                ReikoTrains.LOGGER.info("REJOIN: built: express {}, tender {}, coach {}, coach on the curve {}", loco, tender, coach, curveCoach);
            });
        }
        if (tick == 80) {
            server(mc, (level, player) -> report(level, player, "before"));
        }
        if (tick == 90) {
            server(mc, (level, player) -> {
            });
            view(mc, 58, Y + 4, -8, 20, 20);
        }
        if (tick == 110) {
            shot(mc, "rejoin_1_before");
        }
        if (tick == 130) {
            quitting = true;
            ReikoTrains.LOGGER.info("REJOIN: saving and quitting, still in the express");
            mc.setCameraEntity(mc.player);
            mc.level.disconnect();
            mc.disconnect(new GenericMessageScreen(Component.literal("Saving world")));
            mc.setScreen(new TitleScreen());
        }
    }

    private static void secondVisit(Minecraft mc) {
        if (tick == 100) {
            server(mc, (level, player) -> report(level, player, "after"));
        }
        if (tick == 105) {
            view(mc, 58, Y + 4, -8, 20, 20);
        }
        if (tick == 125) {
            shot(mc, "rejoin_2_after");
        }
        if (tick == 130) {
            server(mc, (level, player) -> {
                player.stopRiding();
                TrainCarEntity car = car(level, coach);
                stand(player, car);
            });
        }
        if (tick == 140) {
            clickCar(mc, coach, false, "get into the coach");
        }
        if (tick == 150) {
            server(mc, (level, player) -> {
                Entity vehicle = player.getVehicle();
                result("get into the coach", vehicle != null && vehicle.getUUID().equals(coach),
                        "riding " + (vehicle == null ? "nothing" : vehicle.getType().toShortString()));
                player.stopRiding();
                stand(player, car(level, coach));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.COUPLER.get()));
            });
        }
        if (tick == 160) {
            // The client sends whether the sneak key's held (not the player's sneaking flag).
            mc.player.input.shiftKeyDown = true;
            clickCar(mc, coach, false, "uncouple the coach with the coupler (sneaking)");
            mc.player.input.shiftKeyDown = false;
        }
        if (tick == 170) {
            server(mc, (level, player) -> {
                TrainCarEntity car = car(level, coach);
                result("uncouple the coach with the coupler", car != null && car.leaderId() == null, "coach's leader " + (car == null ? "-" : car.leaderId()));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.IRON_PICKAXE));
                stand(player, car(level, curveCoach));
            });
        }
        if (tick == 180) {
            clickCar(mc, curveCoach, true, "hit the coach on the curve with a pickaxe");
        }
        if (tick == 190) {
            server(mc, (level, player) -> {
                TrainCarEntity car = car(level, curveCoach);
                result("break the coach on the curve with a pickaxe (creative)", car == null, car == null ? "gone" : "still there");
                stand(player, car(level, loco));
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            });
        }
        if (tick == 200) {
            clickCar(mc, loco, false, "get into the express");
        }
        if (tick == 210) {
            server(mc, (level, player) -> {
                Entity vehicle = player.getVehicle();
                result("get into the express", vehicle != null && vehicle.getUUID().equals(loco),
                        "riding " + (vehicle == null ? "nothing" : vehicle.getType().toShortString()));
            });
        }
        if (tick == 230) {
            server(mc, (level, player) -> {
                player.stopRiding();
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PASSENGER_COACH.get()));
                player.teleportTo(level, 66, Y + 1, -1, -45, 30);
            });
        }
        if (tick == 240) {
            // The middle of the curve from (60, 0) to (72, 12): no track block there, just the curve over the grass.
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(new Vec3(69.0, Y, 4.0),
                    net.minecraft.core.Direction.UP, new BlockPos(68, Y - 1, 4), false));
        }
        if (tick == 250) {
            server(mc, (level, player) -> {
                List<com.reiko.trains.train.PassengerCoach> put = level.getEntitiesOfClass(com.reiko.trains.train.PassengerCoach.class,
                        new AABB(66, Y - 2, 1, 72, Y + 3, 7));
                result("put a coach on the middle of a curve", put.size() == 1, put.isEmpty() ? "none there" : "at " + fmt(put.getFirst().position()));
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.PASSENGER_COACH.get()));
                player.teleportTo(level, 1, Y + 1, -2, 90, 30);
            });
        }
        if (tick == 260) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, new net.minecraft.world.phys.BlockHitResult(new Vec3(0.5, Y + 0.125, 0.5),
                    net.minecraft.core.Direction.UP, new BlockPos(0, Y, 0), false));
        }
        if (tick == 270) {
            server(mc, (level, player) -> {
                List<com.reiko.trains.train.PassengerCoach> put = level.getEntitiesOfClass(com.reiko.trains.train.PassengerCoach.class,
                        new AABB(-1, Y - 2, -2, 12, Y + 3, 3));
                boolean ok = put.size() == 1 && put.getFirst().rear().isValid(level) && put.getFirst().front().isValid(level);
                result("put a coach on the last block of the line", ok, put.isEmpty() ? "none there" : "at " + fmt(put.getFirst().position()));
            });
        }
        if (tick == 280) {
            view(mc, 6, Y + 5, -9, 25, 25);
        }
        if (tick == 300) {
            shot(mc, "rejoin_3_placed_end");
            view(mc, 64, Y + 6, -6, -20, 30);
        }
        if (tick == 320) {
            shot(mc, "rejoin_4_placed_curve");
        }
        if (tick == 400) {
            server(mc, (level, player) -> {
                report(level, player, "after 20 seconds");
                ReikoTrains.LOGGER.info("REJOIN: ===== results =====");
                RESULTS.forEach(line -> ReikoTrains.LOGGER.info("REJOIN: {}", line));
            });
        }
        if (tick == 410) {
            mc.stop();
        }
    }

    private interface ServerStep {
        void run(ServerLevel level, ServerPlayer player);
    }

    private static void server(Minecraft mc, ServerStep step) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
            step.run(player.serverLevel(), player);
        });
    }

    private static TrainCarEntity car(ServerLevel level, UUID id) {
        return level.getEntity(id) instanceof TrainCarEntity car ? car : null;
    }

    /** Stands the player beside a car (north of it), facing it. */
    private static void stand(ServerPlayer player, TrainCarEntity car) {
        if (car == null) {
            ReikoTrains.LOGGER.warn("REJOIN: no car to stand by");
            return;
        }
        player.getAbilities().flying = false;
        player.onUpdateAbilities();
        player.teleportTo(player.serverLevel(), car.getX(), car.getY(), car.getZ() - 2.6, 0, 20);
    }

    /** Clicks (or hits) the car the way the client does: the hitbox section nearest the player, by a packet to the server. */
    private static void clickCar(Minecraft mc, UUID id, boolean attack, String what) {
        CarPart part = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : mc.level.getEntities(null, mc.player.getBoundingBox().inflate(8))) {
            if (entity instanceof CarPart candidate && candidate.getParent().getUUID().equals(id)) {
                double d = candidate.distanceToSqr(mc.player);
                if (d < best) {
                    best = d;
                    part = candidate;
                }
            }
        }
        if (part == null) {
            ReikoTrains.LOGGER.warn("REJOIN: {}: the client has no hitbox section of that car near the player", what);
            RESULTS.add("FAIL " + what + ": the client can't see the car to click it");
            return;
        }
        ReikoTrains.LOGGER.info("REJOIN: {}: clicking section {} at {} (car at {})", what, part.getId(), part.position(), part.getParent().position());
        if (attack) {
            mc.gameMode.attack(mc.player, part);
        } else {
            mc.gameMode.interact(mc.player, part, InteractionHand.MAIN_HAND);
        }
    }

    private static void result(String what, boolean ok, String detail) {
        String line = (ok ? "PASS " : "FAIL ") + what + " (" + detail + ")";
        RESULTS.add(line);
        ReikoTrains.LOGGER.info("REJOIN: {}", line);
    }

    private static void report(ServerLevel level, ServerPlayer player, String when) {
        List<TrainCarEntity> cars = new ArrayList<>(level.getEntitiesOfClass(TrainCarEntity.class, new AABB(-20, Y - 10, -20, 100, Y + 20, 30)));
        cars.sort(Comparator.comparingDouble(Entity::getX));
        ReikoTrains.LOGGER.info("REJOIN: --- {}: {} cars; player riding {} ---", when, cars.size(),
                player.getVehicle() == null ? "nothing" : player.getVehicle().getType().toShortString());
        for (TrainCarEntity car : cars) {
            Vec3 partAt = car.getParts()[0].position();
            ReikoTrains.LOGGER.info("REJOIN: {} {} at {}: on track {}, leader {}, follower {}, first hitbox section at {} ({} from the car), bogies turned {} / {}",
                    car.getType().toShortString(), car.getUUID().toString().substring(0, 8), fmt(car.position()), car.isOnTrack(),
                    car.leaderId() == null ? "-" : car.leaderId().toString().substring(0, 8),
                    car.followerId() == null ? "-" : car.followerId().toString().substring(0, 8), fmt(partAt),
                    String.format("%.1f", partAt.distanceTo(car.position())), String.format("%.1f", car.frontBogieYaw()),
                    String.format("%.1f", car.rearBogieYaw()));
        }
    }

    private static String fmt(Vec3 v) {
        return String.format("(%.1f, %.1f, %.1f)", v.x, v.y, v.z);
    }

    private static void view(Minecraft mc, double x, double y, double z, float yaw, float pitch) {
        net.minecraft.world.entity.decoration.ArmorStand stand = new net.minecraft.world.entity.decoration.ArmorStand(mc.level, x, y, z);
        stand.setPos(x, y - stand.getEyeHeight(), z);
        stand.setYRot(yaw);
        stand.yRotO = yaw;
        stand.setYHeadRot(yaw);
        stand.yHeadRotO = yaw;
        stand.setXRot(pitch);
        stand.xRotO = pitch;
        stand.xo = stand.getX();
        stand.yo = stand.getY();
        stand.zo = stand.getZ();
        stand.xOld = stand.getX();
        stand.yOld = stand.getY();
        stand.zOld = stand.getZ();
        stand.setInvisible(true);
        mc.setCameraEntity(stand);
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), message -> ReikoTrains.LOGGER.info("REJOIN: {}", message.getString()));
        mc.setCameraEntity(mc.player);
    }

    @SuppressWarnings("unused")
    private static void unused(Consumer<Minecraft> ignored) {
    }
}
