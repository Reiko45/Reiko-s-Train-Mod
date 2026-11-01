package com.reiko.trains.registry;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.item.CouplerItem;
import com.reiko.trains.item.TrainCarItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ReikoTrains.MODID);

    public static final DeferredItem<TrainCarItem> STEAM_LOCOMOTIVE = ITEMS.register("steam_locomotive",
            () -> new TrainCarItem(ModEntities.STEAM_LOCOMOTIVE, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TrainCarItem> PASSENGER_COACH = ITEMS.register("passenger_coach",
            () -> new TrainCarItem(ModEntities.PASSENGER_COACH, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<TrainCarItem> FREIGHT_WAGON = ITEMS.register("freight_wagon",
            () -> new TrainCarItem(ModEntities.FREIGHT_WAGON, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CouplerItem> COUPLER = ITEMS.register("coupler",
            () -> new CouplerItem(new Item.Properties().stacksTo(1)));

    private ModItems() {
    }
}
