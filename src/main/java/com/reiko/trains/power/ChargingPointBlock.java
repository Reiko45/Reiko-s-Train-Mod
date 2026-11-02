package com.reiko.trains.power;

import com.reiko.trains.freight.WagonLoaderBlock;
import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.signal.TrackSide;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A charging point, for electric locomotives. Placed beside the track (up to three blocks away; it ties itself to the
 * nearest track block and its cable faces it), it takes power from BuildCraft engines and kinesis pipes (or other
 * mods' cables) on any side, stores it, and charges the batteries of electrics in a train standing beside it. The four
 * bars on its back (facing away from the track) show how full its store is, and the lamp on top lights while it's
 * charging. Click it to see how much it holds.
 */
public class ChargingPointBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final com.mojang.serialization.MapCodec<ChargingPointBlock> CODEC = simpleCodec(ChargingPointBlock::new);
    /** How many of the bars on its front are lit: how full its store is. */
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 0, 4);
    /** The lamp on top: it's charging a train. */
    public static final BooleanProperty CHARGING = BooleanProperty.create("charging");
    private static final VoxelShape SHAPE = Block.box(2, 0, 3, 14, 25, 13);
    private static final VoxelShape SHAPE_X = Block.box(3, 0, 2, 13, 25, 14);

    public ChargingPointBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LEVEL, 0).setValue(CHARGING, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LEVEL, CHARGING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos track = TrackSide.nearestTrack(level, context.getClickedPos());
        Player player = context.getPlayer();
        if (track == null) {
            if (player != null && level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.reikotrains.trackside.no_track").withStyle(ChatFormatting.RED), true);
            }
            return null;
        }
        return defaultBlockState().setValue(FACING, WagonLoaderBlock.facing(context.getClickedPos(), track, player));
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof ChargingPointBlockEntity charger) {
            BlockPos track = TrackSide.nearestTrack(level, pos);
            if (track != null) {
                charger.attach(track);
            }
        }
    }

    /** Clicking it says how much power it has stored. */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof ChargingPointBlockEntity charger) {
            player.displayClientMessage(Component.translatable("message.reikotrains.charger.stored", String.format(java.util.Locale.ROOT, "%,d",
                    charger.storedMj()), String.format(java.util.Locale.ROOT, "%,d", ChargingPointBlockEntity.capacityMj())), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ChargingPointBlockEntity charger ? charger.comparatorSignal() : 0;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE : SHAPE_X;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChargingPointBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.CHARGING_POINT.get()) {
            return null;
        }
        return (l, p, s, be) -> ChargingPointBlockEntity.serverTick(l, p, s, (ChargingPointBlockEntity) be);
    }
}
