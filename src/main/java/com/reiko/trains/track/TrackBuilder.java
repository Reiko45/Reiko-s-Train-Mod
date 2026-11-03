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
import net.minecraft.world.level.block.Block;
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
    /** ... and between street track blocks, for street corners (trams are short, or bend in the middle). */
    public static final double STREET_MIN_RADIUS = 3.0;
    /** The furthest apart two ends can be. */
    public static final double MAX_LENGTH = 48.0;
    /** The steepest a ramp can get anywhere along it (rise over run). */
    public static final double MAX_GRADIENT = 0.5;

    private TrackBuilder() {
    }

    /** A curve worked out but not built yet; {@link #problem} says why it can't be, if it can't. */
    public record Plan(BlockPos start, @Nullable TrackAxis startAxis, int startSide, BlockPos end, @Nullable TrackAxis endAxis, int endSide,
                       boolean newEnd, @Nullable TrackEdge shape, @Nullable Component problem, int cost, boolean startStreet,
                       boolean endStreet) {
        public boolean ok() {
            return problem == null;
        }

        public EdgeRef edge() {
            return new EdgeRef(start, startAxis, startSide, end, endAxis, endSide, startStreet, endStreet);
        }

        /** What it's paid for in: street track between two street track blocks, train track otherwise. */
        public net.minecraft.world.item.Item payItem() {
            return startStreet && endStreet ? ModItems.STREET_TRACK.get() : ModItems.TRACK.get();
        }

        static Plan fail(BlockPos start, BlockPos end, String key, Object... args) {
            return new Plan(start, null, 0, end, null, 0, false, null, Component.translatable("message.reikotrains.track." + key, args), 0,
                    false, false);
        }

        static Plan fail(Plan plan, String key, Object... args) {
            return new Plan(plan.start, plan.startAxis, plan.startSide, plan.end, plan.endAxis, plan.endSide, plan.newEnd, plan.shape,
                    Component.translatable("message.reikotrains.track." + key, args), plan.cost, plan.startStreet, plan.endStreet);
        }
    }

    /** How many pieces of track a curve takes (and gives back when it's broken). */
    public static int cost(TrackEdge shape) {
        return Math.max(1, (int) Math.round(shape.length()));
    }

    /** Where a click ends a curve: on the track block clicked, or in the space next to the face clicked. */
    public static BlockPos endFor(Level level, BlockPos clicked, Direction face) {
        return endFor(level, clicked, face, false);
    }

    /**
     * Where a click ends a curve: on the track block clicked; or, drawing street track, the top of a road block clicked
     * (it's set into the road there); or else in the space next to the face clicked.
     */
    public static BlockPos endFor(Level level, BlockPos clicked, Direction face, boolean street) {
        if (TrackGraph.axis(level, clicked) != null || (street && face == Direction.UP && roadBlock(level, clicked))) {
            return clicked.immutable();
        }
        return clicked.relative(face).immutable();
    }

    /** Whether street track could be set into the block here: a solid, whole, breakable block with nothing in it. */
    public static boolean roadBlock(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.isCollisionShapeFullBlock(level, pos) && !state.hasBlockEntity() && state.getDestroySpeed(level, pos) >= 0
                && ModBlocks.STREET_TRACK.get().defaultBlockState().canSurvive(level, pos);
    }

    /**
     * Works out a curve from the track block at {@code start} to {@code end}. If there's a track block at the end, the
     * curve meets it along its line; if not, a new one goes there, facing so the curve is a smooth arc, or (with
     * {@code keepLook}) facing the way the player looks, for S-bends.
     */
    public static Plan plan(Level level, BlockPos start, BlockPos end, Vec3 look, boolean keepLook) {
        return plan(level, start, end, look, keepLook, false);
    }

    /**
     * The same, with {@code street} for street track: a new end is a street track block set into the road block there
     * (so it has to be one), and a curve between two street track blocks may be as tight as {@link #STREET_MIN_RADIUS}.
     */
    public static Plan plan(Level level, BlockPos start, BlockPos end, Vec3 look, boolean keepLook, boolean street) {
        TrackAxis startAxis = TrackGraph.axis(level, start);
        if (startAxis == null) {
            return Plan.fail(start, end, "start_gone");
        }
        if (start.equals(end)) {
            return Plan.fail(start, end, "same_block");
        }
        boolean startStreet = TrackGraph.street(level, start);
        boolean endExists = TrackGraph.axis(level, end) != null;
        boolean endStreet = endExists ? TrackGraph.street(level, end) : street;
        Vec3 chord = TrackEdge.node(end, endStreet).subtract(TrackEdge.node(start, startStreet));
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
            if (endStreet ? !roadBlock(level, end) : !there.canBeReplaced() || !ModBlocks.TRACK.get().defaultBlockState().canSurvive(level, end)) {
                return Plan.fail(start, end, endStreet ? "cant_place_street" : "cant_place");
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
        double minRadius = startStreet && endStreet ? STREET_MIN_RADIUS : MIN_RADIUS;
        Plan plan = new Plan(start, startAxis, startSide, end, endAxis, endSide, newEnd, null, null, 0, startStreet, endStreet);
        if (endDir.dot(flat) <= 0.05 || startDir.dot(endDir) < -1.0E-3) {
            return Plan.fail(plan, "too_sharp", (int) minRadius);
        }
        if (connected(level, start, end)) {
            return Plan.fail(plan, "already");
        }
        TrackEdge shape = TrackEdge.between(start, startAxis, startSide, end, endAxis, endSide, startStreet, endStreet);
        int cost = cost(shape) + (newEnd ? 1 : 0);
        plan = new Plan(start, startAxis, startSide, end, endAxis, endSide, newEnd, shape, null, cost, startStreet, endStreet);
        if (shape.minRadius() < minRadius - 1.0E-6) {
            return Plan.fail(plan, "too_sharp", (int) minRadius);
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
        return carried(player, ModItems.TRACK.get());
    }

    /** How many of these the player is carrying. */
    public static int carried(Player player, net.minecraft.world.item.Item item) {
        return player.getInventory().clearOrCountMatchingItems(stack -> stack.is(item), 0, player.inventoryMenu.getCraftSlots());
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
        net.minecraft.world.item.Item item = plan.payItem();
        if (pays && carried(player, item) < plan.cost()) {
            return false;
        }
        if (plan.newEnd()) {
            if (plan.endStreet()) {
                // Set into the road: the block that was there comes up (dropped, unless in creative).
                BlockState road = level.getBlockState(plan.end());
                if (pays) {
                    Block.dropResources(road, level, plan.end(), null, player, net.minecraft.world.item.ItemStack.EMPTY);
                }
                level.setBlock(plan.end(), ModBlocks.STREET_TRACK.get().defaultBlockState().setValue(TrackBlock.AXIS, plan.endAxis()), 3);
            } else {
                level.setBlock(plan.end(), ModBlocks.TRACK.get().defaultBlockState().setValue(TrackBlock.AXIS, plan.endAxis()), 3);
            }
        }
        if (!(level.getBlockEntity(plan.start()) instanceof TrackBlockEntity from) || !(level.getBlockEntity(plan.end()) instanceof TrackBlockEntity to)) {
            return false;
        }
        from.add(new TrackConnection(plan.end(), plan.startSide(), plan.endSide()));
        to.add(new TrackConnection(plan.start(), plan.endSide(), plan.startSide()));
        if (pays) {
            player.getInventory().clearOrCountMatchingItems(stack -> stack.is(item), plan.cost(), player.inventoryMenu.getCraftSlots());
        }
        Vec3 middle = plan.shape().point(plan.shape().length() / 2);
        level.playSound(null, middle.x, middle.y, middle.z, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
        return true;
    }

    /** Places a straight line of street track blocks, set into the ground (for the tests and the showcase). */
    public static void streetLine(Level level, BlockPos from, TrackAxis axis, int blocks) {
        for (int i = 0; i < blocks; i++) {
            level.setBlock(from.offset(axis.dx * i, 0, axis.dz * i), ModBlocks.STREET_TRACK.get().defaultBlockState().setValue(TrackBlock.AXIS, axis), 3);
        }
    }

    /** Places a straight line of track blocks (for the tests and the showcase). */
    public static void line(Level level, BlockPos from, TrackAxis axis, int blocks) {
        for (int i = 0; i < blocks; i++) {
            level.setBlock(from.offset(axis.dx * i, 0, axis.dz * i), ModBlocks.TRACK.get().defaultBlockState().setValue(TrackBlock.AXIS, axis), 3);
        }
    }
}
