package com.reiko.trains.client;

import com.reiko.trains.ReikoTrains;
import com.reiko.trains.config.TrainsClientConfig;
import com.reiko.trains.network.TrainMapPayload;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Trains on Xaero's World Map: a marker for each train (the trains the server sends for minimaps, see
 * {@link TrainMinimap}), drawn over the map screen where the train is, in its locomotive's colours, named when the
 * mouse is over it; click one for what it's doing (its kind and length, who's driving it or whether it's on its
 * timetable, how fast it's going, where it's off to). The map screen has no way in for another mod's markers, so this
 * reads where it's looking (its camera and zoom) from it by name, and keeps out of the way if that ever fails (a
 * version it doesn't know). Nothing here touches the map's own classes.
 */
@EventBusSubscriber(modid = ReikoTrains.MODID, value = Dist.CLIENT)
public final class TrainsWorldMap {
    static final boolean WORLD_MAP = ModList.get() != null && ModList.get().isLoaded("xaeroworldmap");
    private static final String SCREEN = "xaero.map.gui.GuiMap";
    /** A marker's half-width and half-height, in GUI pixels. */
    private static final int HALF_W = 8;
    private static final int HALF_H = 6;
    private static boolean broken;
    @Nullable
    private static Field cameraX;
    @Nullable
    private static Field cameraZ;
    @Nullable
    private static Field scale;
    @Nullable
    private static Field mapProcessor;
    /** The train clicked on, whose details are showing. */
    @Nullable
    private static UUID selected;

    private TrainsWorldMap() {
    }

    /** Where the map screen's looking: the block at the middle of the screen, and how many screen pixels to a block. */
    private record View(double x, double z, double pixelsPerBlock, int width, int height) {
        int screenX(Vec3 pos) {
            return (int) Math.round(width / 2.0 + (pos.x - x) * pixelsPerBlock);
        }

        int screenY(Vec3 pos) {
            return (int) Math.round(height / 2.0 + (pos.z - z) * pixelsPerBlock);
        }
    }

    private static boolean isMap(Screen screen) {
        return WORLD_MAP && !broken && screen != null && screen.getClass().getName().equals(SCREEN);
    }

