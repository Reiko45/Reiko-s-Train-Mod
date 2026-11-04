package com.reiko.trains.item;

import com.reiko.trains.registry.ModDataComponents;
import com.reiko.trains.signal.StationBlockEntity;
import com.reiko.trains.train.Locomotive;
import com.reiko.trains.train.Timetable;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * A timetable. Click stations to add them as stops, in order; click the station just added again to say what the
 * train does with wagons there (leave some, leave them all, or pick up what's waiting). Then click a train to set it
 * running them on its own, round and round. Sneak-click a train to stop it running automatically, and sneak-click the
 * air to clear the stops.
 */
public class TimetableItem extends Item {
    public TimetableItem(Properties properties) {
        super(properties);
    }

    public static List<Timetable.Stop> stops(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.TIMETABLE.get(), List.of());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof StationBlockEntity station) || player == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack stack = context.getItemInHand();
            List<Timetable.Stop> stops = new ArrayList<>(stops(stack));
            if (!station.isAttached()) {
                player.displayClientMessage(Component.translatable("message.reikotrains.timetable.no_track").withStyle(ChatFormatting.RED), true);
            } else if (!stops.isEmpty() && stops.getLast().station().equals(context.getClickedPos())) {
                // Clicking the last stop again: what the train does with wagons there, going round.
                Timetable.Stop last = stops.getLast();
                Timetable.Stop changed = last.withWagons(last.nextWagons());
                stops.set(stops.size() - 1, changed);
                stack.set(ModDataComponents.TIMETABLE.get(), stops);
                player.displayClientMessage(Component.translatable("message.reikotrains.timetable.wagons", station.name(), stops.size(),
                        changed.describeWagons()), true);
            } else if (stops.size() >= Timetable.MAX_STOPS) {
                player.displayClientMessage(Component.translatable("message.reikotrains.timetable.full", Timetable.MAX_STOPS)
                        .withStyle(ChatFormatting.RED), true);
            } else {
                stops.add(new Timetable.Stop(context.getClickedPos().immutable(), station.node(), station.name()));
                stack.set(ModDataComponents.TIMETABLE.get(), stops);
                player.displayClientMessage(Component.translatable("message.reikotrains.timetable.added", station.name(), stops.size()), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Clicking a train: sets its locomotive running the timetable, or (sneaking) stops it. */
    public static InteractionResult useOnCar(ItemStack stack, Player player, TrainCarEntity car) {
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        // The locomotive clicked, or else the train's first: it runs the timetable, and any others work with it.
        List<Locomotive> locos = new ArrayList<>();
        for (TrainCarEntity c : Trains.train(car)) {
            if (c instanceof Locomotive l) {
                locos.add(l);
            }
        }
        if (locos.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.reikotrains.timetable.no_locomotive").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        Locomotive loco = car instanceof Locomotive clicked ? clicked : locos.getFirst();
        if (player.isSecondaryUseActive()) {
            for (Locomotive l : locos) {
                l.autopilot().stop();
            }
            player.displayClientMessage(Component.translatable("message.reikotrains.timetable.stopped"), true);
            return InteractionResult.CONSUME;
        }
        for (Locomotive l : locos) {
            if (l != loco) {
                l.autopilot().stop();
            }
        }
        List<Timetable.Stop> stops = stops(stack);
        if (stops.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.reikotrains.timetable.empty").withStyle(ChatFormatting.RED), true);
            return InteractionResult.CONSUME;
        }
        loco.autopilot().start(stops);
        com.reiko.trains.advancement.TrainAdvancements.award(player, "timetable");
        player.displayClientMessage(Component.translatable("message.reikotrains.timetable.started", stops.size(), stops.getFirst().name()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && stack.has(ModDataComponents.TIMETABLE.get())) {
            if (!level.isClientSide) {
                stack.remove(ModDataComponents.TIMETABLE.get());
                player.displayClientMessage(Component.translatable("message.reikotrains.timetable.cleared"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        List<Timetable.Stop> stops = stops(stack);
        if (stops.isEmpty()) {
            tooltip.add(Component.translatable("item.reikotrains.timetable.tooltip").withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("item.reikotrains.timetable.tooltip2").withStyle(ChatFormatting.GRAY));
            return;
        }
        for (int i = 0; i < stops.size(); i++) {
            Timetable.Stop stop = stops.get(i);
            Component line = Component.literal((i + 1) + ". " + stop.name());
            if (stop.wagons() != Timetable.Stop.NONE) {
                line = Component.translatable("item.reikotrains.timetable.stop_with_wagons", line, stop.describeWagons());
            }
            tooltip.add(line.copy().withStyle(ChatFormatting.GRAY));
        }
        tooltip.add(Component.translatable("item.reikotrains.timetable.tooltip3").withStyle(ChatFormatting.DARK_GRAY));
    }
}
