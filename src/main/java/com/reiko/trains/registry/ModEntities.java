package com.reiko.trains.registry;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.train.DieselLocomotive;
import com.reiko.trains.train.BulletCoach;
import com.reiko.trains.train.BulletTrain;
import com.reiko.trains.train.CarTransporter;
import com.reiko.trains.train.ElectricLocomotive;
import com.reiko.trains.train.ExpressLocomotive;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.GuardsVan;
import com.reiko.trains.train.LivestockWagon;
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
    public static final Supplier<EntityType<ElectricLocomotive>> ELECTRIC_LOCOMOTIVE = register("electric_locomotive",
            EntityType.Builder.<ElectricLocomotive>of(ElectricLocomotive::new, MobCategory.MISC));
    public static final Supplier<EntityType<BulletTrain>> BULLET_TRAIN = register("bullet_train",
            EntityType.Builder.<BulletTrain>of(BulletTrain::new, MobCategory.MISC));
    public static final Supplier<EntityType<BulletCoach>> BULLET_COACH = register("bullet_coach",
            EntityType.Builder.<BulletCoach>of(BulletCoach::new, MobCategory.MISC));
    public static final Supplier<EntityType<com.reiko.trains.train.VintageTram>> VINTAGE_TRAM = register("vintage_tram",
            EntityType.Builder.<com.reiko.trains.train.VintageTram>of(com.reiko.trains.train.VintageTram::new, MobCategory.MISC));
    public static final Supplier<EntityType<com.reiko.trains.train.TramCab>> TRAM_CAB = register("tram_cab",
            EntityType.Builder.<com.reiko.trains.train.TramCab>of(com.reiko.trains.train.TramCab::new, MobCategory.MISC));
    public static final Supplier<EntityType<com.reiko.trains.train.TramMiddle>> TRAM_MIDDLE = register("tram_middle",
            EntityType.Builder.<com.reiko.trains.train.TramMiddle>of(com.reiko.trains.train.TramMiddle::new, MobCategory.MISC));
    public static final Supplier<EntityType<CarTransporter>> CAR_TRANSPORTER = register("car_transporter",
            EntityType.Builder.<CarTransporter>of(CarTransporter::new, MobCategory.MISC));
    public static final Supplier<EntityType<Tender>> TENDER = register("tender",
            EntityType.Builder.<Tender>of(Tender::new, MobCategory.MISC));
    public static final Supplier<EntityType<TankWagon>> TANK_WAGON = register("tank_wagon",
            EntityType.Builder.<TankWagon>of(TankWagon::new, MobCategory.MISC));
    public static final Supplier<EntityType<GuardsVan>> GUARDS_VAN = register("guards_van",
            EntityType.Builder.<GuardsVan>of(GuardsVan::new, MobCategory.MISC));
    public static final Supplier<EntityType<LivestockWagon>> LIVESTOCK_WAGON = register("livestock_wagon",
            EntityType.Builder.<LivestockWagon>of(LivestockWagon::new, MobCategory.MISC));
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
