package com.reiko.trains.train;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** A list of stations to call at, in order, round and round. */
public final class Timetable {
    public static final int MAX_STOPS = 16;

    private Timetable() {
    }

    /**
     * One stop: the station sign, the track block trains stop at for it, its name when it was added, and what the train
     * does with wagons there: nothing ({@link #NONE}), leave that many cars at the end it isn't leaving by (or all of
     * them, {@link #DROP_ALL}), or pick up cars waiting there ({@link #PICK_UP}).
     */
    public record Stop(BlockPos station, BlockPos track, String name, int wagons) {
        public static final int NONE = 0;
        public static final int PICK_UP = -1;
        /** Leave every car at that end, up to the first locomotive (or tender). */
        public static final int DROP_ALL = 99;
        /** The most cars a stop can be set to leave, one by one, before "all". */
        public static final int MAX_DROP = 4;

        public static final Codec<Stop> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockPos.CODEC.fieldOf("station").forGetter(Stop::station),
                BlockPos.CODEC.fieldOf("track").forGetter(Stop::track),
                Codec.STRING.fieldOf("name").forGetter(Stop::name),
                Codec.INT.optionalFieldOf("wagons", NONE).forGetter(Stop::wagons)).apply(instance, Stop::new));
        public static final StreamCodec<ByteBuf, Stop> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Stop::station,
                BlockPos.STREAM_CODEC, Stop::track,
                ByteBufCodecs.stringUtf8(64), Stop::name,
                ByteBufCodecs.VAR_INT, Stop::wagons,
                Stop::new);

        public Stop(BlockPos station, BlockPos track, String name) {
            this(station, track, name, NONE);
        }

        public boolean dropsWagons() {
            return wagons > 0;
        }

        public boolean picksUpWagons() {
            return wagons == PICK_UP;
        }

        public Stop withWagons(int wagons) {
            return new Stop(station, track, name, wagons);
        }

        /** The next thing to do with wagons, going round: nothing, leave 1, 2, 3, 4, leave all, pick up. */
        public int nextWagons() {
            if (wagons == PICK_UP) {
                return NONE;
            }
            if (wagons == DROP_ALL) {
                return PICK_UP;
            }
            return wagons >= MAX_DROP ? DROP_ALL : wagons + 1;
        }

        /** What the train does with wagons here, in words (empty for nothing). */
        public Component describeWagons() {
            if (wagons == PICK_UP) {
                return Component.translatable("timetable.reikotrains.wagons.pick_up");
            }
            if (wagons == DROP_ALL) {
                return Component.translatable("timetable.reikotrains.wagons.drop_all");
            }
            if (wagons > 0) {
                return Component.translatable(wagons == 1 ? "timetable.reikotrains.wagons.drop_one" : "timetable.reikotrains.wagons.drop", wagons);
            }
            return Component.translatable("timetable.reikotrains.wagons.none");
        }
    }

    public static final Codec<List<Stop>> CODEC = Stop.CODEC.listOf();
    public static final StreamCodec<ByteBuf, List<Stop>> STREAM_CODEC = Stop.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STOPS));
}
