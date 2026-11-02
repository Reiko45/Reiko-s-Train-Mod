package com.reiko.trains.network;

import com.reiko.trains.ReikoTrains;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: a station's new name and wait. */
public record StationSettingsPayload(BlockPos pos, String name, int dwellSeconds) implements CustomPacketPayload {
    public static final Type<StationSettingsPayload> TYPE = new Type<>(ReikoTrains.id("station_settings"));
    public static final StreamCodec<ByteBuf, StationSettingsPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, StationSettingsPayload::pos,
            ByteBufCodecs.stringUtf8(64), StationSettingsPayload::name,
            ByteBufCodecs.VAR_INT, StationSettingsPayload::dwellSeconds,
            StationSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
