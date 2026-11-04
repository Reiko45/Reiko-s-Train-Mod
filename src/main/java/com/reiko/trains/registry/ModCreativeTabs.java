package com.reiko.trains.registry;

import com.reiko.trains.ReikoTrains;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ReikoTrains.MODID);

    public static final Supplier<CreativeModeTab> TRAINS = TABS.register("trains", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.reikotrains"))
            .icon(() -> new ItemStack(ModItems.STEAM_LOCOMOTIVE.get()))
            .displayItems((parameters, output) -> {
                output.accept(ModItems.TRACK.get());
                output.accept(ModItems.STREET_TRACK.get());
                output.accept(ModItems.SIGNAL.get());
                output.accept(ModItems.THREE_LIGHT_SIGNAL.get());
                output.accept(ModItems.FOUR_ASPECT_SIGNAL.get());
                output.accept(ModItems.SIGNAL_BOX.get());
                output.accept(ModItems.SWITCH.get());
                output.accept(ModItems.TRACK_SENSOR.get());
                output.accept(ModItems.SPEED_SIGN.get());
                output.accept(ModItems.CATENARY_MAST.get());
                output.accept(ModItems.OVERHEAD_WIRE.get());
                output.accept(ModItems.SUBSTATION.get());
                output.accept(ModItems.STATION.get());
                output.accept(ModItems.LEVEL_CROSSING.get());
                output.accept(ModItems.STEAM_LOCOMOTIVE.get());
                output.accept(ModItems.EXPRESS_LOCOMOTIVE.get());
                output.accept(ModItems.TENDER.get());
                output.accept(ModItems.DIESEL_LOCOMOTIVE.get());
                output.accept(ModItems.ELECTRIC_LOCOMOTIVE.get());
                output.accept(ModItems.BULLET_TRAIN.get());
                output.accept(ModItems.BULLET_COACH.get());
                output.accept(ModItems.VINTAGE_TRAM.get());
                output.accept(ModItems.LOW_FLOOR_TRAM.get());
                output.accept(ModItems.PASSENGER_COACH.get());
                output.accept(ModItems.FREIGHT_WAGON.get());
                output.accept(ModItems.TANK_WAGON.get());
                output.accept(ModItems.LIVESTOCK_WAGON.get());
                output.accept(ModItems.CAR_TRANSPORTER.get());
                output.accept(ModItems.GUARDS_VAN.get());
                output.accept(ModItems.TRACK_LAYER.get());
                output.accept(ModItems.WAGON_LOADER.get());
                output.accept(ModItems.WATER_TOWER.get());
                output.accept(ModItems.TURNTABLE.get());
                output.accept(ModItems.SMALL_TURNTABLE.get());
                output.accept(ModItems.ENGINE_SHED.get());
                output.accept(ModItems.CHARGING_POINT.get());
                output.accept(ModItems.COUPLER.get());
                output.accept(ModItems.TIMETABLE.get());
            })
            .build());

    private ModCreativeTabs() {
    }
}
