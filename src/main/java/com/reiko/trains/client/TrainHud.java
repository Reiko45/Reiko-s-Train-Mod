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
 * speed, the brake light and how much fuel is in the firebox. Drawn in code, so it needs no textures.
 */
public final class TrainHud implements LayeredDraw.Layer {
    private static final int WIDTH = 118;
    private static final int HEIGHT = 46;
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
        int x = graphics.guiWidth() - WIDTH - MARGIN;
        int y = graphics.guiHeight() - HEIGHT - MARGIN;
        graphics.fill(x, y, x + WIDTH, y + HEIGHT, 0xA0101014);
        graphics.renderOutline(x, y, WIDTH, HEIGHT, 0x60FFFFFF);

        // Throttle: one box per notch, the current one lit
        int throttle = locomotive.getThrottle();
        int boxW = 15;
        for (int i = 0; i < NOTCHES.length; i++) {
            int notch = i + SteamLocomotive.MIN_THROTTLE;
            int bx = x + 4 + i * (boxW + 1);
            int by = y + 4;
            boolean lit = notch == throttle;
            int color = !lit ? 0x40FFFFFF : notch < 0 ? 0xFFF08A24 : notch == 0 ? 0xFFB8B8B8 : 0xFF4CC266;
            graphics.fill(bx, by, bx + boxW, by + 11, color);
            String label = NOTCHES[i];
            graphics.drawString(font, label, bx + (boxW - font.width(label)) / 2 + 1, by + 2, lit ? 0xFF101010 : 0xFFD0D0D0, false);
        }

        // Speed and brake
        String speed = Math.round(locomotive.getSpeedKmh()) + " km/h";
        graphics.drawString(font, speed, x + 5, y + 19, 0xFFFFFFFF, true);
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
