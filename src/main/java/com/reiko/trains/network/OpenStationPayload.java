package com.reiko.trains.network;

import com.reiko.trains.ReikoTrains;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server to client: open the settings screen for the station at {@code pos}. */
public record OpenStationPayload(BlockPos pos, String name, int dwellSeconds) implements CustomPacketPayload {
    public static final Type<OpenStationPayload> TYPE = new Type<>(ReikoTrains.id("open_station"));
    public static final StreamCodec<ByteBuf, OpenStationPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenStationPayload::pos,
            ByteBufCodecs.stringUtf8(64), OpenStationPayload::name,
            ByteBufCodecs.VAR_INT, OpenStationPayload::dwellSeconds,
            OpenStationPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
