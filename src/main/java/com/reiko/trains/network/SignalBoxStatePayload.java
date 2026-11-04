package com.reiko.trains.network;

import com.reiko.trains.ReikoTrains;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server to client, five times a second to a player with a signal box's screen open: what's happening round the box.
 * The signals (where each lamp goes on the map, which way it guards, its aspect, and whether the box is holding it),
 * the switch stands (their junction, the ways out of it, which way it's set, whether redstone holds it), the train cars,
 * and the turntables. Places are in blocks east and south of the middle of the box.
 */
public record SignalBoxStatePayload(BlockPos box, List<SignalMark> signals, List<SwitchMark> switches, List<CarMark> cars,
                                    List<TableMark> tables, List<RouteMark> routes) implements CustomPacketPayload {
    public static final Type<SignalBoxStatePayload> TYPE = new Type<>(ReikoTrains.id("signal_box_state"));
    public static final StreamCodec<FriendlyByteBuf, SignalBoxStatePayload> STREAM_CODEC = StreamCodec.of(SignalBoxStatePayload::write,
            SignalBoxStatePayload::read);

    public static final int RED = 0;
    public static final int GREEN = 1;
    public static final int YELLOW = 2;
    public static final int DOUBLE_YELLOW = 3;
    /** How many points along each way out of a switch's junction are sent (every block and a half). */
    public static final int WAY_POINTS = 4;

    /** A signal: the block, its lamp's place on the map, its track block's place, which way it guards, its aspect, held. */
    public record SignalMark(BlockPos pos, float x, float z, float nodeX, float nodeZ, float dx, float dz, int aspect, boolean held) {
    }

    /**
     * A switch stand: the block, its junction's place, the ways out ({@link #WAY_POINTS} points along each, x and z), the
     * one it's set to.
     */
    public record SwitchMark(BlockPos pos, float x, float z, float[] ways, int set, boolean locked) {
    }

    /** A train car: its entity id, middle, which way it faces, half its length, and whether it's a locomotive. */
    public record CarMark(int id, float x, float z, float fx, float fz, float half, boolean loco) {
    }

    /** A turntable: its pivot block, its middle, which way its bridge points (degrees clockwise from east), and how wide its pit is. */
    public record TableMark(BlockPos pos, float x, float z, float angle, float radius) {
    }

    /** A route set from the box: the signal it starts at, the one it ends at, and the track between (x and z in turn). */
    public record RouteMark(BlockPos entry, BlockPos exit, float[] points) {
    }

    private static void write(FriendlyByteBuf buf, SignalBoxStatePayload payload) {
        buf.writeBlockPos(payload.box());
        buf.writeVarInt(payload.signals().size());
        for (SignalMark signal : payload.signals()) {
            buf.writeBlockPos(signal.pos());
            buf.writeFloat(signal.x());
            buf.writeFloat(signal.z());
            buf.writeFloat(signal.nodeX());
            buf.writeFloat(signal.nodeZ());
            buf.writeFloat(signal.dx());
            buf.writeFloat(signal.dz());
            buf.writeByte(signal.aspect());
            buf.writeBoolean(signal.held());
        }
        buf.writeVarInt(payload.switches().size());
        for (SwitchMark stand : payload.switches()) {
            buf.writeBlockPos(stand.pos());
            buf.writeFloat(stand.x());
            buf.writeFloat(stand.z());
            buf.writeVarInt(stand.ways().length / 2);
            for (float v : stand.ways()) {
                buf.writeFloat(v);
            }
            buf.writeVarInt(stand.set());
            buf.writeBoolean(stand.locked());
        }
        buf.writeVarInt(payload.cars().size());
        for (CarMark car : payload.cars()) {
            buf.writeVarInt(car.id());
            buf.writeFloat(car.x());
            buf.writeFloat(car.z());
            buf.writeFloat(car.fx());
            buf.writeFloat(car.fz());
            buf.writeFloat(car.half());
            buf.writeBoolean(car.loco());
        }
        buf.writeVarInt(payload.tables().size());
        for (TableMark table : payload.tables()) {
            buf.writeBlockPos(table.pos());
            buf.writeFloat(table.x());
            buf.writeFloat(table.z());
            buf.writeFloat(table.angle());
            buf.writeFloat(table.radius());
        }
        buf.writeVarInt(payload.routes().size());
        for (RouteMark route : payload.routes()) {
            buf.writeBlockPos(route.entry());
            buf.writeBlockPos(route.exit());
            buf.writeVarInt(route.points().length / 2);
            for (float v : route.points()) {
                buf.writeFloat(v);
            }
        }
    }

    private static SignalBoxStatePayload read(FriendlyByteBuf buf) {
        BlockPos box = buf.readBlockPos();
        int count = Math.min(buf.readVarInt(), 4096);
        List<SignalMark> signals = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            signals.add(new SignalMark(buf.readBlockPos(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readByte(), buf.readBoolean()));
        }
        count = Math.min(buf.readVarInt(), 4096);
        List<SwitchMark> switches = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockPos pos = buf.readBlockPos();
            float x = buf.readFloat();
            float z = buf.readFloat();
            float[] ways = new float[Math.min(buf.readVarInt(), 64) * 2];
            for (int j = 0; j < ways.length; j++) {
                ways[j] = buf.readFloat();
            }
            switches.add(new SwitchMark(pos, x, z, ways, buf.readVarInt(), buf.readBoolean()));
        }
        count = Math.min(buf.readVarInt(), 4096);
        List<CarMark> cars = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cars.add(new CarMark(buf.readVarInt(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(),
                    buf.readBoolean()));
        }
        count = Math.min(buf.readVarInt(), 256);
        List<TableMark> tables = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tables.add(new TableMark(buf.readBlockPos(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
        }
        count = Math.min(buf.readVarInt(), 256);
        List<RouteMark> routes = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockPos entry = buf.readBlockPos();
            BlockPos exit = buf.readBlockPos();
            float[] points = new float[Math.min(buf.readVarInt(), 2048) * 2];
            for (int j = 0; j < points.length; j++) {
                points[j] = buf.readFloat();
            }
            routes.add(new RouteMark(entry, exit, points));
        }
        return new SignalBoxStatePayload(box, signals, switches, cars, tables, routes);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
