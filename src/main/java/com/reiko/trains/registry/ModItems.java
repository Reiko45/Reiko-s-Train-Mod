package com.reiko.trains.registry;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.item.CouplerItem;
import com.reiko.trains.item.TimetableItem;
import com.reiko.trains.item.TrainCarItem;
import com.reiko.trains.track.TrackItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ReikoTrains.MODID);

    public static final DeferredItem<TrackItem> TRACK = ITEMS.register("track",
            () -> new TrackItem(ModBlocks.TRACK.get(), new Item.Properties()));
    public static final DeferredItem<TrainCarItem> STEAM_LOCOMOTIVE = ITEMS.register("steam_locomotive",
            () -> new TrainCarItem(ModEntities.STEAM_LOCOMOTIVE, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TrainCarItem> PASSENGER_COACH = ITEMS.register("passenger_coach",
            () -> new TrainCarItem(ModEntities.PASSENGER_COACH, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TrainCarItem> FREIGHT_WAGON = ITEMS.register("freight_wagon",
            () -> new TrainCarItem(ModEntities.FREIGHT_WAGON, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CouplerItem> COUPLER = ITEMS.register("coupler",
            () -> new CouplerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> SIGNAL = ITEMS.register("signal",
            () -> new BlockItem(ModBlocks.SIGNAL.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> SWITCH = ITEMS.register("switch_stand",
            () -> new BlockItem(ModBlocks.SWITCH.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> STATION = ITEMS.register("station",
            () -> new BlockItem(ModBlocks.STATION.get(), new Item.Properties()));
    public static final DeferredItem<TimetableItem> TIMETABLE = ITEMS.register("timetable",
            () -> new TimetableItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
