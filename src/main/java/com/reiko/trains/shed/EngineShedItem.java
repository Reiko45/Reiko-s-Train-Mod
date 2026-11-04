package com.reiko.trains.shed;

import com.reiko.trains.registry.ModBlocks;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackBlock;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Engine shed doors, in kit form. Used on a straight track block (east-west or north-south), it puts up the frame
 * over it, the doors facing the player: the player stands outside the shed, and the shed's inside is beyond the doors.
 * There must be room: five blocks across the track and five up, clear (apart from the track itself). Beside another
 * set of doors facing the same way, on a track four blocks over, they share its post: the two make one shed of two
 * roads (as do doors post to post, on tracks five apart).
 */
public class EngineShedItem extends Item {
    public EngineShedItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos track = context.getClickedPos();
        BlockState state = level.getBlockState(track);
        Direction facing = null;
        if (state.getBlock() instanceof TrackBlock && player != null) {
            TrackAxis axis = state.getValue(TrackBlock.AXIS);
            double dx = player.getX() - (track.getX() + 0.5);
            double dz = player.getZ() - (track.getZ() + 0.5);
            if (axis == TrackAxis.X) {
                facing = dx >= 0 ? Direction.EAST : Direction.WEST;
            } else if (axis == TrackAxis.Z) {
                facing = dz >= 0 ? Direction.SOUTH : Direction.NORTH;
            }
        }
        if (facing == null || !fits(level, track, facing)) {
            if (player != null && level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.reikotrains.shed.no_room").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        place(level, track, facing);
        level.playSound(null, track.above(2), SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
        if (!player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /** Whether there's room for the frame over this track block, facing this way. */
    public static boolean fits(Level level, BlockPos track, Direction facing) {
        for (int up = 0; up <= 4; up++) {
            for (int across = -2; across <= 2; across++) {
                if (across == 0 && up == 0) {
                    continue;
                }
                BlockPos at = EngineShedBlock.at(track, facing, across, up);
                if (!level.isInWorldBounds(at)) {
                    return false;
                }
                BlockState there = level.getBlockState(at);
                if (!there.canBeReplaced() && !sharedPost(there, facing, across, up)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Puts up the frame and its doors over a track block (if there's room), facing {@code facing}, shut. */
    public static boolean place(Level level, BlockPos track, Direction facing) {
        if (!fits(level, track, facing)) {
            return false;
        }
        EngineShedBlock block = ModBlocks.ENGINE_SHED.get();
        for (int up = 0; up <= 4; up++) {
            for (int across = -2; across <= 2; across++) {
                if (across == 0 && up == 0) {
                    continue;
                }
                BlockPos at = EngineShedBlock.at(track, facing, across, up);
                if (sharedPost(level.getBlockState(at), facing, across, up)) {
                    continue; // the post of the doors beside: they share it
                }
                level.setBlock(at, block.defaultBlockState().setValue(EngineShedBlock.FACING, facing)
                        .setValue(EngineShedBlock.CELL, EngineShedBlock.cell(across, up)), 3);
            }
        }
        return true;
    }

    /** Whether a block where these doors' post would go ({@code across} ±2) is the post of doors beside them, facing the same way, that it can share. */
    public static boolean sharedPost(BlockState there, Direction facing, int across, int up) {
        return Math.abs(across) == 2 && there.getBlock() instanceof EngineShedBlock && there.getValue(EngineShedBlock.FACING) == facing
                && EngineShedBlock.across(there) == -across && EngineShedBlock.up(there) == up;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.reikotrains.engine_shed.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.reikotrains.engine_shed.tooltip2").withStyle(ChatFormatting.GRAY));
    }
}
