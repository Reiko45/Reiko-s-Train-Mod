package com.reiko.trains.turntable;

import com.reiko.trains.registry.ModBlocks;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A turntable, in kit form. Used on flat ground, it digs its pit, a circle 15 blocks across (11 for the small one),
 * centred on the block clicked, and sets the turntable in it, its bridge pointing the way the player looks (or at track already laid up to
 * the pit's edge, nearest that way). The ground must be solid all over the circle, with nothing standing on it
 * (plants are cleared away).
 */
public class TurntableItem extends Item {
    private final java.util.function.Supplier<? extends TurntableBlock> block;

    public TurntableItem(Properties properties, java.util.function.Supplier<? extends TurntableBlock> block) {
        super(properties);
        this.block = block;
    }

    public TurntableBlock block() {
        return block.get();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pivot = context.getClickedPos();
        if (context.getClickedFace() != Direction.UP || !fits(level, pivot, block())) {
            if (player != null && level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.reikotrains.turntable.no_room").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResult.FAIL;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        int way = player == null ? 0 : wayFor(player.getYRot());
        if (!place(level, pivot, way, block())) {
            return InteractionResult.FAIL;
        }
        if (level.getBlockEntity(pivot) instanceof TurntableBlockEntity table) {
            // Lined up with the track already laid up to the pit, if there is any, nearest the way the player looks.
            for (int m = 0; m < 8; m++) {
                int d = Math.floorMod(way + (m % 2 == 0 ? m / 2 : -(m + 1) / 2), 8);
                if (table.hasExit(d) || table.hasExit((d + 4) & 7)) {
                    table.setWay(d);
                    break;
                }
            }
        }
        level.playSound(null, pivot, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
        if (player == null || !player.getAbilities().instabuild) {
            context.getItemInHand().shrink(1);
        }
        return InteractionResult.CONSUME;
    }

    /** Which of the eight ways is nearest a yaw. */
    public static int wayFor(float yaw) {
        double r = yaw * Mth.DEG_TO_RAD;
        double x = -Math.sin(r);
        double z = Math.cos(r);
        return Math.floorMod((int) Math.round(Math.atan2(z, x) / (Math.PI / 4)), 8);
    }

    /** Whether there's room for a big turntable with its pivot here. */
    public static boolean fits(Level level, BlockPos pivot) {
        return fits(level, pivot, ModBlocks.TURNTABLE.get());
    }

    /** Whether there's room for a turntable with its pivot here: solid ground all over the circle, nothing on it but plants. */
    public static boolean fits(Level level, BlockPos pivot, TurntableBlock block) {
        int span = block.span();
        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                if (!block.inPit(dx, dz)) {
                    continue;
                }
                BlockPos ground = pivot.offset(dx, 0, dz);
                BlockState state = level.getBlockState(ground);
                if (!level.isLoaded(ground) || state.hasBlockEntity() || state.getDestroySpeed(level, ground) < 0
                        || !state.isCollisionShapeFullBlock(level, ground)) {
                    return false;
                }
                BlockState above = level.getBlockState(ground.above());
                if (!above.isAir() && !above.canBeReplaced()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Digs the pit and sets a big turntable in it (if there's room), its bridge pointing {@code way}. */
    public static boolean place(Level level, BlockPos pivot, int way) {
        return place(level, pivot, way, ModBlocks.TURNTABLE.get());
    }

    /** Digs the pit and sets the turntable in it (if there's room), its bridge pointing {@code way}. */
    public static boolean place(Level level, BlockPos pivot, int way, TurntableBlock block) {
        if (!fits(level, pivot, block)) {
            return false;
        }
        int span = block.span();
        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                if (block.inPit(dx, dz)) {
                    BlockPos ground = pivot.offset(dx, 0, dz);
                    if (!level.getBlockState(ground.above()).isAir()) {
                        level.setBlock(ground.above(), Blocks.AIR.defaultBlockState(), 3);
                    }
                    level.setBlock(ground, block.cell(dx, dz), 3);
                }
            }
        }
        if (level.getBlockEntity(pivot) instanceof TurntableBlockEntity table) {
            table.setWay(way);
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
    }
}
