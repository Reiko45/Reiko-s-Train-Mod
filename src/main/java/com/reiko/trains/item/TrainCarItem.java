package com.reiko.trains.item;

import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.track.TrackPos;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** Puts a train car on the track clicked, centred on that block and facing along the track the way you look. */
public class TrainCarItem extends Item {
    private final Supplier<? extends EntityType<? extends TrainCarEntity>> type;

    public TrainCarItem(Supplier<? extends EntityType<? extends TrainCarEntity>> type, Properties properties) {
        super(properties);
        this.type = type;
    }

    /** The kind of car it puts on the track. */
    public EntityType<? extends TrainCarEntity> carType() {
        return type.get();
    }

    /** Why a car can't go somewhere, or null if it can (and was put there). */
    public enum Problem {
        NOT_TRACK("not_track"),
        NO_ROOM("no_room"),
        BLOCKED("blocked");

        private final String key;

        Problem(String key) {
            this.key = key;
        }

        public net.minecraft.network.chat.MutableComponent message() {
            return Component.translatable("message.reikotrains.place." + key);
        }
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (TrackGraph.axis(level, pos) == null) {
            if (player != null && !level.isClientSide) {
                player.displayClientMessage(Problem.NOT_TRACK.message().withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        if (level instanceof ServerLevel serverLevel) {
            ItemStack stack = context.getItemInHand();
            Vec3 look = player != null ? player.getLookAngle() : new Vec3(1, 0, 0);
            TrainCarEntity car = type.get().create(serverLevel);
            Problem problem = car == null ? Problem.NOT_TRACK : place(serverLevel, car, pos, look);
            if (problem != null) {
                if (player != null) {
                    player.displayClientMessage(problem.message().withStyle(ChatFormatting.RED), true);
                }
                return InteractionResult.FAIL;
            }
            EntityType.<TrainCarEntity>createDefaultStackConfig(serverLevel, stack, player).accept(car);
            serverLevel.addFreshEntity(car);
            serverLevel.gameEvent(GameEvent.ENTITY_PLACE, pos, GameEvent.Context.of(player));
            stack.shrink(1);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * Sets a car on the track centred on the track block at {@code pos}, facing the way along it nearest
     * {@code look}, without adding it to the world. Returns why it can't go there, or null if it's in place.
     */
    @Nullable
    public static Problem place(Level level, TrainCarEntity car, BlockPos pos, Vec3 look) {
        TrackPos centre = TrackPos.at(level, pos, look);
        if (centre == null) {
            return Problem.NO_ROOM;
        }
        TrackPos front = centre.copy();
        TrackPos rear = centre.copy();
        double offset = car.bogieOffset();
        if (front.walk(level, offset, TrackPos.STRAIGHTEST) < offset - 1.0E-6 || rear.walk(level, -offset, TrackPos.STRAIGHTEST) < offset - 1.0E-6) {
            return Problem.NO_ROOM;
        }
        car.setBogies(front, rear);
        return Trains.clear(level, car) ? null : Problem.BLOCKED;
    }

    /** Cars that need explaining have a line or two in the lang file ({@code item.reikotrains.<car>.tooltip}). */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        for (String suffix : new String[] {".tooltip", ".tooltip2"}) {
            String key = getDescriptionId() + suffix;
            if (Language.getInstance().has(key)) {
                tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
            }
        }
    }
}
