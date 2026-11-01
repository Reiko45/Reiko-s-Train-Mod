package com.reiko.trains.train;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;

/**
 * What every train car keeps: which cars it's coupled to (saved with the car), and on the client, how far it has
 * rolled, for turning its wheels.
 */
public final class TrainCarState {
    /** The car in front, which this one follows. */
    @Nullable
    UUID leader;
    /** The car behind, which follows this one. */
    @Nullable
    UUID follower;
    @Nullable
    Entity leaderCache;
    @Nullable
    Entity followerCache;

    private double distance;
    private double distanceO;

    @Nullable
    public UUID leader() {
        return leader;
    }

    @Nullable
    public UUID follower() {
        return follower;
    }

    public boolean isCoupled() {
        return leader != null || follower != null;
    }

    /** Client: how far the car has rolled, forwards positive, for the wheels. */
    public float distance(float partialTick) {
        return (float) Mth.lerp(partialTick, distanceO, distance);
    }

    void roll(double blocks) {
        distanceO = distance;
        distance += blocks;
        if (Math.abs(distance) > 1.0E6) { // keep the float precise; the wheels look the same
            distance %= 1000.0;
            distanceO = distance - blocks;
        }
    }

    void save(CompoundTag tag) {
        if (leader != null) {
            tag.putUUID("CoupledFront", leader);
        }
        if (follower != null) {
            tag.putUUID("CoupledBack", follower);
        }
    }

    void load(CompoundTag tag) {
        leader = tag.hasUUID("CoupledFront") ? tag.getUUID("CoupledFront") : null;
        follower = tag.hasUUID("CoupledBack") ? tag.getUUID("CoupledBack") : null;
        leaderCache = null;
        followerCache = null;
    }
}
