package com.reiko.trains.track;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;

/**
 * A curve (or long straight, or ramp) drawn with the track item from one track block to another, as kept by each
 * end: the other block, which side of this block it leaves by, and which side of the other block it arrives at.
 */
public record TrackConnection(BlockPos other, int mySide, int otherSide) {
    public TrackConnection {
        other = other.immutable();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Other", other.asLong());
        tag.putByte("Side", (byte) mySide);
        tag.putByte("OtherSide", (byte) otherSide);
        return tag;
    }

    public static TrackConnection load(CompoundTag tag) {
        return new TrackConnection(BlockPos.of(tag.getLong("Other")), tag.getByte("Side"), tag.getByte("OtherSide"));
    }

    /** The end that draws the curve and drops its track when it's broken: the lower block position. */
    public boolean ownedBy(BlockPos self) {
        return self.asLong() < other.asLong();
    }
}
