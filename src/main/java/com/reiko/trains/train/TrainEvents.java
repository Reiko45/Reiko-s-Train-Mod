package com.reiko.trains.train;

import com.reiko.trains.ReikoTrains;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Game events the trains need to hear about. */
@EventBusSubscriber(modid = ReikoTrains.MODID)
public final class TrainEvents {
    private TrainEvents() {
    }

    /**
     * Someone leaving the game while riding a train gets off first, beside it. Minecraft takes a vehicle away with the
     * player riding it (a boat, a minecart) and brings it back when they return, but a train car belongs on its track,
     * coupled to the rest of its train: taken away, it would leave a gap the train couldn't move without.
     */
    @SubscribeEvent
    static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        Player player = event.getEntity();
        if (!player.level().isClientSide && player.getRootVehicle() instanceof TrainCarEntity) {
            player.stopRiding();
        }
    }
}
