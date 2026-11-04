package com.reiko.trains.turntable;

import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.registry.ModSounds;
import com.reiko.trains.track.EdgeRef;
import com.reiko.trains.track.TrackAxis;
import com.reiko.trains.track.TrackBlock;
import com.reiko.trains.track.TrackEdge;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.track.TrackPos;
import com.reiko.trains.track.TrackScanner;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A turntable's workings, kept by its pivot. Its bridge points one of eight ways (east first, then round clockwise
 * through south, west and north) and, standing still, it's track: the track-level blocks over the pit along its line
 * count as track blocks, so they join up with track blocks laid in line beyond the pit's edge, and trains run on and
 * off as they would anywhere. Turning, it isn't track at all.
 *
 * <p>When it turns it takes with it every train that's all on its bridge (it won't turn if a train's stopped half on,
 * or isn't stopped, or anything sticks out over the pit): they stop being moved along the track, and are turned with
 * the bridge, and put back on it as track once it's lined up again. Their place along the bridge (from its middle, the
 * way its front end points) is kept, and which way along it they face.
 */
public class TurntableBlockEntity extends BlockEntity {
    /** The big pit's radius, in blocks, from the middle of the pivot. */
    public static final double RADIUS = 7.5;
    /** How fast it turns, in degrees a tick: half way round in six seconds. */
    public static final float TURN_RATE = 1.5F;
    /** The eight ways the bridge can point, as steps from block to block: east first, then clockwise. */
    private static final int[][] WAYS = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final Map<Level, Set<TurntableBlockEntity>> LOADED = new WeakHashMap<>();

    public enum Result {
        TURNING("turning"),
        BUSY("busy"),
        MOVING("moving"),
        IN_THE_WAY("in_the_way");

        private final String key;

        Result(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /** A train car going round with the bridge: how far along it its middle is (the way the front end points), and whether it faces that way. */
    public record Carried(UUID id, double along, boolean front) {
    }

    /** Which way the bridge's front end points, 0 to 7 (when it's turning, which way it was pointing). */
    private int way;
    private boolean turning;
    private int target;
    /** The turn: from which angle (degrees, clockwise from east), how far, starting when (game time), over how many ticks. */
    private float fromAngle;
    private float turn;
    private long start;
    private int duration;
    private final List<Carried> carried = new ArrayList<>();
    /** The blocks of the pit with a redstone signal beside them. */
    private final Set<BlockPos> powered = new HashSet<>();
    private int comparator = -1;

    public TurntableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TURNTABLE.get(), pos, state);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // The bridge as track

    /** The track-level block over the pivot: the middle of the bridge. */
    public BlockPos hub() {
        return worldPosition.above();
    }

    /** The pit's blocks (its size). */
    public TurntableBlock pit() {
        return getBlockState().getBlock() instanceof TurntableBlock block ? block : com.reiko.trains.registry.ModBlocks.TURNTABLE.get();
    }

    /** How far the pit reaches from the middle of the pivot, in blocks. */
    public double radius() {
        return pit().radius();
    }

    /** The middle of the bridge's rail tops. */
    public Vec3 centre() {
        return TrackEdge.node(hub(), false);
    }

    public int way() {
        return way;
    }

    public boolean isTurning() {
        return turning;
    }

    /** The line of track the bridge is when it points this way. */
    public static TrackAxis axisOf(int way) {
        return switch (way & 3) {
            case 0 -> TrackAxis.X;
            case 1 -> TrackAxis.SE;
            case 2 -> TrackAxis.Z;
            default -> TrackAxis.NE;
        };
    }

    /**
     * How many track blocks the bridge is each side of its middle, pointing this way: 7 straight and 5 diagonally for
     * the big one, 5 and 3 for the small one.
     */
    public int reach(int way) {
        int[] step = WAYS[way & 7];
        int k = 0;
        TurntableBlock pit = pit();
        while (pit.underBridge((k + 1) * step[0], (k + 1) * step[1])) {
            k++;
        }
        return k;
    }

    /** The flat unit direction of a way. */
    public static Vec3 direction(int way) {
        int[] step = WAYS[way & 7];
        return new Vec3(step[0], 0, step[1]).normalize();
    }

    /** The track block (if there is one) just beyond the pit's edge, this way, in line with the bridge. */
    public BlockPos exit(int way) {
        int[] step = WAYS[way & 7];
        int k = reach(way) + 1;
        return hub().offset(k * step[0], 0, k * step[1]);
    }

