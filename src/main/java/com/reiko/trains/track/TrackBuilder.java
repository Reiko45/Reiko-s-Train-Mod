package com.reiko.trains.track;

import com.reiko.trains.registry.ModBlocks;
import com.reiko.trains.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Drawing track from one block to another, the way the track item does it: works out the curve (or straight, or
 * ramp) from a track block to where the player clicked, checks it's buildable, and builds it. Shared by the item,
 * its preview, the game tests and the dev showcase, so they all agree.
 */
public final class TrackBuilder {
    /** The tightest curve allowed, in blocks: long cars need room to get round. */
    public static final double MIN_RADIUS = 5.0;
    /** The furthest apart two ends can be. */
    public static final double MAX_LENGTH = 48.0;
    /** The steepest a ramp can get anywhere along it (rise over run). */
    public static final double MAX_GRADIENT = 0.5;

    private TrackBuilder() {
    }

    /** A curve worked out but not built yet; {@link #problem} says why it can't be, if it can't. */
    public record Plan(BlockPos start, @Nullable TrackAxis startAxis, int startSide, BlockPos end, @Nullable TrackAxis endAxis, int endSide,
                       boolean newEnd, @Nullable TrackEdge shape, @Nullable Component problem, int cost) {
        public boolean ok() {
            return problem == null;
        }

        public EdgeRef edge() {
            return new EdgeRef(start, startAxis, startSide, end, endAxis, endSide);
        }

        static Plan fail(BlockPos start, BlockPos end, String key, Object... args) {
            return new Plan(start, null, 0, end, null, 0, false, null, Component.translatable("message.reikotrains.track." + key, args), 0);
        }

        static Plan fail(Plan plan, String key, Object... args) {
            return new Plan(plan.start, plan.startAxis, plan.startSide, plan.end, plan.endAxis, plan.endSide, plan.newEnd, plan.shape,
                    Component.translatable("message.reikotrains.track." + key, args), plan.cost);
        }
    }

    /** How many pieces of track a curve takes (and gives back when it's broken). */
    public static int cost(TrackEdge shape) {
        return Math.max(1, (int) Math.round(shape.length()));
    }

    /** Where a click ends a curve: on the track block clicked, or in the space next to the face clicked. */
    public static BlockPos endFor(Level level, BlockPos clicked, Direction face) {
        return TrackGraph.axis(level, clicked) != null ? clicked.immutable() : clicked.relative(face).immutable();
    }

