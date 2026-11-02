package com.reiko.trains.registry;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.track.TrackBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ReikoTrains.MODID);

    @SuppressWarnings("DataFlowIssue")
    public static final Supplier<BlockEntityType<TrackBlockEntity>> TRACK = BLOCK_ENTITIES.register("track",
            () -> BlockEntityType.Builder.of(TrackBlockEntity::new, ModBlocks.TRACK.get()).build(null));

    private ModBlockEntities() {
    }
}