    /** Whether there's track running off the pit's edge this way, in line with the bridge pointing this way. */
    public boolean hasExit(int way) {
        if (level == null) {
            return false;
        }
        BlockPos at = exit(way);
        if (!level.isLoaded(at)) {
            return false;
        }
        BlockState state = level.getBlockState(at);
        return state.getBlock() instanceof TrackBlock && state.getValue(TrackBlock.AXIS) == axisOf(way);
    }

    /**
     * The line of track at the track-level block {@code dx}, {@code dz} from the middle of the bridge (over the pit),
     * or null if the bridge doesn't cover it: it's turning, or points another way.
     */
    @Nullable
    public TrackAxis axisAt(int dx, int dz) {
        if (turning || !pit().underBridge(dx, dz)) {
            return null;
        }
        int[] step = WAYS[way];
        boolean on = step[0] == 0 ? dx == 0 : step[1] == 0 ? dz == 0 : step[0] == step[1] ? dx == dz : dx == -dz;
        return on ? axisOf(way) : null;
    }

    /**
     * For {@link com.reiko.trains.track.TrackGraph}: the line of track at a track-level block over a turntable's pit,
     * if a turntable's bridge is there, lined up; null otherwise.
     */
    @Nullable
    public static TrackAxis bridgeAxis(BlockGetter level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState state = level.getBlockState(below);
        if (!(state.getBlock() instanceof TurntableBlock)) {
            return null;
        }
        BlockPos pivot = TurntableBlock.pivot(state, below);
        if (level instanceof Level world && !world.isLoaded(pivot)) {
            return null;
        }
        return level.getBlockEntity(pivot) instanceof TurntableBlockEntity table
                ? table.axisAt(below.getX() - pivot.getX(), below.getZ() - pivot.getZ()) : null;
    }

    /** Whether this is a track block of the bridge (as it is now). */
    private boolean onBridge(BlockPos node) {
        return node.getY() == hub().getY() && axisAt(node.getX() - hub().getX(), node.getZ() - hub().getZ()) != null;
    }

    /** Whether all of the car's on the bridge: both its bogies on stretches of track between the bridge's own blocks. */
    private boolean allOn(TrainCarEntity car) {
        TrackPos front = car.front();
        TrackPos rear = car.rear();
        return front != null && rear != null && onBridge(front.edge().from()) && onBridge(front.edge().to()) && onBridge(rear.edge().from())
                && onBridge(rear.edge().to());
    }

