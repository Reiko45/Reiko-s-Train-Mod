package com.reiko.trains.client;

import com.reiko.trains.train.SteamLocomotive;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;

/**
 * The cab gauges, bottom right while you drive a locomotive: the throttle's notch (reverse, neutral, 1-4), the
 * speed, the brake light, which way you'll go at the next junction and how much fuel is in the firebox. Drawn in
 * code, so it needs no textures.
 */
public final class TrainHud implements LayeredDraw.Layer {
    private static final int WIDTH = 118;
    private static final int HEIGHT = 46;
    /** The extra row for the next signal and station (or the timetable). */
    private static final int INFO = 24;
    private static final int MARGIN = 6;
    private static final String[] NOTCHES = {"R2", "R1", "N", "1", "2", "3", "4"};

    @Override
    public void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.player == null
                || !(minecraft.player.getVehicle() instanceof SteamLocomotive locomotive) || locomotive.driver() != minecraft.player) {
            return;
        }
        Font font = minecraft.font;
        Component status = locomotive.status();
        int aspect = locomotive.signalAspect();
        boolean info = aspect != 0 || !status.getString().isEmpty() || locomotive.isAutomatic();
        int height = HEIGHT + (info ? INFO : 0);
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
            if (locomotive.isAutomatic()) {
                Component auto = Component.translatable("hud.reikotrains.auto");
                int aw = font.width(auto) + 6;
                graphics.fill(x + WIDTH - aw - 4, sy - 1, x + WIDTH - 4, sy + 9, 0xFF3070D0);
                graphics.drawString(font, auto, x + WIDTH - aw - 1, sy, 0xFFFFFFFF, false);
            }
            // The next station, or what the timetable's doing
            String line = font.plainSubstrByWidth(status.getString(), WIDTH - 10);
            graphics.drawString(font, line, x + 5, sy + 11, 0xFFFFE9A8, false);
        }
        int y = top + (info ? INFO : 0);

        // Throttle: one box per notch, the current one lit
        int throttle = locomotive.getThrottle();
        int boxW = 15;
        for (int i = 0; i < NOTCHES.length; i++) {
            int notch = i + SteamLocomotive.MIN_THROTTLE;
            int bx = x + 4 + i * (boxW + 1);
            int by = y + 4;
            boolean lit = notch == throttle && !locomotive.isAutomatic();
            int color = !lit ? 0x40FFFFFF : notch < 0 ? 0xFFF08A24 : notch == 0 ? 0xFFB8B8B8 : 0xFF4CC266;
            graphics.fill(bx, by, bx + boxW, by + 11, color);
            String label = NOTCHES[i];
            graphics.drawString(font, label, bx + (boxW - font.width(label)) / 2 + 1, by + 2, lit ? 0xFF101010 : 0xFFD0D0D0, false);
        }

        // Speed, junction steering and brake
        String speed = Math.round(locomotive.speedKmh()) + " km/h";
        graphics.drawString(font, speed, x + 5, y + 19, 0xFFFFFFFF, true);
        int steer = locomotive.getSteer();
        if (steer != 0) {
            String arrow = steer < 0 ? "\u25C0" : "\u25B6";
            graphics.drawString(font, arrow, x + 58, y + 19, 0xFF4CC266, true);
        }
        if (locomotive.isBraking()) {
            Component brake = Component.translatable("hud.reikotrains.brake");
            int bw = font.width(brake) + 6;
            graphics.fill(x + WIDTH - bw - 4, y + 18, x + WIDTH - 4, y + 29, 0xFFD23C32);
            graphics.drawString(font, brake, x + WIDTH - bw - 1, y + 20, 0xFFFFFFFF, false);
        }

        // Fuel
        int fuel = locomotive.getFuelPercent();
        int barX = x + 30;
        int barY = y + 34;
        int barW = WIDTH - 34;
        graphics.drawString(font, Component.translatable("hud.reikotrains.fuel"), x + 5, y + 33, 0xFFD0D0D0, false);
        graphics.fill(barX, barY, barX + barW, barY + 6, 0xFF2A2A2E);
        if (fuel > 0) {
            int fill = Math.max(1, barW * fuel / 100);
            graphics.fill(barX, barY, barX + fill, barY + 6, fuel <= 15 ? 0xFFE0482E : 0xFFF0A030);
        } else if (!locomotive.isSteaming() && (minecraft.player.tickCount / 10) % 2 == 0) {
            Component out = Component.translatable("hud.reikotrains.no_fire");
            graphics.drawString(font, out, barX + (barW - font.width(out)) / 2, barY - 1, 0xFFFF5040, false);
        }
    }
}
