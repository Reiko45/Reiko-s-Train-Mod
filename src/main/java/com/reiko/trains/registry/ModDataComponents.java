package com.reiko.trains.registry;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.train.Timetable;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, ReikoTrains.MODID);

    /** On a coupler: the car picked first, waiting to be coupled to the next car clicked. */
    public static final Supplier<DataComponentType<UUID>> COUPLING_FROM = COMPONENTS.registerComponentType("coupling_from",
            builder -> builder.persistent(UUIDUtil.CODEC).networkSynchronized(UUIDUtil.STREAM_CODEC));
    /** On track: the track block a curve is being drawn from. */
    public static final Supplier<DataComponentType<BlockPos>> TRACK_START = COMPONENTS.registerComponentType("track_start",
            builder -> builder.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));

    /** On overhead wire: the mast it's being strung from. */
    public static final Supplier<DataComponentType<BlockPos>> WIRE_FROM = COMPONENTS.registerComponentType("wire_from",
            builder -> builder.persistent(BlockPos.CODEC).networkSynchronized(BlockPos.STREAM_CODEC));

    /** On a timetable: the stations to call at. */
    public static final Supplier<DataComponentType<List<Timetable.Stop>>> TIMETABLE = COMPONENTS.registerComponentType("timetable",
            builder -> builder.persistent(Timetable.CODEC).networkSynchronized(Timetable.STREAM_CODEC));

    private ModDataComponents() {
    }
}
