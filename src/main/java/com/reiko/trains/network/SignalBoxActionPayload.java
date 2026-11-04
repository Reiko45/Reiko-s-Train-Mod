package com.reiko.trains.network;

import com.reiko.trains.ReikoTrains;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server, from a signal box's screen: hold a signal at red or let it go, or throw a switch. */
public record SignalBoxActionPayload(BlockPos box, int action, BlockPos target) implements CustomPacketPayload {
    public static final Type<SignalBoxActionPayload> TYPE = new Type<>(ReikoTrains.id("signal_box_action"));
    public static final StreamCodec<FriendlyByteBuf, SignalBoxActionPayload> STREAM_CODEC = StreamCodec.of(SignalBoxActionPayload::write,
            SignalBoxActionPayload::read);

    public static final int SIGNAL = 0;
    public static final int SWITCH = 1;

    private static void write(FriendlyByteBuf buf, SignalBoxActionPayload payload) {
        buf.writeBlockPos(payload.box());
        buf.writeVarInt(payload.action());
        buf.writeBlockPos(payload.target());
    }

    private static SignalBoxActionPayload read(FriendlyByteBuf buf) {
        return new SignalBoxActionPayload(buf.readBlockPos(), buf.readVarInt(), buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
