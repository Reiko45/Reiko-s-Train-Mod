package com.reiko.trains.advancement;

import com.reiko.trains.ReikoTrains;
import javax.annotation.Nullable;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The mod's advancements (in {@code data/reikotrains/advancement}, written by {@code tools/advancements}): what happens
 * on the railway, told to the {@link TrainTrigger} as named events. Server side.
 *
 * <p>The events: {@code place_car} (a train car put on the track), {@code drive} (opening the throttle), {@code tram}
 * (driving a tram), {@code speed} (km/h reached, driving), {@code wire} (driving an electric drawing from overhead
 * wire), {@code couple}, {@code car_aboard} (driving a car aboard a car transporter), {@code timetable} (a train set
 * going on one), {@code route} (one set from a signal box), {@code turntable} (a train turned on one, by a click),
 * {@code shed} (an engine shed topping up the engine you're driving), {@code lay} (pieces of track a track-laying
 * wagon's laid, driving it) and {@code bridge} (the track-laying wagon you're driving building a bridge).
 */
public final class TrainAdvancements {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS = DeferredRegister.create(Registries.TRIGGER_TYPE, ReikoTrains.MODID);
    public static final DeferredHolder<CriterionTrigger<?>, TrainTrigger> TRAIN = TRIGGERS.register("train", TrainTrigger::new);

    private TrainAdvancements() {
    }

    /** {@code player} did {@code event} (any entity will do: only players get advancements). */
    public static void award(@Nullable Entity player, String event) {
        reach(player, event, 1.0);
    }

    /** {@code player} has got to {@code value} of {@code event} (km/h, track laid...). */
    public static void reach(@Nullable Entity player, String event, double value) {
        if (player instanceof ServerPlayer server && !server.isRemoved()) {
            TRAIN.get().trigger(server, event, value);
        }
    }
}
