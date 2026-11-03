package com.reiko.trains.client;

import com.reiko.trains.train.Locomotive;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

/**
 * The cab gauges, bottom right while you drive a locomotive: the throttle's notch (reverse, neutral, 1-4), the
 * speed and the speed limit, the brake light, which way you'll go at the next junction and how much fuel is in the
 * firebox. Above them,
 * when there's anything to show: the next signal, the next switch and which way it's set, and the next station (or
 * what the timetable's doing). Drawn in code, so it needs no textures.
 */
public final class TrainHud implements LayeredDraw.Layer {
    private static final int WIDTH = 118;
    private static final int HEIGHT = 46;
    /** The extra row for the next signal and station (or the timetable). */
    private static final int INFO = 24;
    private static final int MARGIN = 6;
    /** The extra row for a steam engine's water. */
    private static final int WATER_ROW = 11;
    private static final String[] NOTCHES = {"R2", "R1", "N", "1", "2", "3", "4"};

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null
                || !(minecraft.player.getVehicle() instanceof Locomotive locomotive) || locomotive.driver() != minecraft.player) {
            return;
        }
        Font font = minecraft.font;
        Component status = locomotive.status();
        int aspect = locomotive.signalAspect();
        boolean points = locomotive.hasPointsAhead();
        boolean info = aspect != 0 || points || !status.getString().isEmpty() || locomotive.isAutomatic();
        int water = locomotive.waterPercent();
        int height = HEIGHT + (info ? INFO : 0) + (water >= 0 ? WATER_ROW : 0);
        int x = graphics.guiWidth() - WIDTH - MARGIN;
        int top = graphics.guiHeight() - height - MARGIN;
        graphics.fill(x, top, x + WIDTH, top + height, 0xA0101014);
        graphics.renderOutline(x, top, WIDTH, height, 0x60FFFFFF);
        if (info) {
            // The next signal: a lamp, red or green, and how far
            int sy = top + 4;
            if (aspect != 0) {
                int lamp = aspect == 1 ? 0xFF3CE05A : 0xFFFF3B30;
                graphics.fill(x + 5, sy + 1, x + 11, sy + 7, 0xFF000000);
                graphics.fill(x + 6, sy + 2, x + 10, sy + 6, lamp);
                graphics.drawString(font, Component.translatable("hud.reikotrains.signal", locomotive.signalDistance()), x + 14, sy, 0xFFD0D0D0, false);
            }
            if (points) {
                // The next switch: which way it's set (yellow if it turns off), and how far
                int turn = locomotive.pointsTurn();
                String distance = Component.translatable("hud.reikotrains.points", locomotive.pointsDistance()).getString();
                int dw = font.width(distance);
                int px = x + WIDTH - 5 - dw - 12;
                graphics.fill(px, sy - 1, px + 10, sy + 9, turn == 0 ? 0xFF2E8B45 : 0xFFD8A020);
                arrow(graphics, px + 1, sy, turn, 0xFFFFFFFF);
                graphics.drawString(font, distance, px + 12, sy, 0xFFD0D0D0, false);
            }
            // The next station, or what the timetable's doing
            String line = font.plainSubstrByWidth(status.getString(), WIDTH - 10);
            graphics.drawString(font, line, x + 5, sy + 11, 0xFFFFE9A8, false);
        }
        int y = top + (info ? INFO : 0);

        // Throttle: one box per notch, the current one lit (or, on a timetable, a bar: it's driving itself)
        int throttle = locomotive.getThrottle();
        int boxW = 15;
        if (locomotive.isAutomatic()) {
            Component auto = Component.translatable("hud.reikotrains.auto");
            graphics.fill(x + 4, y + 4, x + WIDTH - 4, y + 15, 0xFF3070D0);
            graphics.drawString(font, auto, x + (WIDTH - font.width(auto)) / 2, y + 6, 0xFFFFFFFF, false);
        }
        for (int i = 0; i < NOTCHES.length && !locomotive.isAutomatic(); i++) {
            int notch = i + Locomotive.MIN_THROTTLE;
            int bx = x + 4 + i * (boxW + 1);
            int by = y + 4;
            boolean lit = notch == throttle;
            int color = !lit ? 0x40FFFFFF : notch < 0 ? 0xFFF08A24 : notch == 0 ? 0xFFB8B8B8 : 0xFF4CC266;
            graphics.fill(bx, by, bx + boxW, by + 11, color);
            String label = NOTCHES[i];
            graphics.drawString(font, label, bx + (boxW - font.width(label)) / 2 + 1, by + 2, lit ? 0xFF101010 : 0xFFD0D0D0, false);
        }

        // Speed, junction steering and brake
        String speed = Math.round(locomotive.speedKmh()) + " km/h";
        graphics.drawString(font, speed, x + 5, y + 19, 0xFFFFFFFF, true);
        int after = Math.max(x + 46, x + 9 + font.width(speed));
        int limit = locomotive.speedLimitKmh();
        if (limit > 0) {
            // The speed limit, like the sign: red-rimmed, or yellow while slowing for a lower one ahead
            String number = String.valueOf(limit);
            int bw = font.width(number) + 9;
            boolean ahead = locomotive.isSlowingForLimit();
            graphics.fill(after, y + 17, after + bw, y + 30, 0xFFD02828);
            graphics.fill(after + 2, y + 19, after + bw - 2, y + 28, ahead ? 0xFFF4C430 : 0xFFFFFFFF);
            graphics.drawString(font, number, after + (bw - font.width(number)) / 2 + 1, y + 20, 0xFF101010, false);
            after += bw + 4;
        }
        int steer = locomotive.getSteer();
        if (steer != 0) {
            String arrow = steer < 0 ? "\u25C0" : "\u25B6";
            graphics.drawString(font, arrow, after + (limit > 0 ? 0 : 12), y + 19, 0xFF4CC266, true);
        }
        if (locomotive.isBraking()) {
            Component brake = Component.translatable("hud.reikotrains.brake");
            int bw = font.width(brake) + 6;
            graphics.fill(x + WIDTH - bw - 4, y + 18, x + WIDTH - 4, y + 29, 0xFFD23C32);
            graphics.drawString(font, brake, x + WIDTH - bw - 1, y + 20, 0xFFFFFFFF, false);
        }

        // Fuel
        int fuel = locomotive.getFuelPercent();
        Component label = Component.translatable(locomotive.fuelLabel());
        int labels = Math.max(font.width(label), water >= 0 ? font.width(Component.translatable("hud.reikotrains.water")) : 0);
        int barX = x + Math.max(30, 10 + labels);
        int barY = y + 34;
        int barW = WIDTH - 4 - (barX - x);
        graphics.drawString(font, label, x + 5, y + 33, 0xFFD0D0D0, false);
        graphics.fill(barX, barY, barX + barW, barY + 6, 0xFF2A2A2E);
        if (fuel > 0) {
            int fill = Math.max(1, barW * fuel / 100);
            graphics.fill(barX, barY, barX + fill, barY + 6, fuel <= 15 ? 0xFFE0482E : 0xFFF0A030);
        } else if (!locomotive.isRunning() && (minecraft.player.tickCount / 10) % 2 == 0) {
            Component out = Component.translatable(fuel < 0 ? "hud.reikotrains.no_tender" : locomotive.outOfFuelLabel());
            graphics.drawString(font, out, barX + (barW - font.width(out)) / 2, barY - 1, 0xFFFF5040, false);
        }

        // Water, for a steam engine
        if (water >= 0) {
            Component waterLabel = Component.translatable("hud.reikotrains.water");
            int wy = barY + WATER_ROW;
            graphics.drawString(font, waterLabel, x + 5, wy - 1, 0xFFD0D0D0, false);
            graphics.fill(barX, wy, barX + barW, wy + 6, 0xFF2A2A2E);
            if (water > 0) {
                int fill = Math.max(1, barW * water / 100);
                graphics.fill(barX, wy, barX + fill, wy + 6, water <= 15 ? 0xFFE0482E : 0xFF3C8CE0);
            } else if ((minecraft.player.tickCount / 10) % 2 == 0) {
                Component out = Component.translatable("hud.reikotrains.no_water");
                graphics.drawString(font, out, barX + (barW - font.width(out)) / 2, wy - 1, 0xFFFF5040, false);
            }
        }
    }

    /** An 8 x 8 arrow at (x, y): pointing up for straight on, or left or right for a turn ({@code turn} -1 or +1). */
    private static void arrow(GuiGraphics graphics, int x, int y, int turn, int color) {
        for (int i = 0; i < 4; i++) {
            if (turn == 0) {
                graphics.fill(x + 3 - i, y + i, x + 5 + i, y + i + 1, color); // head
            } else {
                int column = turn > 0 ? x + 7 - i : x + i;
                graphics.fill(column, y + 3 - i, column + 1, y + 5 + i, color);
            }
        }
        if (turn == 0) {
            graphics.fill(x + 3, y + 4, x + 5, y + 8, color); // shaft
        } else {
            graphics.fill(turn > 0 ? x : x + 4, y + 3, turn > 0 ? x + 4 : x + 8, y + 5, color);
        }
    }
}
