package com.reiko.trains.advancement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Optional;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * The mod's one advancement trigger, {@code reikotrains:train}: something happened to a player on the railway. Its
 * conditions name the {@code event} (see {@link TrainAdvancements}) and, for the ones that count something (km/h,
 * pieces of track laid), the {@code min} it has to reach.
 */
public final class TrainTrigger extends SimpleCriterionTrigger<TrainTrigger.Instance> {
    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    /** {@code player} did {@code event}, {@code value} of it (1 for the ones that don't count). */
    public void trigger(ServerPlayer player, String event, double value) {
        trigger(player, instance -> instance.matches(event, value));
    }

    public record Instance(Optional<ContextAwarePredicate> player, String event, double min) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                Codec.STRING.fieldOf("event").forGetter(Instance::event),
                Codec.DOUBLE.optionalFieldOf("min", 0.0).forGetter(Instance::min)
        ).apply(i, Instance::new));

        public boolean matches(String happened, double value) {
            return event.equals(happened) && value >= min;
        }
    }
}
