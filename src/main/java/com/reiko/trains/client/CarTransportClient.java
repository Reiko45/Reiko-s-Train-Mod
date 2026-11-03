package com.reiko.trains.client;

import com.reiko.trains.network.CarTransportPayload;
import com.reiko.trains.train.CarCargo;
import com.reiko.trains.train.CarTransporter;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Driving cars from Reiko's Car Mod on and off car transporters: keep going (forwards or in reverse) against a car
 * transporter's free end for a moment and the car goes aboard; sitting in one aboard with the train stopped, keep
 * going forwards or back and it drives off (or along the train). The server checks it all again (see {@link CarCargo}).
 * A hint shows when you're lined up at an end, and when you're aboard.
 */
final class CarTransportClient {
    /** How long the key's held against the end (or aboard) before it goes, in ticks. */
    private static final int HOLD = 6;
    private static int held;
    private static int heldDirection;
    /** Waiting for the keys to be let go before going again. */
    private static boolean latched;
    @Nullable
    private static Entity hinted;

    private CarTransportClient() {
    }

    static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        Entity car = !CarCargo.CAR_MOD || player == null || minecraft.screen != null ? null : CarCargo.drivenCar(player);
        if (car == null) {
            held = 0;
            hinted = null;
            return;
        }
        int direction = player.input.up ? 1 : player.input.down ? -1 : 0;
        if (direction == 0) {
            latched = false;
        }
        boolean ready;
        if (car.getVehicle() instanceof CarTransporter wagon) {
            if (hinted != wagon) {
                hinted = wagon;
                player.displayClientMessage(Component.translatable("message.reikotrains.car_transporter.aboard"), true);
            }
            ready = direction != 0 && CarCargo.standing(wagon);
        } else {
            CarCargo.Approach near = CarCargo.approach(car, 1, CarCargo.CLIENT_REACH);
            if (near == null) {
                near = CarCargo.approach(car, -1, CarCargo.CLIENT_REACH);
            }
            if (near != null && hinted != near.wagon()) {
                hinted = near.wagon();
                player.displayClientMessage(Component.translatable("message.reikotrains.car_transporter.hint"), true);
            } else if (near == null) {
                hinted = null;
            }
            double speed = Math.hypot(car.getX() - car.xo, car.getZ() - car.zo);
            ready = direction != 0 && speed < 0.35 && CarCargo.approach(car, direction, CarCargo.CLIENT_REACH) != null;
        }
        if (ready && !latched && direction == heldDirection) {
            if (++held >= HOLD) {
                PacketDistributor.sendToServer(new CarTransportPayload(direction));
                latched = true;
                held = 0;
            }
        } else {
            held = ready && !latched ? 1 : 0;
            heldDirection = direction;
        }
    }
}
