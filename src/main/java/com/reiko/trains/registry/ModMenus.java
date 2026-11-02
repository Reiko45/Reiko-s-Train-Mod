package com.reiko.trains.registry;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.menu.FreightWagonMenu;
import com.reiko.trains.menu.WagonLoaderMenu;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ReikoTrains.MODID);

    public static final Supplier<MenuType<FreightWagonMenu>> FREIGHT_WAGON = MENUS.register("freight_wagon",
            () -> new MenuType<>(FreightWagonMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final Supplier<MenuType<WagonLoaderMenu>> WAGON_LOADER = MENUS.register("wagon_loader",
            () -> IMenuTypeExtension.create(WagonLoaderMenu::new));

    private ModMenus() {
    }
}
