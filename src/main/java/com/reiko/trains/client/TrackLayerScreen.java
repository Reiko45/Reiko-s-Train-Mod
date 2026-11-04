package com.reiko.trains.client;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.menu.TrackLayerMenu;
import com.reiko.trains.train.TrackLayer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The track-laying wagon's screen: its store, its on/off switch, what it's doing (or why it's stopped), and how it lays:
 * following the ground or level, and tight or wide bends.
 */
public class TrackLayerScreen extends AbstractContainerScreen<TrackLayerMenu> {
    private static final ResourceLocation BACKGROUND = ReikoTrains.id("textures/gui/track_layer.png");
    private Button toggle;
    private Button grade;
    private Button bends;

    public TrackLayerScreen(TrackLayerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 220;
        inventoryLabelY = imageHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        toggle = addRenderableWidget(Button.builder(Component.empty(), b -> press(TrackLayerMenu.BUTTON_SWITCH))
                .bounds(leftPos + 7, topPos + 77, 50, 20).build());
        grade = addRenderableWidget(Button.builder(Component.empty(), b -> press(TrackLayerMenu.BUTTON_GRADE))
                .bounds(leftPos + 7, topPos + 101, 80, 20).build());
        bends = addRenderableWidget(Button.builder(Component.empty(), b -> press(TrackLayerMenu.BUTTON_BENDS))
                .bounds(leftPos + 89, topPos + 101, 80, 20).build());
        HandbookButton handbook = HandbookButton.create(leftPos + imageWidth - 19, topPos + 3, 14, com.reiko.trains.guide.Handbook.TRACK_LAYER);
        if (handbook != null) {
            addRenderableWidget(handbook);
        }
        refresh();
    }

    private void press(int button) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
        }
    }

    private void refresh() {
        boolean on = menu.isOn();
        toggle.setMessage(Component.translatable(on ? "screen.reikotrains.track_layer.on" : "screen.reikotrains.track_layer.off")
                .withStyle(on ? ChatFormatting.GREEN : ChatFormatting.RED));
        toggle.setTooltip(Tooltip.create(Component.translatable(on ? "screen.reikotrains.track_layer.on.tooltip"
                : "screen.reikotrains.track_layer.off.tooltip")));
        String g = menu.followsGround() ? "follow" : "level";
        grade.setMessage(Component.translatable("screen.reikotrains.track_layer.grade." + g));
        grade.setTooltip(Tooltip.create(Component.translatable("screen.reikotrains.track_layer.grade." + g + ".tooltip")));
        String w = menu.wideBends() ? "wide" : "tight";
        bends.setMessage(Component.translatable("screen.reikotrains.track_layer.bends." + w));
        bends.setTooltip(Tooltip.create(Component.translatable("screen.reikotrains.track_layer.bends." + w + ".tooltip")));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        refresh();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        TrackLayer.Status status = menu.status();
        int color = switch (status) {
            case LAYING -> 0x7CFC6C;
            case READY -> 0xD8D8D8;
            case OFF -> 0x9A9A9A;
            default -> 0xFF8C5A;
        };
        graphics.drawString(font, Component.translatable("screen.reikotrains.track_layer.status." + status.key()), 66, 79, color, false);
        graphics.drawString(font, Component.translatable("screen.reikotrains.track_layer.supplies", menu.track(), menu.fill()), 66, 88, 0xB0B8B0,
                false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