    /** A place on the bridge as it's lined up now: {@code along} blocks from its middle the way its front end points. */
    private TrackPos bridgePos(double along, boolean facingFront) {
        int[] step = WAYS[way];
        TrackAxis axis = axisOf(way);
        int side = axis.sideToward(direction(way));
        double length = axis.step();
        int k = reach(way);
        int n = Mth.clamp(Mth.floor(along / length), -k, k - 1);
        BlockPos a = hub().offset(n * step[0], 0, n * step[1]);
        BlockPos b = hub().offset((n + 1) * step[0], 0, (n + 1) * step[1]);
        EdgeRef edge = new EdgeRef(a, axis, side, b, axis, -side, false, false);
        return new TrackPos(edge, Mth.clamp(along - n * length, 0, length), facingFront);
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Turning

    /** Which way the bridge points at a moment (game time, with a fraction of a tick): degrees clockwise from east. */
    public float angleAt(double time) {
        if (!turning) {
            return way * 45F;
        }
        float done = duration <= 0 ? 1 : (float) Mth.clamp((time - start) / duration, 0, 1);
        return fromAngle + turn * done;
    }

    /** Where a car's middle is, going round {@code along} blocks out along the bridge, at a moment. */
    public Vec3 carriedPosition(double along, double time) {
        double a = Math.toRadians(angleAt(time));
        return centre().add(Math.cos(a) * along, 0, Math.sin(a) * along);
    }

    /** Which way (yaw) a car going round faces, at a moment. */
    public float carriedYaw(boolean front, double time) {
        double a = Math.toRadians(angleAt(time));
        double fx = Math.cos(a);
        double fz = Math.sin(a);
        if (!front) {
            fx = -fx;
            fz = -fz;
        }
        return (float) (Mth.atan2(-fx, fz) * Mth.RAD_TO_DEG);
    }

    /** How many steps (eighths of a turn, + clockwise) to the next way that lines its front or back end up with track. */
    public int stepsToNext(int direction) {
        for (int m = 1; m < 8; m++) {
            int to = Math.floorMod(way + direction * m, 8);
            if (hasExit(to) || hasExit((to + 4) & 7)) {
                return direction * m;
            }
        }
        return direction;
    }

    /** Turns to the next way that lines it up with track, clockwise ({@code direction} 1) or the other way (-1). */
    public Result turnNext(int direction) {
        return turn(stepsToNext(direction));
    }

    /** Turns it half way round, which turns round any train on it (and keeps it lined up with the track it was). */
    public Result turnRound() {
        return turn(4);
    }

    /** Turns it {@code steps} eighths of a turn, clockwise if positive, taking with it the trains on its bridge. */
    public Result turn(int steps) {
        if (level == null || level.isClientSide) {
            return Result.BUSY;
        }
        if (turning) {
            return Result.BUSY;
        }
        List<TrainCarEntity> riding = new ArrayList<>();
        for (List<TrainCarEntity> train : trainsOver()) {
            for (TrainCarEntity car : train) {
                if (Math.abs(car.trainSpeed()) > 0.005) {
                    return Result.MOVING;
                }
                if (!allOn(car)) {
                    return Result.IN_THE_WAY;
                }
            }
            riding.addAll(train);
        }
        Vec3 front = direction(way);
        Vec3 centre = centre();
        carried.clear();
        for (TrainCarEntity car : riding) {
            Vec3 d = car.position().subtract(centre);
            double along = d.x * front.x + d.z * front.z;
            Vec3 f = car.flatForward();
            boolean facing = f.x * front.x + f.z * front.z > 0;
            carried.add(new Carried(car.getUUID(), along, facing));
            car.carry(worldPosition, along, facing);
        }
        turning = true;
        target = Math.floorMod(way + steps, 8);
        fromAngle = way * 45F;
        turn = steps * 45F;
        start = level.getGameTime();
        duration = Math.max(1, Mth.ceil(Math.abs(turn) / TURN_RATE));
        Vec3 c = centre();
        level.playSound(null, c.x, c.y, c.z, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.8F, 0.6F);
        changed();
        return Result.TURNING;
    }

    /** The trains with any part of them over the pit (or a little above or below it), each listed once. */
    private List<List<TrainCarEntity>> trainsOver() {
        Vec3 c = centre();
        Set<TrainCarEntity> seen = new LinkedHashSet<>();
        List<List<TrainCarEntity>> trains = new ArrayList<>();
        for (TrainCarEntity car : Trains.loadedCars(level)) {
            if (car.isRemoved() || !car.isOnTrack() || seen.contains(car) || Math.abs(car.getY() - c.y) > 3) {
                continue;
            }
            Vec3 f = car.flatForward();
            Vec3 p = car.position();
            Vec3 d = new Vec3(c.x - p.x, 0, c.z - p.z);
            double along = Mth.clamp(d.x * f.x + d.z * f.z, -car.halfLength(), car.halfLength());
            double nearest = Math.hypot(d.x - f.x * along, d.z - f.z * along);
            if (nearest <= radius() + 0.05) {
                List<TrainCarEntity> train = Trains.train(car);
                seen.addAll(train);
                trains.add(train);
            }
        }
        return trains;
    }

    /** Lined up again: the trains it turned go back on the track, where they are on the bridge now. */
    private void finish() {
        way = target;
        turning = false;
        if (level instanceof ServerLevel server) {
            for (Carried ride : carried) {
                if (server.getEntity(ride.id()) instanceof TrainCarEntity car && worldPosition.equals(car.carriedBy())) {
                    double sign = ride.front() ? 1 : -1;
                    double bogie = car.bogieOffset() * sign;
                    car.settleOn(bridgePos(ride.along() + bogie, ride.front()), bridgePos(ride.along() - bogie, ride.front()));
                }
            }
            Vec3 c = centre();
            server.playSound(null, c.x, c.y, c.z, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 0.9F, 0.7F);
        }
        carried.clear();
        changed();
    }

    /** Whether this car's going round on it now. */
    public boolean isCarrying(UUID car) {
        if (!turning) {
            return false;
        }
        for (Carried ride : carried) {
            if (ride.id().equals(car)) {
                return true;
            }
        }
        return false;
    }

    /** Taken up: anything going round on it is let go of (and, with no track under it, comes off). */
    public void release() {
        if (level instanceof ServerLevel server) {
            for (Carried ride : carried) {
                if (server.getEntity(ride.id()) instanceof TrainCarEntity car) {
                    car.uncarry();
                }
            }
        }
        carried.clear();
        turning = false;
    }

    /**
     * A train car that's going round on a turntable, each tick: puts it where the bridge has taken it. False if it isn't
     * going round any more (the turntable's gone, or done with it), and it should get on with being on the track.
     */
    public static boolean hold(TrainCarEntity car, BlockPos pivot, double along, boolean front) {
        Level level = car.level();
        if (!level.isLoaded(pivot)) {
            return true; // not loaded yet: wait for it
        }
        if (level.getBlockEntity(pivot) instanceof TurntableBlockEntity table && table.isCarrying(car.getUUID())) {
            double time = level.getGameTime();
            car.poseCarried(table.carriedPosition(along, time), table.carriedYaw(front, time));
            return true;
        }
        car.uncarry();
        return false;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Redstone, timetables

    /** A block of the pit has (or hasn't) got a signal beside it: as the first comes on, it turns, as a click does. */
    public void redstone(BlockPos cell, boolean on) {
        boolean was = !powered.isEmpty();
        if (on) {
            powered.add(cell.immutable());
        } else {
            powered.remove(cell);
        }
        if (!was && !powered.isEmpty()) {
            turnNext(1);
        }
    }

    public int comparatorSignal() {
        return turning ? 0 : way + 1;
    }

    /** A settled turntable with all of this train on its bridge, if there's one; for timetables, to turn round on. */
    @Nullable
    public static TurntableBlockEntity under(Level level, List<TrainCarEntity> cars) {
        synchronized (LOADED) {
            Set<TurntableBlockEntity> tables = LOADED.get(level);
            if (tables == null || cars.isEmpty()) {
                return null;
            }
            for (TurntableBlockEntity table : tables) {
                if (table.isRemoved() || table.turning) {
                    continue;
                }
                boolean all = true;
                for (TrainCarEntity car : cars) {
                    if (!table.allOn(car)) {
                        all = false;
                        break;
                    }
                }
                if (all) {
                    return table;
                }
            }
        }
        return null;
    }

    /** A turntable a track block runs up to, in line with one of the ways its bridge can point: which way that is. */
    public record Approach(TurntableBlockEntity table, int way) {
    }

    /** The turntable (if any) the track block {@code node} runs up to the pit of, in line with the bridge pointing some way. */
    @Nullable
    public static Approach approachAt(Level level, BlockPos node) {
        synchronized (LOADED) {
            Set<TurntableBlockEntity> tables = LOADED.get(level);
            if (tables == null) {
                return null;
            }
            for (TurntableBlockEntity table : tables) {
                BlockPos hub = table.hub();
                double reach = table.radius() + 2;
                if (table.isRemoved() || node.getY() != hub.getY() || Math.abs(node.getX() - hub.getX()) > reach
                        || Math.abs(node.getZ() - hub.getZ()) > reach) {
                    continue;
                }
                for (int way = 0; way < 8; way++) {
                    if (table.exit(way).equals(node) && table.hasExit(way)) {
                        return new Approach(table, way);
                    }
                }
            }
        }
        return null;
    }

    /** How far it is along the track to {@code target}, going off the bridge this way (from the track beyond it); -1 if it doesn't lead there. */
    public double distanceOut(int way, BlockPos target, double limit) {
        if (level == null || !hasExit(way)) {
            return -1;
        }
        BlockPos out = exit(way);
        if (out.equals(target)) {
            return 0;
        }
        int side = axisOf(way).sideToward(direction(way));
        double best = -1;
        for (EdgeRef edge : TrackGraph.edgesFrom(level, out, side)) {
            double distance = TrackScanner.distanceVia(level, edge, target, limit);
            if (distance >= 0 && (best < 0 || distance < best)) {
                best = distance;
            }
        }
        return best;
    }

    /** The way off the bridge that's the shortest way along the track to {@code target} (but not {@code except}), or -1 if none leads there. */
    public int bestWayTo(BlockPos target, double limit, int except) {
        int best = -1;
        double nearest = Double.POSITIVE_INFINITY;
        for (int way = 0; way < 8; way++) {
            if (way == except) {
                continue;
            }
            double distance = distanceOut(way, target, limit);
            if (distance >= 0 && distance < nearest) {
                nearest = distance;
                best = way;
            }
        }
        return best;
    }

    /** Turns it, the shorter way round, so that the end of the bridge pointing {@code from} points {@code to}. */
    public Result turnEnd(int from, int to) {
        int steps = Math.floorMod(to - from, 8);
        return turn(steps > 4 ? steps - 8 : steps);
    }

    /** Turns it, the shorter way round, to line it up with the track running up to it this way (either end will do). */
    public Result lineUpWith(int exitWay) {
        int front = Math.floorMod(exitWay - way, 8);
        int back = Math.floorMod(exitWay + 4 - way, 8);
        int a = front > 4 ? front - 8 : front;
        int b = back > 4 ? back - 8 : back;
        return turn(Math.abs(a) <= Math.abs(b) ? a : b);
    }

    /** Whether its bridge is lined up with track running up to it this way. */
    public boolean linedUpWith(int exitWay) {
        return !turning && (way & 3) == (exitWay & 3);
    }

    /** Whether any train's over the pit. */
    public boolean hasTrainOver() {
        return !trainsOver().isEmpty();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TurntableBlockEntity table) {
        if (table.turning) {
            long elapsed = level.getGameTime() - table.start;
            if (elapsed >= table.duration) {
                table.finish();
            } else if (elapsed % 24 == 0) {
                Vec3 c = table.centre();
                level.playSound(null, c.x, c.y, c.z, ModSounds.TURNTABLE.get(), SoundSource.BLOCKS, 0.7F, 1.0F);
            }
        }
        int signal = table.comparatorSignal();
        if (signal != table.comparator) {
            table.comparator = signal;
            table.notifyComparators();
        }
    }

    /** Lets comparators beside the pit know it points somewhere else. */
    private void notifyComparators() {
        if (level == null) {
            return;
        }
        TurntableBlock pit = pit();
        int span = pit.span();
        for (int dx = -span; dx <= span; dx++) {
            for (int dz = -span; dz <= span; dz++) {
                if (pit.inPit(dx, dz) && (!pit.inPit(dx + 1, dz) || !pit.inPit(dx - 1, dz) || !pit.inPit(dx, dz + 1) || !pit.inPit(dx, dz - 1))) {
                    BlockPos at = worldPosition.offset(dx, 0, dz);
                    level.updateNeighbourForOutputSignal(at, level.getBlockState(at).getBlock());
                }
            }
        }
    }

    /** Sets which way it points, straight away (placing it, tests). */
    public void setWay(int way) {
        if (!turning) {
            this.way = way & 7;
            changed();
        }
    }

    private void changed() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            synchronized (LOADED) {
                LOADED.computeIfAbsent(level, l -> Collections.newSetFromMap(new WeakHashMap<>())).add(this);
            }
        }
    }

