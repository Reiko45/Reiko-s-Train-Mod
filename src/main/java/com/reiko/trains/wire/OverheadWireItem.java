package com.reiko.trains.wire;

import com.reiko.trains.registry.ModDataComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A reel of overhead wire: click a mast, then the next mast along (up to 32 blocks away) to string a span between them;
 * it takes one reel a span. It stays ready to go on from the second mast, so clicking mast after mast strings a whole
 * line. Sneak and click the air to stop; sneak-click a mast to take its spans down (and get the wire back).
 */
public class OverheadWireItem extends Item {
    public OverheadWireItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (!(level.getBlockEntity(pos) instanceof CatenaryMastBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel server) || player == null) {
            return InteractionResult.SUCCESS;
        }
        OverheadLines lines = OverheadLines.get(server);
        if (player.isSecondaryUseActive()) {
            List<OverheadLines.Span> spans = lines.spansAt(pos);
            for (OverheadLines.Span span : spans) {
                lines.disconnect(span.a(), span.b());
                CatenaryMastBlockEntity.refresh(level, span.other(pos));
            }
            CatenaryMastBlockEntity.refresh(level, pos);
            if (!spans.isEmpty() && !player.getAbilities().instabuild) {
                ItemStack back = new ItemStack(this, spans.size());
                if (!player.addItem(back)) {
                    player.drop(back, false);
                }
            }
            stack.remove(ModDataComponents.WIRE_FROM.get());
            player.displayClientMessage(Component.translatable("message.reikotrains.wire.taken_down", spans.size()), true);
            return InteractionResult.CONSUME;
        }
        BlockPos from = stack.get(ModDataComponents.WIRE_FROM.get());
        if (from == null || !lines.hasMast(from)) {
            stack.set(ModDataComponents.WIRE_FROM.get(), pos.immutable());
            player.displayClientMessage(Component.translatable("message.reikotrains.wire.start", (int) OverheadLines.MAX_SPAN), true);
            return InteractionResult.CONSUME;
        }
        if (from.equals(pos)) {
            stack.remove(ModDataComponents.WIRE_FROM.get());
            player.displayClientMessage(Component.translatable("message.reikotrains.wire.cancelled"), true);
            return InteractionResult.CONSUME;
        }
        String problem = lines.cantConnect(from, pos);
        if (problem != null) {
            player.displayClientMessage(Component.translatable("message.reikotrains.wire." + problem, (int) OverheadLines.MAX_SPAN)
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        lines.connect(from, pos);
        CatenaryMastBlockEntity.refresh(level, from);
        CatenaryMastBlockEntity.refresh(level, pos);
        Vec3 a = lines.wire(from);
        Vec3 b = lines.wire(pos);
        int length = (int) Math.round(a.distanceTo(b));
        // Ready to go on from here (unless that was the last of the wire).
        boolean last = !player.getAbilities().instabuild && stack.getCount() <= 1;
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        if (!last) {
            stack.set(ModDataComponents.WIRE_FROM.get(), pos.immutable());
        }
        level.playSound(null, pos, SoundEvents.CHAIN_PLACE, SoundSource.BLOCKS, 0.8F, 1.2F);
        player.displayClientMessage(Component.translatable("message.reikotrains.wire.strung", length), true);
        return InteractionResult.CONSUME;
    }

    /** Sneak and click the air: stop stringing. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && stack.has(ModDataComponents.WIRE_FROM.get())) {
            stack.remove(ModDataComponents.WIRE_FROM.get());
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.reikotrains.wire.cancelled"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    /** It glints while it's being strung from a mast. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.WIRE_FROM.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.reikotrains.overhead_wire.tooltip", (int) OverheadLines.MAX_SPAN).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.reikotrains.overhead_wire.tooltip2").withStyle(ChatFormatting.GRAY));
    }
}
