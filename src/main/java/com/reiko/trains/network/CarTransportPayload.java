package com.reiko.trains.network;

import com.reiko.trains.ReikoTrains;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the driver of a car kept going forwards (+1) or in reverse (-1) at a car transporter. */
public record CarTransportPayload(int direction) implements CustomPacketPayload {
    public static final Type<CarTransportPayload> TYPE = new Type<>(ReikoTrains.id("car_transport"));
    public static final StreamCodec<ByteBuf, CarTransportPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CarTransportPayload::direction, CarTransportPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
