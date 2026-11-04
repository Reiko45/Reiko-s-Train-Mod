package com.reiko.trains.client;

import com.reiko.trains.network.OpenStationPayload;
import com.reiko.trains.network.StationSettingsPayload;
import com.reiko.trains.signal.StationBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/** A station's settings: its name (shown on the board) and how long trains on a timetable wait there. */
public class StationScreen extends Screen {
    private final BlockPos pos;
    private final String initialName;
    private int dwell;
    private EditBox name;
    private Button dwellLabel;

    public StationScreen(BlockPos pos, String name, int dwell) {
        super(Component.translatable("screen.reikotrains.station"));
        this.pos = pos;
        this.initialName = name;
        this.dwell = dwell;
    }

    public static void open(OpenStationPayload payload) {
        Minecraft.getInstance().setScreen(new StationScreen(payload.pos(), payload.name(), payload.dwellSeconds()));
    }

    @Override
    protected void init() {
        int x = width / 2 - 100;
        int y = height / 2 - 50;
        name = new EditBox(font, x, y + 14, 200, 20, Component.translatable("screen.reikotrains.station.name"));
        name.setMaxLength(StationBlockEntity.MAX_NAME);
        name.setValue(initialName);
        addRenderableWidget(name);
        setInitialFocus(name);
        addRenderableWidget(Button.builder(Component.literal("-"), b -> changeDwell(hasShiftDown() ? -10 : -1)).bounds(x, y + 54, 20, 20).build());
        dwellLabel = addRenderableWidget(Button.builder(dwellText(), b -> {
        }).bounds(x + 24, y + 54, 152, 20).build());
        dwellLabel.active = false;
        addRenderableWidget(Button.builder(Component.literal("+"), b -> changeDwell(hasShiftDown() ? 10 : 1)).bounds(x + 180, y + 54, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(x, y + 84, 200, 20).build());
        HandbookButton handbook = HandbookButton.create(x + 204, y + 14, 20, com.reiko.trains.guide.Handbook.STATIONS);
        if (handbook != null) {
            addRenderableWidget(handbook);
        }
    }

    private Component dwellText() {
        return Component.translatable("screen.reikotrains.station.dwell", dwell);
    }

    private void changeDwell(int by) {
        dwell = Mth.clamp(dwell + by, StationBlockEntity.MIN_DWELL, StationBlockEntity.MAX_DWELL);
        dwellLabel.setMessage(dwellText());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int x = width / 2 - 100;
        int y = height / 2 - 50;
        graphics.drawCenteredString(font, title, width / 2, y - 16, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.reikotrains.station.name"), x, y + 2, 0xA0A0A0);
        graphics.drawString(font, Component.translatable("screen.reikotrains.station.wait"), x, y + 42, 0xA0A0A0);
    }

    @Override
    public void onClose() {
        PacketDistributor.sendToServer(new StationSettingsPayload(pos, name.getValue(), dwell));
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 257 || keyCode == 335) { // enter
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
