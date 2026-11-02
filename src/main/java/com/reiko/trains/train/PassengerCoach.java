package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** A coach with eight seats, two either side of the aisle in four rows, all facing forwards. */
public class PassengerCoach extends TrainCarEntity {
    private static final double SEAT_SIDE = 7.5 / 16;
    private static final double SEAT_UP = 1.25;
    private static final Seat[] SEATS = {
            seat(-30, -1), seat(-30, 1), seat(-10, -1), seat(-10, 1), seat(10, -1), seat(10, 1), seat(30, -1), seat(30, 1)};

    public PassengerCoach(EntityType<? extends PassengerCoach> type, Level level) {
        super(type, level);
    }

    /** A seat on the bench whose cushion is centred {@code x} pixels along the model (front is negative). */
    private static Seat seat(int x, int side) {
        return new Seat(-(x + 1) / 16.0, side * SEAT_SIDE, SEAT_UP);
    }

    @Override
    public double halfLength() {
        return 3.25;
    }

    @Override
    public double bogieOffset() {
        return 2.25;
    }

    @Override
    public double mass() {
        return 1.5;
    }

    @Override
    protected float partHeight() {
        return 3.0F;
    }

    @Override
    public Seat[] seats() {
        return SEATS;
    }

    @Override
    protected InteractionResult interactCar(Player player, InteractionHand hand) {
        return board(player);
    }

    @Override
    protected Item getDropItem() {
        return ModItems.PASSENGER_COACH.get();
    }
}