    /**
     * Works out a curve from the track block at {@code start} to {@code end}. If there's a track block at the end, the
     * curve meets it along its line; if not, a new one goes there, facing so the curve is a smooth arc, or (with
     * {@code keepLook}) facing the way the player looks, for S-bends.
     */
    public static Plan plan(Level level, BlockPos start, BlockPos end, Vec3 look, boolean keepLook) {
        TrackAxis startAxis = TrackGraph.axis(level, start);
        if (startAxis == null) {
            return Plan.fail(start, end, "start_gone");
        }
        if (start.equals(end)) {
            return Plan.fail(start, end, "same_block");
        }
        Vec3 chord = TrackEdge.node(end).subtract(TrackEdge.node(start));
        double run = Math.sqrt(chord.x * chord.x + chord.z * chord.z);
        if (run < 1.9) {
            return Plan.fail(start, end, "too_short");
        }
        if (run > MAX_LENGTH) {
            return Plan.fail(start, end, "too_long", (int) MAX_LENGTH);
        }
        Vec3 flat = new Vec3(chord.x / run, 0, chord.z / run);
        int startSide = startAxis.sideToward(flat);
        Vec3 startDir = startSide == 0 ? Vec3.ZERO : startAxis.out(startSide);
        if (startDir.dot(flat) < 0.2) {
            return Plan.fail(start, end, "behind");
        }

        TrackAxis endAxis = TrackGraph.axis(level, end);
        boolean newEnd = endAxis == null;
        Vec3 endDir;
        if (newEnd) {
            BlockState there = level.getBlockState(end);
            if (!there.canBeReplaced() || !ModBlocks.TRACK.get().defaultBlockState().canSurvive(level, end)) {
                return Plan.fail(start, end, "cant_place");
            }
            // A smooth arc leaves the chord at the same angle it met it: the start direction mirrored in the chord.
            Vec3 arc = flat.scale(2 * startDir.dot(flat)).subtract(startDir);
            endDir = TrackAxis.snap(keepLook ? look : arc);
            endAxis = TrackAxis.nearest(endDir);
        } else {
            int side = endAxis.sideToward(flat);
            endDir = side == 0 ? Vec3.ZERO : endAxis.out(side);
        }
        int endSide = -endAxis.sideToward(endDir);
        Plan plan = new Plan(start, startAxis, startSide, end, endAxis, endSide, newEnd, null, null, 0);
        if (endDir.dot(flat) <= 0.05 || startDir.dot(endDir) < -1.0E-3) {
            return Plan.fail(plan, "too_sharp", (int) MIN_RADIUS);
        }
        if (connected(level, start, end)) {
            return Plan.fail(plan, "already");
        }
        TrackEdge shape = TrackEdge.between(start, startAxis, startSide, end, endAxis, endSide);
        int cost = cost(shape) + (newEnd ? 1 : 0);
        plan = new Plan(start, startAxis, startSide, end, endAxis, endSide, newEnd, shape, null, cost);
        if (shape.minRadius() < MIN_RADIUS) {
            return Plan.fail(plan, "too_sharp", (int) MIN_RADIUS);
        }
        if (shape.maxGradient() > MAX_GRADIENT) {
            return Plan.fail(plan, "too_steep");
        }
        return plan;
    }

    private static boolean connected(Level level, BlockPos a, BlockPos b) {
        if (level.getBlockEntity(a) instanceof TrackBlockEntity track) {
            for (TrackConnection connection : track.connections()) {
                if (connection.other().equals(b)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** How many pieces of track the player is carrying. */
    public static int carried(Player player) {
        return player.getInventory().clearOrCountMatchingItems(stack -> stack.is(ModItems.TRACK.get()), 0, player.inventoryMenu.getCraftSlots());
    }

    /**
     * Builds a planned curve: places the new end block if there is one, joins the two ends and (unless the player's in
     * creative, or there's no player) takes the track it costs. Returns false (building nothing) if it can't.
     */
    public static boolean build(Level level, Plan plan, @Nullable Player player) {
        if (!plan.ok()) {
            return false;
        }
        boolean pays = player != null && !player.getAbilities().instabuild;
        if (pays && carried(player) < plan.cost()) {
            return false;
        }
        if (plan.newEnd()) {
            level.setBlock(plan.end(), ModBlocks.TRACK.get().defaultBlockState().setValue(TrackBlock.AXIS, plan.endAxis()), 3);
        }
        if (!(level.getBlockEntity(plan.start()) instanceof TrackBlockEntity from) || !(level.getBlockEntity(plan.end()) instanceof TrackBlockEntity to)) {
            return false;
        }
        from.add(new TrackConnection(plan.end(), plan.startSide(), plan.endSide()));
        to.add(new TrackConnection(plan.start(), plan.endSide(), plan.startSide()));
        if (pays) {
            player.getInventory().clearOrCountMatchingItems(stack -> stack.is(ModItems.TRACK.get()), plan.cost(), player.inventoryMenu.getCraftSlots());
        }
        Vec3 middle = plan.shape().point(plan.shape().length() / 2);
        level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
        return true;
    }

    /** Places a straight line of track blocks (for the tests and the showcase). */
    public static void line(Level level, BlockPos from, TrackAxis axis, int blocks) {
        for (int i = 0; i < blocks; i++) {
            level.setBlock(from.offset(axis.dx * i, 0, axis.dz * i), ModBlocks.TRACK.get().defaultBlockState().setValue(TrackBlock.AXIS, axis), 3);
        }
    }
}
