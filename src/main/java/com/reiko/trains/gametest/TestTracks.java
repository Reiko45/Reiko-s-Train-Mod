package com.reiko.trains.gametest;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;

/** Lays test track: used by the game tests and the dev showcase. */
public final class TestTracks {
    private TestTracks() {
    }

    /**
     * A square loop of rails on a stone bed, corners {@code from} and {@code to} (absolute, same y). The rails are
     * set in two passes, without block updates, so they come out exactly as drawn instead of reconnecting as they go.
     */
    public static List<BlockPos> loop(ServerLevel level, BlockPos from, BlockPos to) {
        int x0 = Math.min(from.getX(), to.getX());
        int x1 = Math.max(from.getX(), to.getX());
        int z0 = Math.min(from.getZ(), to.getZ());
        int z1 = Math.max(from.getZ(), to.getZ());
        int y = from.getY();
        List<BlockPos> rails = new ArrayList<>();
        for (int x = x0; x <= x1; x++) {
            rails.add(new BlockPos(x, y, z0));
            rails.add(new BlockPos(x, y, z1));
        }
        for (int z = z0 + 1; z < z1; z++) {
            rails.add(new BlockPos(x0, y, z));
            rails.add(new BlockPos(x1, y, z));
        }
        for (BlockPos pos : rails) {
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
            level.setBlock(pos, Blocks.RAIL.defaultBlockState(), 2);
        }
        for (BlockPos pos : rails) {
            int x = pos.getX();
            int z = pos.getZ();
            RailShape shape;
            if (x == x0 && z == z0) {
                shape = RailShape.SOUTH_EAST;
            } else if (x == x1 && z == z0) {
                shape = RailShape.SOUTH_WEST;
            } else if (x == x1 && z == z1) {
                shape = RailShape.NORTH_WEST;
            } else if (x == x0 && z == z1) {
                shape = RailShape.NORTH_EAST;
            } else {
                shape = z == z0 || z == z1 ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
            }
            level.setBlock(pos, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape), 2);
        }
        return rails;
    }
}
