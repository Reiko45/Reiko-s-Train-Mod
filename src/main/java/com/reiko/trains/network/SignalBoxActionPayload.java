package com.reiko.trains.network;

import com.reiko.trains.ReikoTrains;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server, from a signal box's screen: hold a signal at red or let it go, throw a switch, set (or cancel) a
 * route from one signal to another, or turn a turntable.
 */
public record SignalBoxActionPayload(BlockPos box, int action, BlockPos target, BlockPos target2) implements CustomPacketPayload {
    public static final Type<SignalBoxActionPayload> TYPE = new Type<>(ReikoTrains.id("signal_box_action"));
    public static final StreamCodec<FriendlyByteBuf, SignalBoxActionPayload> STREAM_CODEC = StreamCodec.of(SignalBoxActionPayload::write,
            SignalBoxActionPayload::read);

    public static final int SIGNAL = 0;
    public static final int SWITCH = 1;
    /** Set a route from the signal {@code target} to the signal {@code target2}; or, if one's set from it, cancel it. */
    public static final int ROUTE = 2;
    /** Turn the turntable whose pivot is {@code target} to its next track, clockwise. */
    public static final int TURNTABLE = 3;

    public SignalBoxActionPayload(BlockPos box, int action, BlockPos target) {
        this(box, action, target, target);
    }

    private static void write(FriendlyByteBuf buf, SignalBoxActionPayload payload) {
        buf.writeBlockPos(payload.box());
        buf.writeVarInt(payload.action());
        buf.writeBlockPos(payload.target());
        buf.writeBlockPos(payload.target2());
    }

    private static SignalBoxActionPayload read(FriendlyByteBuf buf) {
        return new SignalBoxActionPayload(buf.readBlockPos(), buf.readVarInt(), buf.readBlockPos(), buf.readBlockPos());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
