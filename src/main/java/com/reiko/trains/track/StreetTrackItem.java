package com.reiko.trains.track;

import net.minecraft.world.level.block.Block;

/**
 * Street track: placed like a block (into the road, in place of a road block), and drawn into curves like train
 * track, the new end set into the road block clicked. Curves between street track blocks are paid for in street track.
 */
public class StreetTrackItem extends TrackItem {
    public StreetTrackItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public boolean street() {
        return true;
    }
}
