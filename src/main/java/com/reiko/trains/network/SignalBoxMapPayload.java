package com.reiko.trains.network;

import com.reiko.trains.ReikoTrains;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client, to a player with a signal box's screen open (when it opens, and every few seconds): the shape of the
 * track around the box, as lines, and the stations on it. Points are in blocks east and south of the middle of the box,
 * sent in eighths of a block.
 */
public record SignalBoxMapPayload(BlockPos box, List<float[]> lines, List<Label> stations, List<float[]> faded) implements CustomPacketPayload {
    public static final Type<SignalBoxMapPayload> TYPE = new Type<>(ReikoTrains.id("signal_box_map"));
    public static final StreamCodec<FriendlyByteBuf, SignalBoxMapPayload> STREAM_CODEC = StreamCodec.of(SignalBoxMapPayload::write,
            SignalBoxMapPayload::read);
    private static final float UNIT = 8;

    /** A station's name, where it stops trains. */
    public record Label(String name, float x, float z) {
    }

    static void writePoint(FriendlyByteBuf buf, float v) {
        buf.writeShort(Math.round(Math.max(-4000, Math.min(4000, v * UNIT))));
    }

    static float readPoint(FriendlyByteBuf buf) {
        return buf.readShort() / UNIT;
    }

    private static void write(FriendlyByteBuf buf, SignalBoxMapPayload payload) {
        buf.writeBlockPos(payload.box());
        buf.writeVarInt(payload.lines().size());
        for (float[] line : payload.lines()) {
            buf.writeVarInt(line.length / 2);
            for (float v : line) {
                writePoint(buf, v);
            }
        }
        buf.writeVarInt(payload.stations().size());
        for (Label station : payload.stations()) {
            buf.writeUtf(station.name(), 64);
            writePoint(buf, station.x());
            writePoint(buf, station.z());
        }
        writeLines(buf, payload.faded());
    }

    private static void writeLines(FriendlyByteBuf buf, List<float[]> lines) {
        buf.writeVarInt(lines.size());
        for (float[] line : lines) {
            buf.writeVarInt(line.length / 2);
            for (float v : line) {
                writePoint(buf, v);
            }
        }
    }

    private static List<float[]> readLines(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<float[]> lines = new ArrayList<>(Math.min(count, 8192));
        for (int i = 0; i < count; i++) {
            int points = Math.min(buf.readVarInt(), 4096);
            float[] line = new float[points * 2];
            for (int j = 0; j < line.length; j++) {
                line[j] = readPoint(buf);
            }
            lines.add(line);
        }
        return lines;
    }

    private static SignalBoxMapPayload read(FriendlyByteBuf buf) {
        BlockPos box = buf.readBlockPos();
        int count = buf.readVarInt();
        List<float[]> lines = new ArrayList<>(Math.min(count, 8192));
        for (int i = 0; i < count; i++) {
            int points = Math.min(buf.readVarInt(), 4096);
            float[] line = new float[points * 2];
            for (int j = 0; j < line.length; j++) {
                line[j] = readPoint(buf);
            }
            lines.add(line);
        }
        int stationCount = buf.readVarInt();
        List<Label> stations = new ArrayList<>(Math.min(stationCount, 256));
        for (int i = 0; i < stationCount; i++) {
            stations.add(new Label(buf.readUtf(64), readPoint(buf), readPoint(buf)));
        }
        return new SignalBoxMapPayload(box, lines, stations, readLines(buf));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
