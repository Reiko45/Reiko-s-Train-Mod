package com.reiko.trains.item;

import com.reiko.trains.registry.ModDataComponents;
import com.reiko.trains.registry.ModSounds;
import com.reiko.trains.train.TrainCar;
import com.reiko.trains.train.Trains;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Couples train cars together: click one car, then the car to couple it to. Sneak-click a car to uncouple it from
 * the cars either side of it; sneak-click the air to forget the car picked.
 */
public class CouplerItem extends Item {
    public CouplerItem(Properties properties) {
        super(properties);
    }

    public static InteractionResult useOnCar(ItemStack stack, Player player, TrainCar car) {
        if (!(player.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        AbstractMinecart cart = car.cart();
        if (player.isSecondaryUseActive()) {
            stack.remove(ModDataComponents.COUPLING_FROM.get());
            int undone = Trains.uncouple(car);
            if (undone > 0) {
                level.playSound(null, cart.getX(), cart.getY(), cart.getZ(), ModSounds.COUPLE.get(), SoundSource.NEUTRAL, 0.8F, 0.75F);
            }
            player.displayClientMessage(Component.translatable(undone > 0 ? "message.reikotrains.uncoupled" : "message.reikotrains.not_coupled",
                    cart.getDisplayName()), true);
            return InteractionResult.CONSUME;
        }
        UUID firstId = stack.get(ModDataComponents.COUPLING_FROM.get());
        if (firstId == null || firstId.equals(cart.getUUID())) {
            if (firstId != null) {
                stack.remove(ModDataComponents.COUPLING_FROM.get());
                player.displayClientMessage(Component.translatable("message.reikotrains.couple.cancelled"), true);
            } else {
                stack.set(ModDataComponents.COUPLING_FROM.get(), cart.getUUID());
                player.displayClientMessage(Component.translatable("message.reikotrains.couple.picked", cart.getDisplayName()), true);
            }
            return InteractionResult.CONSUME;
        }
        stack.remove(ModDataComponents.COUPLING_FROM.get());
        if (!(level.getEntity(firstId) instanceof TrainCar first) || first.cart().isRemoved()) {
            player.displayClientMessage(Component.translatable("message.reikotrains.couple.gone"), true);
            return InteractionResult.CONSUME;
        }
        Trains.CoupleResult result = Trains.couple(first, car);
        if (result == Trains.CoupleResult.COUPLED) {
            level.playSound(null, cart.getX(), cart.getY(), cart.getZ(), ModSounds.COUPLE.get(), SoundSource.NEUTRAL, 1.0F, 1.0F);
            player.displayClientMessage(Component.translatable("message.reikotrains.couple.coupled", first.cart().getDisplayName(),
                    cart.getDisplayName(), Trains.train(car).size()), true);
        } else {
            player.displayClientMessage(result.message(), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isSecondaryUseActive() && stack.has(ModDataComponents.COUPLING_FROM.get())) {
            if (!level.isClientSide) {
                stack.remove(ModDataComponents.COUPLING_FROM.get());
                player.displayClientMessage(Component.translatable("message.reikotrains.couple.cancelled"), true);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return stack.has(ModDataComponents.COUPLING_FROM.get());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.reikotrains.coupler.tooltip").withStyle(net.minecraft.ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.reikotrains.coupler.tooltip2").withStyle(net.minecraft.ChatFormatting.GRAY));
    }
}
