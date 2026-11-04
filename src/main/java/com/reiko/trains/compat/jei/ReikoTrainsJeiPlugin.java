package com.reiko.trains.compat.jei;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.registry.ModEntities;
import com.reiko.trains.registry.ModItems;
import com.reiko.trains.train.DieselFuel;
import com.reiko.trains.train.ElectricLocomotive;
import com.reiko.trains.train.Locomotive;
import com.reiko.trains.train.TrackLayer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.annotation.Nullable;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * JEI support, loaded by JEI only when it's installed (nothing else in the mod touches JEI). The mod's recipes are all
 * ordinary crafting recipes, which JEI shows on its own; this adds:
 * <ul>
 * <li>an information page for each of the mod's items, saying what it does;</li>
 * <li>"Locomotive Fuel": what the steam engines, the diesel and the electrics run on, and how long each fuel lasts;</li>
 * <li>"Track-Laying Wagon": what it lays, and what it fills dips with.</li>
 * </ul>
 */
@JeiPlugin
public final class ReikoTrainsJeiPlugin implements IModPlugin {
    public static final mezz.jei.api.recipe.RecipeType<LocomotiveFuel> LOCOMOTIVE_FUEL =
            mezz.jei.api.recipe.RecipeType.create(ReikoTrains.MODID, "locomotive_fuel", LocomotiveFuel.class);
    public static final mezz.jei.api.recipe.RecipeType<TrackLaying> TRACK_LAYING =
            mezz.jei.api.recipe.RecipeType.create(ReikoTrains.MODID, "track_laying", TrackLaying.class);

    @Nullable
    private static IJeiRuntime runtime;

