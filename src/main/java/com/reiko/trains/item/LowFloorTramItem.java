package com.reiko.trains.item;

import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.train.LowFloorTram;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** The low-floor tram: click a track block to put all three sections on it, the middle one there. */
public class LowFloorTramItem extends Item {
    public LowFloorTramItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (TrackGraph.axis(level, pos) == null) {
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(TrainCarItem.Problem.NOT_TRACK.message().withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel) {
            Vec3 look = player != null ? player.getLookAngle() : new Vec3(1, 0, 0);
            TrainCarItem.Problem problem = LowFloorTram.place(serverLevel, pos, look);
            if (problem != null) {
                if (player != null) {
                    player.displayClientMessage(problem.message().withStyle(ChatFormatting.RED), true);
                }
                return InteractionResult.FAIL;
            }
            serverLevel.gameEvent(GameEvent.ENTITY_PLACE, pos, GameEvent.Context.of(player));
            context.getItemInHand().shrink(1);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
