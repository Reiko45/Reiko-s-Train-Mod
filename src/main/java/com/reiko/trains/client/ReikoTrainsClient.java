package com.reiko.trains.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.reiko.trains.ReikoTrains;
import com.reiko.trains.client.model.geometry.BogieGeometry;
import com.reiko.trains.client.model.geometry.FreightWagonGeometry;
import com.reiko.trains.client.model.geometry.PassengerCoachGeometry;
import com.reiko.trains.client.model.geometry.SteamLocomotiveGeometry;
import com.reiko.trains.network.TrainControlPayload;
import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModMenus;
import com.reiko.trains.train.SteamLocomotive;
import java.util.function.Function;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Client setup: the train and track renderers, the curve preview, the wagon screen, the cab gauges and the
 * driver's controls. Driving uses forward and back for the throttle (a notch per press; hold to keep going), jump
 * for the brake, left and right to pick the way at the next junction, sneak to get out, and its own key (H) for
 * the whistle.
 */
@Mod(value = ReikoTrains.MODID, dist = Dist.CLIENT)
public final class ReikoTrainsClient {
    public static final KeyMapping WHISTLE = new KeyMapping("key.reikotrains.whistle", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, InputConstants.KEY_H, "key.categories.reikotrains");

    static final ModelLayerLocation LOCOMOTIVE_LAYER = new ModelLayerLocation(ReikoTrains.id("steam_locomotive"), "main");
    static final ModelLayerLocation COACH_LAYER = new ModelLayerLocation(ReikoTrains.id("passenger_coach"), "main");
    static final ModelLayerLocation WAGON_LAYER = new ModelLayerLocation(ReikoTrains.id("freight_wagon"), "main");
    static final ModelLayerLocation BOGIE_LAYER = new ModelLayerLocation(ReikoTrains.id("bogie"), "main");

    /** Ticks until a held throttle key moves another notch. */
    private static int throttleRepeat;
    private static boolean wasUp;
    private static boolean wasDown;
    private static boolean wasBraking;
    private static int lastSteer;
    private static boolean wasDriving;

    public ReikoTrainsClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(ReikoTrainsClient::registerLayers);
        modBus.addListener(ReikoTrainsClient::registerRenderers);
        modBus.addListener(ReikoTrainsClient::registerKeys);
        modBus.addListener(ReikoTrainsClient::registerHud);
        modBus.addListener(ReikoTrainsClient::registerScreens);
        NeoForge.EVENT_BUS.addListener(ReikoTrainsClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(TrackPreview::render);
    }

    private static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(LOCOMOTIVE_LAYER, SteamLocomotiveGeometry::create);
        event.registerLayerDefinition(COACH_LAYER, PassengerCoachGeometry::create);
        event.registerLayerDefinition(WAGON_LAYER, FreightWagonGeometry::create);
        event.registerLayerDefinition(BOGIE_LAYER, BogieGeometry::create);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        Function<EntityRendererProvider.Context, TrainModel> bogie =
                c -> TrainModel.simple(c.bakeLayer(BOGIE_LAYER), BogieGeometry.WHEELS, BogieGeometry.WHEEL_RADII);
        event.registerEntityRenderer(ModEntities.STEAM_LOCOMOTIVE.get(), context -> new TrainCarRenderer<>(context,
                c -> new TrainModel(c.bakeLayer(LOCOMOTIVE_LAYER), SteamLocomotiveGeometry.WHEELS, SteamLocomotiveGeometry.WHEEL_RADII,
                        SteamLocomotiveGeometry.RODS, SteamLocomotiveGeometry.CRANK_RADIUS, SteamLocomotiveGeometry.DRIVER_RADIUS, new String[0]),
                null, ReikoTrains.id("textures/entity/steam_locomotive.png")));
        event.registerEntityRenderer(ModEntities.PASSENGER_COACH.get(), context -> new TrainCarRenderer<>(context,
                c -> TrainModel.simple(c.bakeLayer(COACH_LAYER), new String[0], new float[0]),
                bogie, ReikoTrains.id("textures/entity/passenger_coach.png")));
        event.registerEntityRenderer(ModEntities.FREIGHT_WAGON.get(), context -> new TrainCarRenderer<>(context,
                c -> new TrainModel(c.bakeLayer(WAGON_LAYER), new String[0], new float[0], new String[0], 0.0F, 1.0F, FreightWagonGeometry.CARGO),
                bogie, ReikoTrains.id("textures/entity/freight_wagon.png")));
        event.registerBlockEntityRenderer(ModBlockEntities.TRACK.get(), TrackRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.STATION.get(), StationRenderer::new);
    }

    private static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(WHISTLE);
    }

    private static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAbove(VanillaGuiLayers.HOTBAR, ReikoTrains.id("locomotive_gauges"), new TrainHud());
    }

    private static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.FREIGHT_WAGON.get(), FreightWagonScreen::new);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        TrackPreview.tick();
        drive();
    }

    /** Turns the driver's keys into throttle notches, the brake, steering and the whistle. */
    private static void drive() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        boolean driving = player != null && player.getVehicle() instanceof SteamLocomotive locomotive && locomotive.driver() == player;
        if (!driving) {
            wasDriving = false;
            wasUp = wasDown = wasBraking = false;
            lastSteer = 0;
            while (WHISTLE.consumeClick()) {
                // not driving: the key does nothing
            }
            return;
        }
        if (!wasDriving) {
            wasDriving = true;
            player.displayClientMessage(Component.translatable("message.reikotrains.controls",
                    minecraft.options.keyUp.getTranslatedKeyMessage(), minecraft.options.keyDown.getTranslatedKeyMessage(),
                    minecraft.options.keyJump.getTranslatedKeyMessage(), minecraft.options.keyLeft.getTranslatedKeyMessage(),
                    minecraft.options.keyRight.getTranslatedKeyMessage(), WHISTLE.getTranslatedKeyMessage()), true);
        }
        boolean up = minecraft.options.keyUp.isDown();
        boolean down = minecraft.options.keyDown.isDown();
        if (up != down) {
            boolean pressed = up ? !wasUp : !wasDown;
            if (pressed) {
                throttleRepeat = 8;
                send(up ? TrainControlPayload.THROTTLE_UP : TrainControlPayload.THROTTLE_DOWN);
            } else if (--throttleRepeat <= 0) {
                throttleRepeat = 5;
                send(up ? TrainControlPayload.THROTTLE_UP : TrainControlPayload.THROTTLE_DOWN);
            }
        }
        wasUp = up;
        wasDown = down;
        boolean braking = minecraft.options.keyJump.isDown();
        if (braking != wasBraking) {
            wasBraking = braking;
            send(braking ? TrainControlPayload.BRAKE_ON : TrainControlPayload.BRAKE_OFF);
        }
        boolean left = minecraft.options.keyLeft.isDown();
        boolean right = minecraft.options.keyRight.isDown();
        int steer = left == right ? 0 : left ? -1 : 1;
        if (steer != lastSteer) {
            lastSteer = steer;
            send(steer < 0 ? TrainControlPayload.STEER_LEFT : steer > 0 ? TrainControlPayload.STEER_RIGHT : TrainControlPayload.STEER_STRAIGHT);
        }
        while (WHISTLE.consumeClick()) {
            send(TrainControlPayload.WHISTLE);
        }
    }

    private static void send(int action) {
        PacketDistributor.sendToServer(new TrainControlPayload(action));
    }
}
