package com.reiko.trains.signal;

import com.reiko.trains.config.TrainsConfig;
import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.registry.ModBlocks;
import com.reiko.trains.registry.ModSounds;
import com.reiko.trains.track.EdgeRef;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.track.TrackPos;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A level crossing's workings. It watches the track either way from the track block it's beside: when a train is
 * on the crossing (within {@link #ON_CROSSING} blocks of that track block), or coming towards it and due there within
 * the warning time (ten seconds, by default), the lamps start flashing; {@link #LEAD_TICKS} later the arm comes down.
 * When the last train's gone by (and none is coming), the arm goes back up and the lamps go out once it's up.
 */
public class LevelCrossingBlockEntity extends BlockEntity {
    /** How long the lamps flash before the arm starts down, in ticks. */
    public static final int LEAD_TICKS = 60;
    /** How long the arm takes to come down or go up, in ticks. */
    public static final int ARM_TICKS = 40;
    /** How near the crossing's track block (blocks along the track, either way) a train is on the crossing. */
    public static final double ON_CROSSING = 8.0;
    /** How often the bell rings, in ticks. */
    public static final int BELL_TICKS = 10;
    private static final int TRACK_REFRESH = 100;
    /** What keeps the arm's barrier out of a block: anything solid in the way (not dropped items). */
    private static final Predicate<Entity> IN_THE_WAY = entity -> !entity.isSpectator() && !(entity instanceof ItemEntity)
            && !(entity instanceof ExperienceOrb);

    @Nullable
    private BlockPos node;
    /** The track near the crossing: each stretch, pointing away from it, by how far its start is from it. */
    @Nullable
    private Map<EdgeRef, Double> approach;
    private int approachAge;
    private boolean coming;
    private int warned;
    /** Server: how far down the arm is, 0 (up) to {@link #ARM_TICKS} (down). */
    private int arm;
    private boolean started;
    // Client: the arm's swing, 0 up to 1 down.
    private float swing;
    private float swingO;
    private boolean shown;

    public LevelCrossingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.LEVEL_CROSSING.get(), pos, state);
    }

    /** Ties the crossing to the track block it's beside. */
    public void attach(BlockPos node) {
        this.node = node.immutable();
        this.approach = null;
        setChanged();
    }

    public BlockPos node() {
        return node == null ? worldPosition : node;
    }

    public boolean isAttached() {
        return node != null && level != null && TrackGraph.axis(level, node) != null;
    }

    /** Whether the lamps are flashing. */
    public boolean isWarning() {
        return getBlockState().getValue(LevelCrossingBlock.WARNING);
    }

    /** Whether the arm's down (or coming down). */
    public boolean isDown() {
        return getBlockState().getValue(LevelCrossingBlock.DOWN);
    }

    /** Makes the arm this long, moving its barrier if it's down. */
    public void setLength(int length) {
        if (level == null) {
            return;
        }
        BlockState state = getBlockState();
        LevelCrossingBlock.clearArm(level, worldPosition, state.getValue(LevelCrossingBlock.FACING), state.getValue(LevelCrossingBlock.LENGTH));
        BlockState next = state.setValue(LevelCrossingBlock.LENGTH, length);
        level.setBlock(worldPosition, next, 3);
        if (next.getValue(LevelCrossingBlock.DOWN)) {
            placeArm(level, worldPosition, next);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Watching for trains

    /** How long before a train gets to the crossing it starts warning, in ticks. */
    public static int warningTicks() {
        return TrainsConfig.CROSSING_WARNING.get() * 20;
    }

    /** How far either way along the track it looks: far enough to see the fastest train in time. */
    private static double lookAhead() {
        return Math.min(1024, ON_CROSSING + 32 + warningTicks() * TrainsConfig.MAX_SPEED.get() * 1.5);
    }

    private record Step(BlockPos node, int side, double distance) {
    }

    private Map<EdgeRef, Double> approach() {
        if (approach == null || ++approachAge > TRACK_REFRESH) {
            approachAge = 0;
            approach = new HashMap<>();
            if (isAttached()) {
                double look = lookAhead();
                Deque<Step> open = new ArrayDeque<>();
                open.add(new Step(node, 1, 0));
                open.add(new Step(node, -1, 0));
                int guard = 0;
                while (!open.isEmpty() && guard++ < 4096) {
                    Step step = open.poll();
                    for (EdgeRef edge : TrackGraph.edgesFrom(level, step.node, step.side)) {
                        if (approach.containsKey(edge) || approach.containsKey(edge.reversed())) {
                            continue;
                        }
                        approach.put(edge, step.distance);
                        double end = step.distance + edge.geometry().length();
                        if (end < look) {
                            open.add(new Step(edge.to(), -edge.toSide(), end));
                        }
                    }
                }
            }
        }
        return approach;
    }

    /** Whether a train is on the crossing, or coming and due there within the warning time. */
    public boolean trainComing() {
        Map<EdgeRef, Double> edges = approach();
        if (edges.isEmpty()) {
            return false;
        }
        int warning = warningTicks();
        for (TrainCarEntity car : Trains.loadedCars(level)) {
            if (car.isRemoved() || !car.isOnTrack()) {
                continue;
            }
            double speed = car.trainSpeed();
            // The car's ends stick out past its bogies.
            double overhang = Math.max(0, car.halfLength() - car.bogieOffset());
            for (TrackPos bogie : new TrackPos[] {car.front(), car.rear()}) {
                boolean movingAlong = speed > 0 == bogie.facingAlong();
                double distance;
                boolean away;
                Double start = edges.get(bogie.edge());
                if (start != null) {
                    distance = start + bogie.distance();
                    away = movingAlong;
                } else {
                    start = edges.get(bogie.edge().reversed());
                    if (start == null) {
                        continue;
                    }
                    distance = start + bogie.edge().geometry().length() - bogie.distance();
                    away = !movingAlong;
                }
                double gap = distance - overhang - ON_CROSSING;
                if (gap <= 0) {
                    return true;
                }
                if (!away && Math.abs(speed) > 1.0E-4 && gap / Math.abs(speed) <= warning) {
                    return true;
                }
            }
        }
        return false;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // The arm's barrier

    /** Puts the barrier under the lowered arm, in every empty block of road it reaches over with nobody in it. */
    static void placeArm(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(LevelCrossingBlock.FACING);
        BlockState barrier = ModBlocks.CROSSING_ARM.get().defaultBlockState().setValue(CrossingArmBlock.AXIS, facing.getAxis());
        for (int i = 1; i <= state.getValue(LevelCrossingBlock.LENGTH); i++) {
            BlockPos at = LevelCrossingBlock.armBlock(pos, facing, i);
            if (!level.isLoaded(at)) {
                break;
            }
            if (!level.getBlockState(at).isAir()) {
                continue;
            }
            AABB box = barrier.getCollisionShape(level, at).bounds().move(at);
            if (level.getEntities((Entity) null, box, IN_THE_WAY).isEmpty()) {
                level.setBlock(at, barrier, 3);
            }
        }
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, LevelCrossingBlockEntity crossing) {
        boolean down = state.getValue(LevelCrossingBlock.DOWN);
        boolean warning = state.getValue(LevelCrossingBlock.WARNING);
        if (!crossing.started) {
            // Just loaded: tidy up a barrier left from before, and carry on from where it was.
            crossing.started = true;
            crossing.arm = down ? ARM_TICKS : 0;
            crossing.warned = down ? LEAD_TICKS : 0;
            if (!down) {
                LevelCrossingBlock.clearArm(level, pos, state.getValue(LevelCrossingBlock.FACING), state.getValue(LevelCrossingBlock.LENGTH));
            }
        }
        boolean check = (level.getGameTime() + pos.hashCode()) % 4 == 0;
        if (check) {
            crossing.coming = crossing.isAttached() && crossing.trainComing();
        }
        if (crossing.coming) {
            warning = true;
            if (crossing.warned < LEAD_TICKS) {
                crossing.warned++;
            } else {
                down = true;
            }
        } else {
            down = false;
            if (crossing.arm == 0) {
                warning = false;
                crossing.warned = 0;
            }
        }
        crossing.arm = down ? Math.min(ARM_TICKS, crossing.arm + 1) : Math.max(0, crossing.arm - 1);
        if (down != state.getValue(LevelCrossingBlock.DOWN) || warning != state.getValue(LevelCrossingBlock.WARNING)) {
            boolean wasDown = state.getValue(LevelCrossingBlock.DOWN);
            state = state.setValue(LevelCrossingBlock.DOWN, down).setValue(LevelCrossingBlock.WARNING, warning);
            level.setBlock(pos, state, 3);
            if (wasDown && !down) {
                LevelCrossingBlock.clearArm(level, pos, state.getValue(LevelCrossingBlock.FACING), state.getValue(LevelCrossingBlock.LENGTH));
            } else if (down && !wasDown) {
                placeArm(level, pos, state);
            }
        } else if (down && check) {
            // Fill in where something was in the way.
            placeArm(level, pos, state);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Client: the arm's swing and the bell

    public static void clientTick(Level level, BlockPos pos, BlockState state, LevelCrossingBlockEntity crossing) {
        float target = state.getValue(LevelCrossingBlock.DOWN) ? 1 : 0;
        if (!crossing.shown) {
            crossing.shown = true;
            crossing.swing = target;
        }
        crossing.swingO = crossing.swing;
        float step = 1F / ARM_TICKS;
        crossing.swing = target > crossing.swing ? Math.min(target, crossing.swing + step) : Math.max(target, crossing.swing - step);
        if (state.getValue(LevelCrossingBlock.WARNING) && level.getGameTime() % BELL_TICKS == 0) {
            level.playLocalSound(pos, ModSounds.CROSSING_BELL.get(), SoundSource.BLOCKS, 0.7F, 1.0F, false);
        }
    }

    /** How far down the arm is, 0 (up) to 1 (across the road), eased at each end. */
    public float swing(float partialTick) {
        float t = Mth.lerp(partialTick, swingO, swing);
        return t * t * (3 - 2 * t);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (node != null) {
            tag.putLong("Track", node.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Track")) {
            node = BlockPos.of(tag.getLong("Track"));
        }
    }
}
