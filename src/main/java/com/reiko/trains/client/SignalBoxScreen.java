package com.reiko.trains.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reiko.trains.menu.SignalBoxMenu;
import com.reiko.trains.network.SignalBoxActionPayload;
import com.reiko.trains.network.SignalBoxMapPayload;
import com.reiko.trains.network.SignalBoxStatePayload;
import com.reiko.trains.signal.SignalBoxBlockEntity;
import com.reiko.trains.train.Locomotive;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

/**
 * A signal box's diagram: a live map of the railway round the box, north up. The track in its real shape, light grey;
 * turntables as circles with their bridges; each switch's junction with the way it's set picked out in yellow; each
 * signal as a lamp beside its track (red, yellow or green, with a white ring if the box is holding it) and a tick
 * showing which way it guards; the stations' names; and the trains, car by car, locomotives orange. Hover over things to
 * see what they are; click a switch to throw it, or a signal to hold it at red or let it go. Drag to move the map
 * about, scroll to zoom.
 */
public class SignalBoxScreen extends AbstractContainerScreen<SignalBoxMenu> {
    private static final int BOARD = 0xFF14251B;
    private static final int GRID = 0x1AFFFFFF;
    private static final int TRACK = 0xFFB9C2CB;
    private static final int SET_WAY = 0xFFFFD23C;
    private static final int LOCO = 0xFFFF8C1A;
    private static final int CAR = 0xFF5DADE2;
    private static final int[] LAMPS = {0xFFFF3B30, 0xFF3CE05A, 0xFFFFC21E};
    private static final float MIN_SCALE = 0.6F;
    private static final float MAX_SCALE = 12F;

    /** The last map and state the server sent (and the state before, and when the last came), for the box on screen. */
    @Nullable
    private static SignalBoxMapPayload map;
    @Nullable
    private static SignalBoxStatePayload state;
    @Nullable
    private static SignalBoxStatePayload before;
    private static long stateAt;

    private float scale = 1;
    private float panX;
    private float panZ;
    private boolean dragging;
    private int mapLeft;
    private int mapTop;
    private int mapRight;
    private int mapBottom;

