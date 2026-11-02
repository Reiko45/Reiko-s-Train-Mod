package com.reiko.trains.signal;

import com.reiko.trains.item.TimetableItem;
import com.reiko.trains.item.TrainCarItem;
import com.reiko.trains.registry.ModBlockEntities;
import com.reiko.trains.track.TrackGraph;
import com.reiko.trains.train.Locomotive;
import com.reiko.trains.train.TankWagon;
import com.reiko.trains.train.Timetable;
import com.reiko.trains.train.TrainCarEntity;
import com.reiko.trains.train.Trains;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;

/**
 * A track sensor's workings. It's tied to a track block and senses the train over it (moving or standing), if the train
 * is going the way it's set to sense and passes its filter: an item shown on its back. With nothing there, it senses
 * every train; a train car (a locomotive, coach or wagon) means only trains with that kind of car; a timetable, only
 * trains running those stops, in that order round (or, a blank one, any train on a timetable); a filled bucket or other
 * fluid container, only trains with a tank wagon of that fluid; anything else, only trains carrying it in a freight
 * wagon (or tender).
 */
public class TrackSensorBlockEntity extends BlockEntity {
    @Nullable
    private BlockPos node;
    private ItemStack filter = ItemStack.EMPTY;
    /** The train it last sensed (its first car), and whether it was going the right way: for when it stops over it. */
    @Nullable
    private TrainCarEntity last;
    private boolean lastWay;
    private int comparator;

    public TrackSensorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRACK_SENSOR.get(), pos, state);
    }

    /** Ties it to the track block it watches. */
    public void attach(BlockPos node) {
        this.node = node.immutable();
        setChanged();
    }

    public BlockPos node() {
        return node == null ? worldPosition : node;
    }

    public boolean isAttached() {
        return node != null && level != null && TrackGraph.axis(level, node) != null;
    }

    public ItemStack filter() {
        return filter;
    }

    /** Sets its filter to (a copy of one of) this item, or clears it. */
    public void setFilter(ItemStack stack) {
        filter = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** What it senses now, in words: for telling whoever set it. */
    public Component describeFilter() {
        if (filter.isEmpty()) {
            return Component.translatable("message.reikotrains.sensor.filter.any");
        }
        if (filter.getItem() instanceof TrainCarItem) {
            return Component.translatable("message.reikotrains.sensor.filter.car", filter.getHoverName());
        }
        if (filter.getItem() instanceof TimetableItem) {
            List<Timetable.Stop> stops = TimetableItem.stops(filter);
            return stops.isEmpty() ? Component.translatable("message.reikotrains.sensor.filter.any_timetable")
                    : Component.translatable("message.reikotrains.sensor.filter.timetable", stops.size());
        }
        Optional<FluidStack> fluid = FluidUtil.getFluidContained(filter);
        if (fluid.isPresent()) {
            return Component.translatable("message.reikotrains.sensor.filter.carrying", fluid.get().getHoverName());
        }
        return Component.translatable("message.reikotrains.sensor.filter.carrying", filter.getHoverName());
    }

    /** How many cars the train it's sensing has (up to 15), for a comparator; 0 when it senses none. */
    public int comparatorSignal() {
        return comparator;
    }

    // ----------------------------------------------------------------------------------------------------------------

    /** Whether the filter lets this train through. */
    public boolean passes(List<TrainCarEntity> train) {
        if (filter.isEmpty()) {
            return true;
        }
        if (filter.getItem() instanceof TrainCarItem item) {
            for (TrainCarEntity car : train) {
                if (car.getType() == item.carType()) {
                    return true;
                }
            }
            return false;
        }
        if (filter.getItem() instanceof TimetableItem) {
            List<Timetable.Stop> wanted = TimetableItem.stops(filter);
            for (TrainCarEntity car : train) {
                if (car instanceof Locomotive loco && loco.autopilot().isOn()
                        && (wanted.isEmpty() || sameStations(wanted, loco.autopilot().stops()))) {
                    return true;
                }
            }
            return false;
        }
        Optional<FluidStack> fluid = FluidUtil.getFluidContained(filter);
        if (fluid.isPresent()) {
            for (TrainCarEntity car : train) {
                if (car instanceof TankWagon tank && FluidStack.isSameFluid(tank.tank().getFluid(), fluid.get())) {
                    return true;
                }
            }
            return false;
        }
        for (TrainCarEntity car : train) {
            if (car instanceof Container box) {
                for (int i = 0; i < box.getContainerSize(); i++) {
                    if (ItemStack.isSameItem(box.getItem(i), filter)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Whether two timetables call at the same stations in the same order, going round (so A, B, C is B, C, A). */
    private static boolean sameStations(List<Timetable.Stop> a, List<Timetable.Stop> b) {
        if (a.size() != b.size() || a.isEmpty()) {
            return a.size() == b.size();
        }
        for (int shift = 0; shift < a.size(); shift++) {
            boolean same = true;
            for (int i = 0; i < a.size() && same; i++) {
                same = a.get(i).station().equals(b.get((i + shift) % b.size()).station());
            }
            if (same) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the train's going the way it senses: left or right as seen from behind it, looking at the track. A train
     * standing over it keeps the answer it had while it was moving.
     */
    private boolean goingTheWay(List<TrainCarEntity> train, SensorDirection direction) {
        if (direction == SensorDirection.BOTH) {
            return true;
        }
        TrainCarEntity car = train.getFirst();
        double speed = car.trainSpeed();
        if (Math.abs(speed) < 1.0E-4) {
            return car == last && lastWay;
        }
        Vec3 velocity = car.flatForward().scale(speed);
        Vec3 right = Vec3.atLowerCornerOf(getBlockState().getValue(TrackSensorBlock.FACING).getClockWise().getNormal());
        boolean goingRight = velocity.dot(right) > 0;
        return goingRight == (direction == SensorDirection.RIGHT);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TrackSensorBlockEntity sensor) {
        List<TrainCarEntity> train = sensor.isAttached() ? Trains.over(level, sensor.node) : List.of();
        boolean sensed = false;
        if (!train.isEmpty()) {
            boolean way = sensor.goingTheWay(train, state.getValue(TrackSensorBlock.DIRECTION));
            sensor.last = train.getFirst();
            sensor.lastWay = way;
            sensed = way && sensor.passes(train);
        } else {
            sensor.last = null;
        }
        int signal = sensed ? Math.min(15, train.size()) : 0;
        if (state.getValue(TrackSensorBlock.POWERED) != sensed) {
            level.setBlock(pos, state.setValue(TrackSensorBlock.POWERED, sensed), 3);
        }
        if (signal != sensor.comparator) {
            sensor.comparator = signal;
            level.updateNeighbourForOutputSignal(pos, level.getBlockState(pos).getBlock());
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Saving, and showing the filter to nearby players

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (node != null) {
            tag.putLong("Track", node.asLong());
        }
        if (!filter.isEmpty()) {
            tag.put("Filter", filter.save(registries));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Track")) {
            node = BlockPos.of(tag.getLong("Track"));
        }
        filter = tag.contains("Filter") ? ItemStack.parseOptional(registries, tag.getCompound("Filter")) : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!filter.isEmpty()) {
            tag.put("Filter", filter.save(registries));
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
