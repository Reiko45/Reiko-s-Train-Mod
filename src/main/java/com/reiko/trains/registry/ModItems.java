package com.reiko.trains.registry;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.item.CouplerItem;
import com.reiko.trains.item.TimetableItem;
import com.reiko.trains.item.TrainCarItem;
import com.reiko.trains.track.TrackItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ReikoTrains.MODID);

    public static final DeferredItem<TrackItem> TRACK = ITEMS.register("track",
            () -> new TrackItem(ModBlocks.TRACK.get(), new Item.Properties()));
    public static final DeferredItem<com.reiko.trains.track.StreetTrackItem> STREET_TRACK = ITEMS.register("street_track",
            () -> new com.reiko.trains.track.StreetTrackItem(ModBlocks.STREET_TRACK.get(), new Item.Properties()));
    public static final DeferredItem<TrainCarItem> STEAM_LOCOMOTIVE = ITEMS.register("steam_locomotive",
            () -> new TrainCarItem(ModEntities.STEAM_LOCOMOTIVE, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TrainCarItem> PASSENGER_COACH = ITEMS.register("passenger_coach",
            () -> new TrainCarItem(ModEntities.PASSENGER_COACH, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TrainCarItem> EXPRESS_LOCOMOTIVE = ITEMS.register("express_locomotive",
            () -> new TrainCarItem(ModEntities.EXPRESS_LOCOMOTIVE, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TrainCarItem> DIESEL_LOCOMOTIVE = ITEMS.register("diesel_locomotive",
            () -> new TrainCarItem(ModEntities.DIESEL_LOCOMOTIVE, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TrainCarItem> ELECTRIC_LOCOMOTIVE = ITEMS.register("electric_locomotive",
            () -> new TrainCarItem(ModEntities.ELECTRIC_LOCOMOTIVE, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TrainCarItem> BULLET_TRAIN = ITEMS.register("bullet_train",
            () -> new TrainCarItem(ModEntities.BULLET_TRAIN, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<TrainCarItem> BULLET_COACH = ITEMS.register("bullet_coach",
            () -> new TrainCarItem(ModEntities.BULLET_COACH, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));
    public static final DeferredItem<TrainCarItem> VINTAGE_TRAM = ITEMS.register("vintage_tram",
            () -> new TrainCarItem(ModEntities.VINTAGE_TRAM, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<com.reiko.trains.item.LowFloorTramItem> LOW_FLOOR_TRAM = ITEMS.register("low_floor_tram",
            () -> new com.reiko.trains.item.LowFloorTramItem(new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TrainCarItem> CAR_TRANSPORTER = ITEMS.register("car_transporter",
            () -> new TrainCarItem(ModEntities.CAR_TRANSPORTER, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TrainCarItem> TENDER = ITEMS.register("tender",
            () -> new TrainCarItem(ModEntities.TENDER, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TrainCarItem> TANK_WAGON = ITEMS.register("tank_wagon",
            () -> new TrainCarItem(ModEntities.TANK_WAGON, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TrainCarItem> GUARDS_VAN = ITEMS.register("guards_van",
            () -> new TrainCarItem(ModEntities.GUARDS_VAN, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TrainCarItem> LIVESTOCK_WAGON = ITEMS.register("livestock_wagon",
            () -> new TrainCarItem(ModEntities.LIVESTOCK_WAGON, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TrainCarItem> FREIGHT_WAGON = ITEMS.register("freight_wagon",
            () -> new TrainCarItem(ModEntities.FREIGHT_WAGON, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CouplerItem> COUPLER = ITEMS.register("coupler",
            () -> new CouplerItem(new Item.Properties().stacksTo(1)));

    public static final DeferredItem<BlockItem> SIGNAL = ITEMS.register("signal",
            () -> new BlockItem(ModBlocks.SIGNAL.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> SIGNAL_BOX = ITEMS.register("signal_box",
            () -> new BlockItem(ModBlocks.SIGNAL_BOX.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> THREE_LIGHT_SIGNAL = ITEMS.register("three_light_signal",
            () -> new BlockItem(ModBlocks.THREE_LIGHT_SIGNAL.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> SWITCH = ITEMS.register("switch_stand",
            () -> new BlockItem(ModBlocks.SWITCH.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> STATION = ITEMS.register("station",
            () -> new BlockItem(ModBlocks.STATION.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> WATER_TOWER = ITEMS.register("water_tower",
            () -> new BlockItem(ModBlocks.WATER_TOWER.get(), new Item.Properties()));
    public static final DeferredItem<com.reiko.trains.turntable.TurntableItem> TURNTABLE = ITEMS.register("turntable",
            () -> new com.reiko.trains.turntable.TurntableItem(new Item.Properties()));
    public static final DeferredItem<com.reiko.trains.shed.EngineShedItem> ENGINE_SHED = ITEMS.register("engine_shed",
            () -> new com.reiko.trains.shed.EngineShedItem(new Item.Properties()));
    public static final DeferredItem<BlockItem> CHARGING_POINT = ITEMS.register("charging_point",
            () -> new BlockItem(ModBlocks.CHARGING_POINT.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> CATENARY_MAST = ITEMS.register("catenary_mast",
            () -> new BlockItem(ModBlocks.CATENARY_MAST.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> SUBSTATION = ITEMS.register("substation",
            () -> new BlockItem(ModBlocks.SUBSTATION.get(), new Item.Properties()));
    public static final DeferredItem<com.reiko.trains.wire.OverheadWireItem> OVERHEAD_WIRE = ITEMS.register("overhead_wire",
            () -> new com.reiko.trains.wire.OverheadWireItem(new Item.Properties()));
    public static final DeferredItem<BlockItem> SPEED_SIGN = ITEMS.register("speed_sign",
            () -> new BlockItem(ModBlocks.SPEED_SIGN.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> TRACK_SENSOR = ITEMS.register("track_sensor",
            () -> new BlockItem(ModBlocks.TRACK_SENSOR.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> LEVEL_CROSSING = ITEMS.register("level_crossing",
            () -> new BlockItem(ModBlocks.LEVEL_CROSSING.get(), new Item.Properties()));
    public static final DeferredItem<BlockItem> WAGON_LOADER = ITEMS.register("wagon_loader",
            () -> new BlockItem(ModBlocks.WAGON_LOADER.get(), new Item.Properties()));
    public static final DeferredItem<TimetableItem> TIMETABLE = ITEMS.register("timetable",
            () -> new TimetableItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