    /** Where the map screen's looking, or null if it's showing another dimension (or can't be read). */
    @Nullable
    private static View view(Screen screen, Minecraft minecraft) {
        try {
            if (cameraX == null) {
                Class<?> type = screen.getClass();
                cameraX = field(type, "cameraX");
                cameraZ = field(type, "cameraZ");
                scale = field(type, "scale");
                mapProcessor = field(type, "mapProcessor");
            }
            if (minecraft.level == null || !minecraft.level.dimension().equals(shownDimension(screen))) {
                return null;
            }
            double guiScale = minecraft.getWindow().getGuiScale();
            return new View(cameraX.getDouble(screen), cameraZ.getDouble(screen), scale.getDouble(screen) / guiScale, screen.width, screen.height);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            broken = true;
            ReikoTrains.LOGGER.warn("Couldn't show trains on Xaero's World Map (a version this mod doesn't know?)", e);
            return null;
        }
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    /** The dimension the map's showing: one picked on the map, or the one it's in. */
    @Nullable
    private static Object shownDimension(Screen screen) throws ReflectiveOperationException {
        Object processor = mapProcessor.get(screen);
        if (processor == null) {
            return null;
        }
        Object world = processor.getClass().getMethod("getMapWorld").invoke(processor);
        if (world == null) {
            return null;
        }
        Object custom = world.getClass().getMethod("getCustomDimensionId").invoke(world);
        return custom != null ? custom : world.getClass().getMethod("getCurrentDimensionId").invoke(world);
    }

    private static List<TrainMinimap.Shown> trains(Minecraft minecraft) {
        return TrainsClientConfig.TRAINS_ON_MINIMAP.getAsBoolean() ? TrainMinimap.shown(minecraft) : List.of();
    }

    @SubscribeEvent
    static void onRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        if (!isMap(screen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        View view = view(screen, minecraft);
        if (view == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        TrainMinimap.Shown hovered = null;
        TrainMinimap.Shown chosen = null;
        for (TrainMinimap.Shown train : trains(minecraft)) {
            int x = view.screenX(train.pos());
            int y = view.screenY(train.pos());
            if (x < -HALF_W || y < -HALF_H || x > view.width() + HALF_W || y > view.height() + HALF_H) {
                continue;
            }
            boolean over = Math.abs(event.getMouseX() - x) <= HALF_W && Math.abs(event.getMouseY() - y) <= HALF_H;
            if (over) {
                hovered = train;
            }
            if (train.id().equals(selected)) {
                chosen = train;
            }
            int colour = colour(train.kind());
            boolean picked = train.id().equals(selected);
            graphics.fill(x - HALF_W - 1, y - HALF_H - 1, x + HALF_W + 1, y + HALF_H + 1, picked || over ? 0xFFFFFFFF : 0xFF101010);
            graphics.fill(x - HALF_W, y - HALF_H, x + HALF_W, y + HALF_H, colour);
            String initials = train.initials();
            graphics.drawString(font, initials, x - font.width(initials) / 2, y - 3, 0xFFFFFFFF, true);
        }
        if (chosen != null) {
            details(graphics, font, view, chosen);
        } else if (hovered != null) {
            graphics.renderTooltip(font, Component.literal(hovered.name()), event.getMouseX(), event.getMouseY());
        }
        graphics.pose().popPose();
    }

    /** A box beside the chosen train, saying what it's doing. */
    private static void details(GuiGraphics graphics, Font font, View view, TrainMinimap.Shown train) {
        TrainMapPayload.Pin pin = train.pin();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(TrainMinimap.name(pin)).withStyle(net.minecraft.ChatFormatting.WHITE));
        if (!pin.loaded()) {
            lines.add(Component.translatable("worldmap.reikotrains.far"));
        } else {
            if (!pin.driver().isEmpty()) {
                lines.add(Component.translatable("worldmap.reikotrains.driven_by", pin.driver()));
            } else if (pin.auto()) {
                lines.add(Component.translatable("worldmap.reikotrains.timetable"));
            } else {
                lines.add(Component.translatable("worldmap.reikotrains.no_driver"));
            }
            lines.add(pin.kmh() > 0 ? Component.translatable("worldmap.reikotrains.speed", pin.kmh())
                    : Component.translatable(pin.atStop() ? "worldmap.reikotrains.at_stop" : "worldmap.reikotrains.standing"));
        }
        if (!pin.stop().isEmpty()) {
            lines.add(Component.translatable(pin.atStop() ? "worldmap.reikotrains.at" : "worldmap.reikotrains.next", pin.stop()));
        }
        lines.add(Component.translatable("worldmap.reikotrains.where", (int) Math.floor(train.pos().x), (int) Math.floor(train.pos().y),
                (int) Math.floor(train.pos().z)));
        int width = 0;
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        int x = view.screenX(train.pos()) + HALF_W + 6;
        int y = view.screenY(train.pos()) - 6;
        int height = lines.size() * 10 + 6;
        if (x + width + 8 > view.width()) {
            x = view.screenX(train.pos()) - HALF_W - 6 - width - 8;
        }
        y = Math.max(2, Math.min(y, view.height() - height - 2));
        graphics.fill(x, y, x + width + 8, y + height, 0xE0101418);
        graphics.fill(x, y, x + 2, y + height, colour(train.kind()));
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(font, lines.get(i), x + 6, y + 4 + i * 10, i == 0 ? 0xFFFFFFFF : 0xFFC8D0D8, false);
        }
    }

    /** Clicking a train picks it (and shows what it's doing); clicking it again, or anywhere else, puts it down. */
    @SubscribeEvent
    static void onClick(ScreenEvent.MouseButtonPressed.Pre event) {
        Screen screen = event.getScreen();
        if (!isMap(screen) || event.getButton() != 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        View view = view(screen, minecraft);
        if (view == null) {
            return;
        }
        for (TrainMinimap.Shown train : trains(minecraft)) {
            int x = view.screenX(train.pos());
            int y = view.screenY(train.pos());
            if (Math.abs(event.getMouseX() - x) <= HALF_W && Math.abs(event.getMouseY() - y) <= HALF_H) {
                selected = train.id().equals(selected) ? null : train.id();
                event.setCanceled(true);
                return;
            }
        }
        selected = null;
    }

    /** The map closed: nothing picked next time. */
    @SubscribeEvent
    static void onClose(ScreenEvent.Closing event) {
        if (isMap(event.getScreen())) {
            selected = null;
        }
    }

    /** Opens on this train (for the showcase): picked, as if clicked. */
    public static void pick(@Nullable UUID train) {
        selected = train;
    }

    /** The locomotive's livery, as a marker colour. */
    static int colour(String kind) {
        return switch (kind) {
            case "steam_locomotive" -> 0xFF2C6840;
            case "express_locomotive" -> 0xFF8A2A20;
            case "diesel_locomotive" -> 0xFF2F5F9E;
            case "electric_locomotive" -> 0xFFB03A2E;
            case "bullet_train" -> 0xFF2E9AA8;
            case "vintage_tram" -> 0xFFB8862E;
            case "low_floor_tram" -> 0xFF4E8A3C;
            default -> 0xFF505860;
        };
    }
}
