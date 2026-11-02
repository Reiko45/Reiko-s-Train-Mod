package com.reiko.trains.registry;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.train.DieselLocomotive;
import com.reiko.trains.train.ExpressLocomotive;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.GuardsVan;
import com.reiko.trains.train.TankWagon;
import com.reiko.trains.train.PassengerCoach;
import com.reiko.trains.train.SteamLocomotive;
import com.reiko.trains.train.Tender;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ReikoTrains.MODID);

    public static final Supplier<EntityType<SteamLocomotive>> STEAM_LOCOMOTIVE = register("steam_locomotive",
            EntityType.Builder.<SteamLocomotive>of(SteamLocomotive::new, MobCategory.MISC));
    public static final Supplier<EntityType<PassengerCoach>> PASSENGER_COACH = register("passenger_coach",
            EntityType.Builder.<PassengerCoach>of(PassengerCoach::new, MobCategory.MISC));
    public static final Supplier<EntityType<ExpressLocomotive>> EXPRESS_LOCOMOTIVE = register("express_locomotive",
            EntityType.Builder.<ExpressLocomotive>of(ExpressLocomotive::new, MobCategory.MISC));
    public static final Supplier<EntityType<DieselLocomotive>> DIESEL_LOCOMOTIVE = register("diesel_locomotive",
            EntityType.Builder.<DieselLocomotive>of(DieselLocomotive::new, MobCategory.MISC));
    public static final Supplier<EntityType<Tender>> TENDER = register("tender",
            EntityType.Builder.<Tender>of(Tender::new, MobCategory.MISC));
    public static final Supplier<EntityType<TankWagon>> TANK_WAGON = register("tank_wagon",
            EntityType.Builder.<TankWagon>of(TankWagon::new, MobCategory.MISC));
    public static final Supplier<EntityType<GuardsVan>> GUARDS_VAN = register("guards_van",
            EntityType.Builder.<GuardsVan>of(GuardsVan::new, MobCategory.MISC));
    public static final Supplier<EntityType<FreightWagon>> FREIGHT_WAGON = register("freight_wagon",
            EntityType.Builder.<FreightWagon>of(FreightWagon::new, MobCategory.MISC));

    private ModEntities() {
    }

    /**
     * The car's own box is just its middle; its whole length is covered by its hitbox sections (see CarPart). Cars
     * send their position every tick, as they can move fast.
     */
    private static <T extends Entity> Supplier<EntityType<T>> register(String name, EntityType.Builder<T> builder) {
        return ENTITIES.register(name, () -> builder.sized(1.8F, 2.2F).clientTrackingRange(10).updateInterval(1).build(name));
    }
}
