package com.reiko.trains.track;

import com.reiko.trains.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * Train track. Placed on the ground it's a block of straight track facing the way you look (blocks in a line join
 * up). Click a track block to start a curve, then click where it should go: on another track block to join it, or
 * on the ground for a new end. Sneak when clicking the end to have the track there face the way you're looking (for
 * S-bends). Sneak-click the air to start over.
 */
public class TrackItem extends BlockItem {
    public TrackItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        BlockPos clicked = context.getClickedPos();
        BlockPos start = stack.get(ModDataComponents.TRACK_START.get());
        if (start == null) {
            if (TrackGraph.axis(level, clicked) != null && player != null) {
                if (!level.isClientSide) {
                    stack.set(ModDataComponents.TRACK_START.get(), clicked.immutable());
                    player.displayClientMessage(Component.translatable("message.reikotrains.track.picked"), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return super.useOn(context);
        }
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (clicked.equals(start)) {
            if (!level.isClientSide) {
                stack.remove(ModDataComponents.TRACK_START.get());
                player.displayClientMessage(Component.translatable("message.reikotrains.track.cleared"), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockPos end = TrackBuilder.endFor(level, clicked, context.getClickedFace());
        TrackBuilder.Plan plan = TrackBuilder.plan(level, start, end, player.getLookAngle(), player.isSecondaryUseActive());
        if (!plan.ok()) {
            player.displayClientMessage(plan.problem().copy().withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild && TrackBuilder.carried(player) < plan.cost()) {
            player.displayClientMessage(Component.translatable("message.reikotrains.track.need", plan.cost()).withStyle(ChatFormatting.RED), true);
            return InteractionResult.FAIL;
        }
        stack.remove(ModDataComponents.TRACK_START.get());
        if (TrackBuilder.build(level, plan, player)) {
            player.displayClientMessage(Component.translatable("message.reikotrains.track.built",
                    String.format("%.0f", plan.shape().length()), plan.cost()), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && stack.has(ModDataComponents.TRACK_START.get())) {
            if (!level.isClientSide) {
                stack.remove(ModDataComponents.TRACK_START.get());
                player.displayClientMessage(Component.translatable("message.reikotrains.track.cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return super.use(level, player, hand);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.TRACK_START.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.reikotrains.track.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.reikotrains.track.tooltip2").withStyle(ChatFormatting.GRAY));
    }
}