    @Override
    public void setRemoved() {
        forget();
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        forget();
        super.onChunkUnloaded();
    }

    private void forget() {
        if (level != null && !level.isClientSide) {
            synchronized (LOADED) {
                Set<TurntableBlockEntity> tables = LOADED.get(level);
                if (tables != null) {
                    tables.remove(this);
                }
            }
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Saving, syncing

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveTurn(tag);
        ListTag list = new ListTag();
        for (Carried ride : carried) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", ride.id());
            entry.putDouble("Along", ride.along());
            entry.putBoolean("Front", ride.front());
            list.add(entry);
        }
        tag.put("Carried", list);
    }

    private void saveTurn(CompoundTag tag) {
        tag.putByte("Way", (byte) way);
        if (turning) {
            tag.putBoolean("Turning", true);
            tag.putByte("Target", (byte) target);
            tag.putFloat("From", fromAngle);
            tag.putFloat("Turn", turn);
            tag.putLong("Start", start);
            tag.putInt("Duration", duration);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        way = tag.getByte("Way") & 7;
        turning = tag.getBoolean("Turning");
        target = tag.getByte("Target") & 7;
        fromAngle = tag.getFloat("From");
        turn = tag.getFloat("Turn");
        start = tag.getLong("Start");
        duration = tag.getInt("Duration");
        if (tag.contains("Carried")) {
            carried.clear();
            ListTag list = tag.getList("Carried", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                carried.add(new Carried(entry.getUUID("Id"), entry.getDouble("Along"), entry.getBoolean("Front")));
            }
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveTurn(tag);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The whole pit and the bridge over it, so it's drawn while any of it is on screen. */
    public AABB renderBox() {
        double r = radius() + 1;
        return new AABB(worldPosition).inflate(r, 1, r).expandTowards(0, 2, 0);
    }
}
