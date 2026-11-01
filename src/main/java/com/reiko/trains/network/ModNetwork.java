package com.reiko.trains.network;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.train.SteamLocomotive;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = ReikoTrains.MODID)
public final class ModNetwork {
    private static final String PROTOCOL_VERSION = "1";

    private ModNetwork() {
    }

    @SubscribeEvent
    static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(TrainControlPayload.TYPE, TrainControlPayload.STREAM_CODEC, ModNetwork::handleControl);
    }

    /** Only the driver (the first one aboard) can work the controls. */
    private static void handleControl(TrainControlPayload payload, IPayloadContext context) {
        Player player = context.player();
        if (player.getVehicle() instanceof SteamLocomotive locomotive && locomotive.driver() == player) {
            locomotive.control(payload.action());
        }
    }
}
