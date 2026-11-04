package com.reiko.trains.network;

import com.reiko.trains.ReikoTrains;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Server to client, every couple of seconds: where the trains in {@code dimension} are, for minimap mods. */
public record TrainMapPayload(ResourceLocation dimension, List<Pin> trains) implements CustomPacketPayload {
    public static final Type<TrainMapPayload> TYPE = new Type<>(ReikoTrains.id("train_map"));
    public static final StreamCodec<FriendlyByteBuf, TrainMapPayload> STREAM_CODEC = StreamCodec.of(TrainMapPayload::write, TrainMapPayload::read);

    /**
     * A train: its leading locomotive, where it is, what kind it is, how many cars, and its timetable's stop; and, if
     * it's loaded (near someone), how fast it's going, who's driving it, and whether it's running to its timetable.
     */
    public record Pin(UUID id, Vec3 pos, ResourceLocation type, int cars, String stop, boolean atStop, boolean loaded, int kmh, String driver,
                      boolean auto) {
        public Pin(UUID id, Vec3 pos, ResourceLocation type, int cars, String stop, boolean atStop) {
            this(id, pos, type, cars, stop, atStop, false, 0, "", false);
        }
    }

    private static void write(FriendlyByteBuf buf, TrainMapPayload payload) {
        buf.writeResourceLocation(payload.dimension());
        buf.writeVarInt(payload.trains().size());
        for (Pin pin : payload.trains()) {
            buf.writeUUID(pin.id());
            buf.writeDouble(pin.pos().x);
            buf.writeDouble(pin.pos().y);
            buf.writeDouble(pin.pos().z);
            buf.writeResourceLocation(pin.type());
            buf.writeVarInt(pin.cars());
            buf.writeUtf(pin.stop(), 64);
            buf.writeBoolean(pin.atStop());
            buf.writeBoolean(pin.loaded());
            buf.writeVarInt(pin.kmh());
            buf.writeUtf(pin.driver(), 64);
            buf.writeBoolean(pin.auto());
        }
    }

    private static TrainMapPayload read(FriendlyByteBuf buf) {
        ResourceLocation dimension = buf.readResourceLocation();
        int count = buf.readVarInt();
        List<Pin> pins = new ArrayList<>(Math.min(count, 256));
        for (int i = 0; i < count; i++) {
            pins.add(new Pin(buf.readUUID(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()), buf.readResourceLocation(),
                    buf.readVarInt(), buf.readUtf(64), buf.readBoolean(), buf.readBoolean(), buf.readVarInt(), buf.readUtf(64), buf.readBoolean()));
        }
        return new TrainMapPayload(dimension, pins);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
