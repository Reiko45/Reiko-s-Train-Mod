package com.reiko.trains.registry;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.train.FreightWagon;
import com.reiko.trains.train.PassengerCoach;
import com.reiko.trains.train.SteamLocomotive;
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
    public static final Supplier<EntityType<FreightWagon>> FREIGHT_WAGON = register("freight_wagon",
            EntityType.Builder.<FreightWagon>of(FreightWagon::new, MobCategory.MISC));

    private ModEntities() {
    }

    /** Train cars are minecarts underneath: the same hitbox (the model is longer), seen from a bit further away. */
    private static <T extends Entity> Supplier<EntityType<T>> register(String name, EntityType.Builder<T> builder) {
        return ENTITIES.register(name, () -> builder.sized(0.98F, 0.7F).clientTrackingRange(10).build(name));
    }
}