    @Override
    public ResourceLocation getPluginUid() {
        return ReikoTrains.id("jei_plugin");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new LocomotiveFuelCategory(registration.getJeiHelpers().getGuiHelper()),
                new TrackLayingCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            String key = "jei.reikotrains.info." + id.getPath();
            if (id.getNamespace().equals(ReikoTrains.MODID) && I18n.exists(key)) {
                registration.addIngredientInfo(new ItemStack(item), VanillaTypes.ITEM_STACK, Component.translatable(key));
            }
        }
        Level level = Minecraft.getInstance().level;
        registration.addRecipes(LOCOMOTIVE_FUEL, List.of(steam(level), diesel(level), electric(level)));
        registration.addRecipes(TRACK_LAYING, List.of(
                new TrackLaying(List.of(new ItemStack(ModItems.TRACK.get())),
                        List.of(Component.translatable("jei.reikotrains.track_laying.lays"), Component.translatable("jei.reikotrains.track_laying.lays2"))),
                new TrackLaying(tagged(TrackLayer.TRACKBED),
                        List.of(Component.translatable("jei.reikotrains.track_laying.fills", TrackLayer.MAX_FILL),
                                Component.translatable("jei.reikotrains.track_laying.fills2")))));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ModItems.TRACK_LAYER.get()), TRACK_LAYING);
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** JEI's runtime while a world is open, for the dev showcase to open its pages. */
    @Nullable
    public static IJeiRuntime runtime() {
        return runtime;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Locomotive fuel

    /** Coal and the like, grouped by how long they burn, and water. */
    private static LocomotiveFuel steam(@Nullable Level level) {
        double tankUse = use(level, ModEntities.STEAM_LOCOMOTIVE.get());
        double expressUse = use(level, ModEntities.EXPRESS_LOCOMOTIVE.get());
        Map<Integer, List<ItemStack>> byBurn = new TreeMap<>();
        for (ItemStack stack : tagged(Locomotive.FUEL)) {
            int burn = stack.getBurnTime(RecipeType.SMELTING);
            if (burn > 0) {
                byBurn.computeIfAbsent(burn, b -> new ArrayList<>()).add(stack);
            }
        }
        List<LocomotiveFuel.Slot> slots = new ArrayList<>();
        for (Map.Entry<Integer, List<ItemStack>> group : byBurn.entrySet()) {
            int burn = group.getKey();
            java.util.function.DoubleFunction<Component> note = tankUse > 0 && expressUse > 0
                    ? use -> Component.translatable("jei.reikotrains.fuel.steam", time(burn / tankUse / use), time(burn / expressUse / use))
                    : LocomotiveFuel.fixed(Component.translatable("jei.reikotrains.fuel.burns", burn / 20));
            slots.add(new LocomotiveFuel.Slot(group.getValue(), note));
        }
        slots.add(new LocomotiveFuel.Slot(List.of(new ItemStack(Items.WATER_BUCKET), new ItemStack(ModItems.WATER_TOWER.get())),
                LocomotiveFuel.fixed(Component.translatable("jei.reikotrains.fuel.water"))));
        return new LocomotiveFuel(slots, List.of(),
                List.of(new ItemStack(ModItems.STEAM_LOCOMOTIVE.get()), new ItemStack(ModItems.EXPRESS_LOCOMOTIVE.get()),
                        new ItemStack(ModItems.TENDER.get())),
                List.of(LocomotiveFuel.fixed(Component.translatable("jei.reikotrains.fuel.steam_line")),
                        LocomotiveFuel.fixed(Component.translatable("jei.reikotrains.fuel.steam_line2"))));
    }

    /** The fluids in the diesel tag (BuildCraft's Dense Fuel and Mixed Heavy Fuels, other mods' diesel). */
    private static LocomotiveFuel diesel(@Nullable Level level) {
        double use = use(level, ModEntities.DIESEL_LOCOMOTIVE.get());
        List<LocomotiveFuel.FluidSlot> fluids = new ArrayList<>();
        BuiltInRegistries.FLUID.getTag(DieselFuel.TAG).ifPresent(tag -> {
            for (Holder<Fluid> holder : tag) {
                Fluid fluid = holder.value();
                if (fluid instanceof FlowingFluid flowing && flowing.getSource() != fluid) {
                    continue; // just the still fluid, once
                }
                double energy = energy(fluid);
                java.util.function.DoubleFunction<Component> note = energy > 0 && use > 0
                        ? fuelUse -> Component.translatable("jei.reikotrains.fuel.diesel", time(energy / use / fuelUse))
                        : LocomotiveFuel.fixed(Component.translatable("jei.reikotrains.fuel.diesel_unknown"));
                fluids.add(new LocomotiveFuel.FluidSlot(fluid, note));
            }
        });
        return new LocomotiveFuel(List.of(), fluids.subList(0, Math.min(fluids.size(), LocomotiveFuelCategory.PER_ROW * 2)),
                List.of(new ItemStack(ModItems.DIESEL_LOCOMOTIVE.get())),
                List.of(LocomotiveFuel.fixed(Component.translatable("jei.reikotrains.fuel.diesel_line")),
                        LocomotiveFuel.fixed(Component.translatable("jei.reikotrains.fuel.diesel_line2"))));
    }

    /** BuildCraft power: from a charging point, or an overhead line. */
    private static LocomotiveFuel electric(@Nullable Level level) {
        double electricUse = use(level, ModEntities.ELECTRIC_LOCOMOTIVE.get());
        List<LocomotiveFuel.Slot> slots = List.of(
                new LocomotiveFuel.Slot(List.of(new ItemStack(ModItems.CHARGING_POINT.get())), LocomotiveFuel.fixed(Component.translatable("jei.reikotrains.fuel.charging_point"))),
                new LocomotiveFuel.Slot(List.of(new ItemStack(ModItems.CATENARY_MAST.get())), LocomotiveFuel.fixed(Component.translatable("jei.reikotrains.fuel.mast"))),
                new LocomotiveFuel.Slot(List.of(new ItemStack(ModItems.OVERHEAD_WIRE.get())), LocomotiveFuel.fixed(Component.translatable("jei.reikotrains.fuel.wire"))),
                new LocomotiveFuel.Slot(List.of(new ItemStack(ModItems.SUBSTATION.get())), LocomotiveFuel.fixed(Component.translatable("jei.reikotrains.fuel.substation"))));
        java.util.function.DoubleFunction<Component> battery = electricUse > 0
                ? use -> Component.translatable("jei.reikotrains.fuel.battery", time(ElectricLocomotive.BATTERY_CAPACITY / electricUse / use))
                : LocomotiveFuel.fixed(Component.translatable("jei.reikotrains.fuel.battery_plain"));
        return new LocomotiveFuel(slots, List.of(),
                List.of(new ItemStack(ModItems.ELECTRIC_LOCOMOTIVE.get()), new ItemStack(ModItems.BULLET_TRAIN.get()),
                        new ItemStack(ModItems.VINTAGE_TRAM.get()), new ItemStack(ModItems.LOW_FLOOR_TRAM.get())),
                List.of(battery, LocomotiveFuel.fixed(Component.translatable("jei.reikotrains.fuel.electric_line2"))));
    }

    /** How much a locomotive uses a tick at full throttle, or 0 if there's no world to make one in to ask. */
    private static double use(@Nullable Level level, EntityType<? extends Locomotive> type) {
        if (level == null) {
            return 0;
        }
        Locomotive loco = type.create(level);
        return loco == null ? 0 : loco.fullThrottleUse();
    }

    /** MJ in a bucket of a diesel fluid, or 0 if it can't be told here (the server's fallback setting isn't known). */
    private static double energy(Fluid fluid) {
        try {
            return DieselFuel.energyPerBucket(new FluidStack(fluid, 1000));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** "about 3 min", from a number of ticks. */
    static Component time(double ticks) {
        double seconds = ticks / 20.0;
        if (seconds < 90) {
            return Component.translatable("jei.reikotrains.time.seconds", Math.max(1, Math.round(seconds)));
        }
        double minutes = seconds / 60.0;
        if (minutes < 90) {
            return Component.translatable("jei.reikotrains.time.minutes", Math.round(minutes));
        }
        return Component.translatable("jei.reikotrains.time.hours", Math.round(minutes / 60.0 * 10.0) / 10.0);
    }

    /** One of each item in an item tag. */
    private static List<ItemStack> tagged(net.minecraft.tags.TagKey<Item> tag) {
        List<ItemStack> stacks = new ArrayList<>();
        BuiltInRegistries.ITEM.getTag(tag).ifPresent(set -> {
            for (Holder<Item> holder : set) {
                stacks.add(new ItemStack(holder.value()));
            }
        });
        return stacks;
    }
}
