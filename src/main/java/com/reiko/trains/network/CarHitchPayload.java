package com.reiko.trains.network;

import com.reiko.trains.ReikoTrains;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the driver of a car aboard a car transporter pressed the car mod's hitch key. */
public record CarHitchPayload() implements CustomPacketPayload {
    public static final Type<CarHitchPayload> TYPE = new Type<>(ReikoTrains.id("car_hitch"));
    public static final StreamCodec<ByteBuf, CarHitchPayload> STREAM_CODEC = StreamCodec.unit(new CarHitchPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
