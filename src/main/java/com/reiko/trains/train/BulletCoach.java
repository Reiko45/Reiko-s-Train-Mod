package com.reiko.trains.train;

import com.reiko.trains.registry.ModItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * A coach for the Bullet Train, to go between its two driving cars: eight blocks long, white with a blue stripe, with
 * twelve seats, two either side of the aisle in six rows, and a gangway at each end.
 */
public class BulletCoach extends TrainCarEntity {
    private static final double SEAT_SIDE = 7.5 / 16;
    private static final double SEAT_UP = 1.25;
    private static final int[] ROWS = {-45, -27, -9, 9, 27, 45};
    private static final Seat[] SEATS = rows();

    public BulletCoach(EntityType<? extends BulletCoach> type, Level level) {
        super(type, level);
    }

    private static Seat[] rows() {
        Seat[] seats = new Seat[ROWS.length * 2];
        for (int i = 0; i < ROWS.length; i++) {
            for (int side = 0; side < 2; side++) {
                seats[i * 2 + side] = new Seat(-(ROWS[i] + 1) / 16.0, (side == 0 ? -1 : 1) * SEAT_SIDE, SEAT_UP);
            }
        }
        return seats;
    }

    @Override
    public double halfLength() {
        return 4.0;
    }

    @Override
    public double bogieOffset() {
        return 2.75;
    }

    @Override
    public double mass() {
        return 1.6;
    }

    @Override
    protected float partHeight() {
        return 2.9F;
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
        return ModItems.BULLET_COACH.get();
    }
}
