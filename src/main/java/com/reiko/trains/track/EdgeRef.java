package com.reiko.trains.track;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * A stretch of track from one track block to the next, in one direction: it leaves {@code from} by its side
 * {@code fromSide} and arrives at {@code to} on its side {@code toSide}. The axes are kept too, and whether each end
 * is street track (its rails at the top of the block, not a quarter of the way up), so the shape can be worked out
 * without looking at the world.
 */
public record EdgeRef(BlockPos from, TrackAxis fromAxis, int fromSide, BlockPos to, TrackAxis toAxis, int toSide, boolean fromStreet,
                      boolean toStreet) {
    public EdgeRef {
        from = from.immutable();
        to = to.immutable();
    }

    public EdgeRef reversed() {
        return new EdgeRef(to, toAxis, toSide, from, fromAxis, fromSide, toStreet, fromStreet);
    }

    /** Whether it's street track all the way: both ends are. */
    public boolean street() {
        return fromStreet && toStreet;
    }

    public TrackEdge geometry() {
        return TrackGraph.geometry(this);
    }

    /** Whether the two refer to the same stretch of track, either way round. */
    public boolean sameTrack(EdgeRef other) {
        return equals(other) || equals(other.reversed());
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("From", from.asLong());
        tag.putByte("FromAxis", (byte) fromAxis.ordinal());
        tag.putByte("FromSide", (byte) fromSide);
        tag.putLong("To", to.asLong());
        tag.putByte("ToAxis", (byte) toAxis.ordinal());
        tag.putByte("ToSide", (byte) toSide);
        if (fromStreet) {
            tag.putBoolean("FromStreet", true);
        }
        if (toStreet) {
            tag.putBoolean("ToStreet", true);
        }
        return tag;
    }

    public static EdgeRef load(CompoundTag tag) {
        TrackAxis[] axes = TrackAxis.values();
        return new EdgeRef(BlockPos.of(tag.getLong("From")), axes[tag.getByte("FromAxis") & 3], tag.getByte("FromSide"),
                BlockPos.of(tag.getLong("To")), axes[tag.getByte("ToAxis") & 3], tag.getByte("ToSide"), tag.getBoolean("FromStreet"),
                tag.getBoolean("ToStreet"));
    }
}
