package com.reiko.trains.network;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.train.CarCargo;
import com.reiko.trains.train.Locomotive;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ReikoTrains.MODID)
public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "11";

    private ModNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(TrainControlPayload.TYPE, TrainControlPayload.STREAM_CODEC, ModNetwork::handleControl);
        registrar.playToServer(StationSettingsPayload.TYPE, StationSettingsPayload.STREAM_CODEC, ModNetwork::handleStation);
        registrar.playToServer(CarTransportPayload.TYPE, CarTransportPayload.STREAM_CODEC, ModNetwork::handleCarTransport);
        registrar.playToClient(OpenStationPayload.TYPE, OpenStationPayload.STREAM_CODEC,
                (payload, context) -> com.reiko.trains.client.StationScreen.open(payload));
        registrar.playToClient(TrainMapPayload.TYPE, TrainMapPayload.STREAM_CODEC,
                (payload, context) -> com.reiko.trains.client.TrainMinimap.receive(payload));
        registrar.playToClient(SignalBoxMapPayload.TYPE, SignalBoxMapPayload.STREAM_CODEC,
                (payload, context) -> com.reiko.trains.client.SignalBoxScreen.receive(payload));
        registrar.playToClient(SignalBoxStatePayload.TYPE, SignalBoxStatePayload.STREAM_CODEC,
                (payload, context) -> com.reiko.trains.client.SignalBoxScreen.receive(payload));
        registrar.playToServer(SignalBoxActionPayload.TYPE, SignalBoxActionPayload.STREAM_CODEC, ModNetwork::handleSignalBox);
    }

    /** Only the driver (the first one aboard) can work the controls. */
    private static void handleControl(TrainControlPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player.getVehicle() instanceof Locomotive locomotive && locomotive.driver() == player) {
            locomotive.control(payload.action());
        }
    }

    /** A car's driver at a car transporter: on, along or off. */
    private static void handleCarTransport(CarTransportPayload payload, IPayloadContext context) {
        Component said = CarCargo.drive(context.player(), payload.direction() < 0 ? -1 : 1);
        if (said != null) {
            context.player().displayClientMessage(said, true);
        }
    }

    /** A player at a signal box's screen works a signal or a switch from it. */
    private static void handleSignalBox(SignalBoxActionPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player.containerMenu instanceof com.reiko.trains.menu.SignalBoxMenu menu && menu.pos().equals(payload.box()) && menu.stillValid(player)
                && player.level().getBlockEntity(payload.box()) instanceof com.reiko.trains.signal.SignalBoxBlockEntity box) {
            com.reiko.trains.signal.SignalBoxBlockEntity.Done done = payload.action() == SignalBoxActionPayload.SIGNAL
                    ? box.toggleSignal(payload.target()) : box.throwSwitch(payload.target());
            if (done == com.reiko.trains.signal.SignalBoxBlockEntity.Done.LOCKED) {
                player.displayClientMessage(Component.translatable("message.reikotrains.switch.locked").withStyle(net.minecraft.ChatFormatting.RED), true);
            }
            box.sendState();
        }
    }

    /** Anyone close enough to the station can rename it. */
    private static void handleStation(StationSettingsPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player.distanceToSqr(payload.pos().getCenter()) < 64 && player.level().isLoaded(payload.pos())
                && player.level().getBlockEntity(payload.pos()) instanceof StationBlockEntity station) {
            station.setName(payload.name());
            station.setDwellSeconds(payload.dwellSeconds());
        }
    }
}
