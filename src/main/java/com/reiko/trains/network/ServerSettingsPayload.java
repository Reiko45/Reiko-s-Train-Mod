package com.reiko.trains.network;

import com.reiko.trains.ReikoTrains;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client, on joining: the server's settings the client shows things by (JEI's fuel times): how fast fuel
 * burns, and whether it's needed at all.
 */
public record ServerSettingsPayload(double fuelUse, boolean needsFuel) implements CustomPacketPayload {
    public static final Type<ServerSettingsPayload> TYPE = new Type<>(ReikoTrains.id("server_settings"));
    public static final StreamCodec<ByteBuf, ServerSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, ServerSettingsPayload::fuelUse,
            ByteBufCodecs.BOOL, ServerSettingsPayload::needsFuel,
            ServerSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