    public SignalBoxScreen(SignalBoxMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    public static void receive(SignalBoxMapPayload payload) {
        map = payload;
    }

    public static void receive(SignalBoxStatePayload payload) {
        before = state != null && state.box().equals(payload.box()) ? state : payload;
        state = payload;
        stateAt = Util.getMillis();
    }

    @Override
    protected void init() {
        imageWidth = Math.min(width - 12, 460);
        imageHeight = Math.min(height - 12, 280);
        super.init();
        mapLeft = leftPos + 6;
        mapTop = topPos + 18;
        mapRight = leftPos + imageWidth - 6;
        mapBottom = topPos + imageHeight - 16;
        scale = Mth.clamp(Math.min(mapRight - mapLeft, mapBottom - mapTop) / (2.0F * SignalBoxBlockEntity.RANGE) * 1.6F, MIN_SCALE, MAX_SCALE);
        panX = 0;
        panZ = 0;
    }

    private float sx(float x) {
        return (mapLeft + mapRight) / 2.0F + (x - panX) * scale;
    }

    private float sy(float z) {
        return (mapTop + mapBottom) / 2.0F + (z - panZ) * scale;
    }

    private boolean onMap(double mouseX, double mouseY) {
        return mouseX >= mapLeft && mouseX < mapRight && mouseY >= mapTop && mouseY < mapBottom;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Drawing

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF3A3530);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF6E6252);
        graphics.fill(mapLeft, mapTop, mapRight, mapBottom, BOARD);
        graphics.flush();
        graphics.enableScissor(mapLeft, mapTop, mapRight, mapBottom);
        Matrix4f pose = graphics.pose().last().pose();
        VertexConsumer vc = graphics.bufferSource().getBuffer(RenderType.gui());
        boolean current = map != null && map.box().equals(menu.pos());
        // A faint line every chunk, to judge distances by.
        if (scale >= 1.2F) {
            float left = panX - (mapRight - mapLeft) / 2.0F / scale;
            float top = panZ - (mapBottom - mapTop) / 2.0F / scale;
            int bx = menu.pos().getX();
            int bz = menu.pos().getZ();
            for (int gx = Mth.floor((left + bx) / 16) * 16; gx - bx < left + (mapRight - mapLeft) / scale; gx += 16) {
                float x = sx(gx - bx - 0.5F);
                quad(vc, pose, x, mapTop, x + 1, mapTop, x + 1, mapBottom, x, mapBottom, GRID);
            }
            for (int gz = Mth.floor((top + bz) / 16) * 16; gz - bz < top + (mapBottom - mapTop) / scale; gz += 16) {
                float y = sy(gz - bz - 0.5F);
                quad(vc, pose, mapLeft, y, mapRight, y, mapRight, y + 1, mapLeft, y + 1, GRID);
            }
        }
        float width = Mth.clamp(scale * 0.55F, 1.3F, 4.5F);
        SignalBoxStatePayload now = state != null && state.box().equals(menu.pos()) ? state : null;
        if (now != null) {
            for (SignalBoxStatePayload.TableMark table : now.tables()) {
                circle(vc, pose, table.x(), table.z(), 7.5F, 1.2F, 0xFF707A84);
                double a = Math.toRadians(table.angle());
                float dx = (float) Math.cos(a) * 7.4F;
                float dz = (float) Math.sin(a) * 7.4F;
                line(vc, pose, sx(table.x() - dx), sy(table.z() - dz), sx(table.x() + dx), sy(table.z() + dz), width, TRACK);
            }
        }
        if (current) {
            for (float[] points : map.lines()) {
                for (int i = 0; i + 3 < points.length; i += 2) {
                    line(vc, pose, sx(points[i]), sy(points[i + 1]), sx(points[i + 2]), sy(points[i + 3]), width, TRACK);
                }
            }
        }
        if (now != null) {
            for (SignalBoxStatePayload.SwitchMark stand : now.switches()) {
                int n = SignalBoxStatePayload.WAY_POINTS;
                if (stand.set() >= 0 && (stand.set() + 1) * n * 2 <= stand.ways().length) {
                    float px = stand.x();
                    float pz = stand.z();
                    for (int j = 0; j < n; j++) {
                        int k = 2 * (stand.set() * n + j);
                        line(vc, pose, sx(px), sy(pz), sx(stand.ways()[k]), sy(stand.ways()[k + 1]), width + 0.6F, SET_WAY);
                        px = stand.ways()[k];
                        pz = stand.ways()[k + 1];
                    }
                }
                float r = Math.max(2.0F, width);
                box(vc, pose, sx(stand.x()), sy(stand.z()), r, stand.locked() ? 0xFF8A8A8A : SET_WAY);
            }
            for (SignalBoxStatePayload.SignalMark signal : now.signals()) {
                float lx = sx(signal.x());
                float ly = sy(signal.z());
                line(vc, pose, sx(signal.nodeX()), sy(signal.nodeZ()), lx, ly, 1.0F, 0xFF55606A);
                float r = Mth.clamp(scale * 0.9F, 2.5F, 5.0F);
                line(vc, pose, lx, ly, lx + signal.dx() * (r + 4), ly + signal.dz() * (r + 4), 1.2F, 0xFFE0E0E0);
                if (signal.held()) {
                    box(vc, pose, lx, ly, r + 1.5F, 0xFFFFFFFF);
                }
                box(vc, pose, lx, ly, r + 0.5F, 0xFF101010);
                box(vc, pose, lx, ly, r - 0.5F, LAMPS[Mth.clamp(signal.aspect(), 0, 2)]);
            }
            float t = Mth.clamp((Util.getMillis() - stateAt) / 200.0F, 0, 1);
            Map<Integer, SignalBoxStatePayload.CarMark> last = new HashMap<>();
            if (before != null) {
                for (SignalBoxStatePayload.CarMark car : before.cars()) {
                    last.put(car.id(), car);
                }
            }
            for (SignalBoxStatePayload.CarMark car : now.cars()) {
                SignalBoxStatePayload.CarMark was = last.getOrDefault(car.id(), car);
                float x = Mth.lerp(t, was.x(), car.x());
                float z = Mth.lerp(t, was.z(), car.z());
                float half = car.half() - 0.15F;
                float fx = car.fx() * half;
                float fz = car.fz() * half;
                float ax = -car.fz() * 0.8F;
                float az = car.fx() * 0.8F;
                quad(vc, pose, sx(x + fx + ax), sy(z + fz + az), sx(x + fx - ax), sy(z + fz - az), sx(x - fx - ax), sy(z - fz - az),
                        sx(x - fx + ax), sy(z - fz + az), car.loco() ? LOCO : CAR);
            }
        }
        // The box itself.
        box(vc, pose, sx(0), sy(0), 2.5F, 0xFFFFFFFF);
        box(vc, pose, sx(0), sy(0), 1.5F, 0xFF3A3530);
        graphics.flush();
        if (current) {
            for (SignalBoxMapPayload.Label station : map.stations()) {
                int x = Math.round(sx(station.x()));
                int y = Math.round(sy(station.z()));
                graphics.fill(x - 1, y - 1, x + 2, y + 2, 0xFFE8E0C0);
                graphics.drawString(font, station.name(), x + 4, y - 9, 0xFFE8E0C0, false);
            }
        }
        if (map == null) {
            graphics.drawCenteredString(font, Component.translatable("screen.reikotrains.signal_box.surveying"), (mapLeft + mapRight) / 2,
                    (mapTop + mapBottom) / 2, 0xFFA0A0A0);
        }
        graphics.flush();
        graphics.disableScissor();
        graphics.drawString(font, Component.translatable("screen.reikotrains.signal_box.help"), mapLeft, mapBottom + 4, 0xFFB0A898, false);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 6, 6, 0xFFE8E0C0, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        List<Component> tip = tooltip(mouseX, mouseY);
        if (!tip.isEmpty()) {
            graphics.renderComponentTooltip(font, tip, mouseX, mouseY);
        }
    }

    /** A filled square, {@code r} out from its middle each way. */
    private static void box(VertexConsumer vc, Matrix4f pose, float x, float y, float r, int color) {
        quad(vc, pose, x - r, y - r, x - r, y + r, x + r, y + r, x + r, y - r, color);
    }

    /** A line {@code w} wide. */
    private static void line(VertexConsumer vc, Matrix4f pose, float x0, float y0, float x1, float y1, float w, int color) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float length = Mth.sqrt(dx * dx + dy * dy);
        if (length < 1.0E-3F) {
            return;
        }
        float nx = -dy / length * w / 2;
        float ny = dx / length * w / 2;
        quad(vc, pose, x0 + nx, y0 + ny, x0 - nx, y0 - ny, x1 - nx, y1 - ny, x1 + nx, y1 + ny, color);
    }

    private void circle(VertexConsumer vc, Matrix4f pose, float x, float z, float radius, float w, int color) {
        int pieces = 40;
        for (int i = 0; i < pieces; i++) {
            double a0 = 2 * Math.PI * i / pieces;
            double a1 = 2 * Math.PI * (i + 1) / pieces;
            line(vc, pose, sx(x + (float) Math.cos(a0) * radius), sy(z + (float) Math.sin(a0) * radius),
                    sx(x + (float) Math.cos(a1) * radius), sy(z + (float) Math.sin(a1) * radius), w, color);
        }
    }

    /** A four-cornered shape, wound the way the GUI draws (whichever way round the corners come). */
    private static void quad(VertexConsumer vc, Matrix4f pose, float ax, float ay, float bx, float by, float cx, float cy, float dx, float dy,
                             int color) {
        float turn = (bx - ax) * (cy - by) - (by - ay) * (cx - bx);
        if (turn > 0) {
            vc.addVertex(pose, ax, ay, 0).setColor(color);
            vc.addVertex(pose, dx, dy, 0).setColor(color);
            vc.addVertex(pose, cx, cy, 0).setColor(color);
            vc.addVertex(pose, bx, by, 0).setColor(color);
        } else {
            vc.addVertex(pose, ax, ay, 0).setColor(color);
            vc.addVertex(pose, bx, by, 0).setColor(color);
            vc.addVertex(pose, cx, cy, 0).setColor(color);
            vc.addVertex(pose, dx, dy, 0).setColor(color);
        }
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Pointing at things

    @Nullable
    private SignalBoxStatePayload.SignalMark signalAt(double mouseX, double mouseY) {
        if (state == null || !state.box().equals(menu.pos()) || !onMap(mouseX, mouseY)) {
            return null;
        }
        SignalBoxStatePayload.SignalMark best = null;
        double bestDistance = 6;
        for (SignalBoxStatePayload.SignalMark signal : state.signals()) {
            double d = Math.hypot(sx(signal.x()) - mouseX, sy(signal.z()) - mouseY);
            if (d < bestDistance) {
                best = signal;
                bestDistance = d;
            }
        }
        return best;
    }

    @Nullable
    private SignalBoxStatePayload.SwitchMark switchAt(double mouseX, double mouseY) {
        if (state == null || !state.box().equals(menu.pos()) || !onMap(mouseX, mouseY)) {
            return null;
        }
        SignalBoxStatePayload.SwitchMark best = null;
        double bestDistance = 6;
        for (SignalBoxStatePayload.SwitchMark stand : state.switches()) {
            double d = Math.hypot(sx(stand.x()) - mouseX, sy(stand.z()) - mouseY);
            if (d < bestDistance) {
                best = stand;
                bestDistance = d;
            }
        }
        return best;
    }

    @Nullable
    private SignalBoxStatePayload.CarMark carAt(double mouseX, double mouseY) {
        if (state == null || !state.box().equals(menu.pos()) || !onMap(mouseX, mouseY)) {
            return null;
        }
        for (SignalBoxStatePayload.CarMark car : state.cars()) {
            // Within the car's outline, near enough.
            double x = (mouseX - sx(car.x())) / scale;
            double z = (mouseY - sy(car.z())) / scale;
            double along = x * car.fx() + z * car.fz();
            double across = -x * car.fz() + z * car.fx();
            if (Math.abs(along) <= car.half() + 2 / scale && Math.abs(across) <= 0.8 + 2 / scale) {
                return car;
            }
        }
        return null;
    }

    private List<Component> tooltip(double mouseX, double mouseY) {
        List<Component> lines = new ArrayList<>();
        SignalBoxStatePayload.SignalMark signal = signalAt(mouseX, mouseY);
        if (signal != null) {
            String aspect = signal.held() ? "held" : signal.aspect() == SignalBoxStatePayload.GREEN ? "green"
                    : signal.aspect() == SignalBoxStatePayload.YELLOW ? "yellow" : "red";
            lines.add(Component.translatable("screen.reikotrains.signal_box.signal." + aspect));
            lines.add(Component.translatable(signal.held() ? "screen.reikotrains.signal_box.release" : "screen.reikotrains.signal_box.hold")
                    .withStyle(ChatFormatting.GRAY));
            return lines;
        }
        SignalBoxStatePayload.SwitchMark stand = switchAt(mouseX, mouseY);
        if (stand != null) {
            lines.add(Component.translatable("screen.reikotrains.signal_box.switch", stand.set() + 1,
                    stand.ways().length / 2 / SignalBoxStatePayload.WAY_POINTS));
            lines.add(stand.locked() ? Component.translatable("screen.reikotrains.signal_box.locked").withStyle(ChatFormatting.RED)
                    : Component.translatable("screen.reikotrains.signal_box.throw").withStyle(ChatFormatting.GRAY));
            return lines;
        }
        SignalBoxStatePayload.CarMark car = carAt(mouseX, mouseY);
        if (car != null) {
            Entity entity = minecraft == null || minecraft.level == null ? null : minecraft.level.getEntity(car.id());
            if (entity != null) {
                lines.add(entity.getName());
                if (entity instanceof Locomotive loco && !loco.status().getString().isEmpty()) {
                    lines.add(loco.status().copy().withStyle(ChatFormatting.GRAY));
                }
            } else {
                lines.add(Component.translatable("screen.reikotrains.signal_box.car"));
            }
        }
        return lines;
    }

    // ----------------------------------------------------------------------------------------------------------------
    // Mouse

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && onMap(mouseX, mouseY)) {
            SignalBoxStatePayload.SignalMark signal = signalAt(mouseX, mouseY);
            SignalBoxStatePayload.SwitchMark stand = signal == null ? switchAt(mouseX, mouseY) : null;
            if (signal != null) {
                PacketDistributor.sendToServer(new SignalBoxActionPayload(menu.pos(), SignalBoxActionPayload.SIGNAL, signal.pos()));
            } else if (stand != null) {
                PacketDistributor.sendToServer(new SignalBoxActionPayload(menu.pos(), SignalBoxActionPayload.SWITCH, stand.pos()));
            } else {
                dragging = true;
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (dragging) {
            panX -= (float) dragX / scale;
            panZ -= (float) dragY / scale;
            panX = Mth.clamp(panX, -SignalBoxBlockEntity.RANGE, SignalBoxBlockEntity.RANGE);
            panZ = Mth.clamp(panZ, -SignalBoxBlockEntity.RANGE, SignalBoxBlockEntity.RANGE);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    /** Zooms in or out, keeping the point under the mouse where it is. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (!onMap(mouseX, mouseY) || scrollY == 0) {
            return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
        }
        float worldX = panX + (float) (mouseX - (mapLeft + mapRight) / 2.0) / scale;
        float worldZ = panZ + (float) (mouseY - (mapTop + mapBottom) / 2.0) / scale;
        scale = Mth.clamp(scale * (scrollY > 0 ? 1.25F : 0.8F), MIN_SCALE, MAX_SCALE);
        panX = worldX - (float) (mouseX - (mapLeft + mapRight) / 2.0) / scale;
        panZ = worldZ - (float) (mouseY - (mapTop + mapBottom) / 2.0) / scale;
        return true;
    }

    /** Sets the zoom and middle of the map (the showcase uses it). */
    public void view(float scale, float x, float z) {
        this.scale = Mth.clamp(scale, MIN_SCALE, MAX_SCALE);
        this.panX = x;
        this.panZ = z;
    }
}
