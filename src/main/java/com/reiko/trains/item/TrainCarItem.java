package com.reiko.trains.item;

import com.reiko.trains.train.Trains;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/** Puts a train car on the rails clicked, facing along them the way the player is looking. */
public class TrainCarItem extends Item {
    private final Supplier<? extends EntityType<? extends AbstractMinecart>> type;

    public TrainCarItem(Supplier<? extends EntityType<? extends AbstractMinecart>> type, Properties properties) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.RAILS)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        if (level instanceof ServerLevel serverLevel) {
            RailShape shape = state.getBlock() instanceof BaseRailBlock rail
                    ? rail.getRailDirection(state, level, pos, null) : RailShape.NORTH_SOUTH;
            Player player = context.getPlayer();
            Vec3 look = player != null ? player.getLookAngle() : new Vec3(0, 0, 1);
            AbstractMinecart car = place(serverLevel, type.get(), pos, shape, look, stack, player);
            if (car == null) {
                return InteractionResult.FAIL;
            }
            serverLevel.gameEvent(GameEvent.ENTITY_PLACE, pos, GameEvent.Context.of(player, serverLevel.getBlockState(pos.below())));
        }
        stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Creates a car on the rail at {@code pos}, facing the way along it closest to {@code look}. */
    public static <T extends AbstractMinecart> T place(ServerLevel level, EntityType<T> type, BlockPos pos, RailShape shape, Vec3 look,
                                                      ItemStack stack, Player player) {
        T car = type.create(level);
        if (car == null) {
            return null;
        }
        Vec3 along = along(shape);
        if (along.x * look.x + along.z * look.z < 0) {
            along = along.reverse();
        }
        float yaw = Trains.yawFacing(along);
        car.moveTo(pos.getX() + 0.5, pos.getY() + 0.0625 + (shape.isAscending() ? 0.5 : 0.0), pos.getZ() + 0.5, yaw, 0.0F);
        EntityType.<AbstractMinecart>createDefaultStackConfig(level, stack, player).accept(car);
        level.addFreshEntity(car);
        return car;
    }

    /** The direction a rail runs in, flat (one of the two). */
    public static Vec3 along(RailShape shape) {
        double s = Math.sqrt(0.5);
        return switch (shape) {
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> new Vec3(1, 0, 0);
            case SOUTH_EAST -> new Vec3(s, 0, -s);
            case SOUTH_WEST -> new Vec3(-s, 0, -s);
            case NORTH_WEST -> new Vec3(-s, 0, s);
            case NORTH_EAST -> new Vec3(s, 0, s);
            default -> new Vec3(0, 0, 1);
        };
    }
}
