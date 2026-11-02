package com.reiko.trains.network;

import com.reiko.trains.ReikoTrains;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the driver worked one of the locomotive's controls. */
public record TrainControlPayload(int action) implements CustomPacketPayload {
    public static final int THROTTLE_UP = 0;
    public static final int THROTTLE_DOWN = 1;
    public static final int BRAKE_ON = 2;
    public static final int BRAKE_OFF = 3;
    public static final int WHISTLE = 4;
    public static final int STEER_LEFT = 5;
    public static final int STEER_STRAIGHT = 6;
    public static final int STEER_RIGHT = 7;

    public static final Type<TrainControlPayload> TYPE = new Type<>(ReikoTrains.id("train_control"));
    public static final StreamCodec<ByteBuf, TrainControlPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(TrainControlPayload::new, TrainControlPayload::action);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
