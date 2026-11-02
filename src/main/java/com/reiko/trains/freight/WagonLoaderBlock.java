package com.reiko.trains.freight;

import com.reiko.trains.menu.WagonLoaderMenu;
import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.signal.TrackSide;
import com.reiko.trains.track.TrackEdge;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A wagon loader. Placed beside the track (up to three blocks away; it ties itself to the nearest track block and
 * faces it), it fills the freight wagons of a train standing there from the storage next to it, or empties them into
 * it. Click it to open its screen (its filter, and loading or unloading); sneak-click to switch between loading and
 * unloading. Redstone pauses it; a comparator reads how full the train is. Its lamp is lit while it's working.
 */
public class WagonLoaderBlock extends HorizontalDirectionalBlock implements EntityBlock {
    public static final com.mojang.serialization.MapCodec<WagonLoaderBlock> CODEC = simpleCodec(WagonLoaderBlock::new);
    public static final EnumProperty<LoaderMode> MODE = EnumProperty.create("mode", LoaderMode.class);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public WagonLoaderBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(MODE, LoaderMode.LOAD).setValue(ACTIVE, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, MODE, ACTIVE);
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
        return defaultBlockState().setValue(FACING, facing(context.getClickedPos(), track, player));
    }

    /** Its chute faces the track. */
    public static Direction facing(BlockPos pos, BlockPos track, @Nullable Player player) {
        Vec3 toTrack = TrackEdge.node(track).subtract(Vec3.atCenterOf(pos));
        if (toTrack.horizontalDistanceSqr() < 0.01) {
            return player != null ? player.getDirection().getOpposite() : Direction.NORTH;
        }
        return TrackSide.nearestDirection(toTrack);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WagonLoaderBlockEntity loader) {
            BlockPos track = TrackSide.nearestTrack(level, pos);
            if (track != null) {
                loader.attach(track);
            }
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof WagonLoaderBlockEntity loader)) {
            return InteractionResult.PASS;
        }
        if (!player.isSecondaryUseActive()) {
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new WagonLoaderMenu(id, inventory, loader),
                    Component.translatable("block.reikotrains.wagon_loader")), pos);
            return InteractionResult.CONSUME;
        }
        LoaderMode mode = loader.toggleMode();
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5F, mode == LoaderMode.LOAD ? 1.2F : 0.9F);
        player.displayClientMessage(Component.translatable("message.reikotrains.loader." + mode.getSerializedName()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof WagonLoaderBlockEntity loader ? loader.comparatorSignal() : 0;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WagonLoaderBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModBlockEntities.WAGON_LOADER.get()) {
            return null;
        }
        return (l, p, s, be) -> WagonLoaderBlockEntity.serverTick(l, p, s, (WagonLoaderBlockEntity) be);
    }
}
