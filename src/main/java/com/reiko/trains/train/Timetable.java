package com.reiko.trains.train;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A list of stations to call at, in order, round and round. */
public final class Timetable {
    public static final int MAX_STOPS = 16;

    private Timetable() {
    }

    /** One stop: the station sign, the track block trains stop at for it, and its name when it was added. */
    public record Stop(BlockPos station, BlockPos track, String name) {
        public static final Codec<Stop> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("station").forGetter(Stop::station),
                BlockPos.CODEC.fieldOf("track").forGetter(Stop::track),
                Codec.STRING.fieldOf("name").forGetter(Stop::name)).apply(instance, Stop::new));
        public static final StreamCodec<ByteBuf, Stop> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Stop::station,
                BlockPos.STREAM_CODEC, Stop::track,
                ByteBufCodecs.stringUtf8(64), Stop::name,
                Stop::new);
    }

    public static final Codec<List<Stop>> CODEC = Stop.CODEC.listOf();
    public static final StreamCodec<ByteBuf, List<Stop>> STREAM_CODEC = Stop.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STOPS));
}
