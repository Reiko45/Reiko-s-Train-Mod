package com.reiko.trains.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reiko.trains.ReikoTrains;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;

/**
 * Draws plain boxes for block entity renderers, in blocks, in whatever space the pose stack is in: each face shows as
 * much of its sprite as it's big (a block or more shows all of it), so long things should be drawn in pieces.
 */
final class Boxes {
    private final VertexConsumer consumer;
    private final PoseStack.Pose pose;

    Boxes(VertexConsumer consumer, PoseStack.Pose pose) {
        this.consumer = consumer;
        this.pose = pose;
    }

    static TextureAtlasSprite sprite(String path) {
        return Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ReikoTrains.id(path));
    }

    void box(double x0, double y0, double z0, double x1, double y1, double z1, TextureAtlasSprite sprite, int light) {
        float ax = (float) x0;
        float ay = (float) y0;
        float az = (float) z0;
        float bx = (float) x1;
        float by = (float) y1;
        float bz = (float) z1;
        float dx = Math.min(1, bx - ax);
        float dy = Math.min(1, by - ay);
        float dz = Math.min(1, bz - az);
        // up, down
        quad(ax, by, az, ax, by, bz, bx, by, bz, bx, by, az, 0, 1, 0, sprite, dx, dz, light);
        quad(ax, ay, bz, ax, ay, az, bx, ay, az, bx, ay, bz, 0, -1, 0, sprite, dx, dz, light);
        // north (-z), south (+z)
        quad(bx, ay, az, ax, ay, az, ax, by, az, bx, by, az, 0, 0, -1, sprite, dx, dy, light);
        quad(ax, ay, bz, bx, ay, bz, bx, by, bz, ax, by, bz, 0, 0, 1, sprite, dx, dy, light);
        // west (-x), east (+x)
        quad(ax, ay, az, ax, ay, bz, ax, by, bz, ax, by, az, -1, 0, 0, sprite, dz, dy, light);
        quad(bx, ay, bz, bx, ay, az, bx, by, az, bx, by, bz, 1, 0, 0, sprite, dz, dy, light);
    }

    /** A flat face, from a's corner round to d's, showing {@code du} by {@code dv} of the sprite (as shares of it). */
    private void quad(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy, float dz,
                      float nx, float ny, float nz, TextureAtlasSprite sprite, float du, float dv, int light) {
        float u0 = sprite.getU(0);
        float u1 = sprite.getU(du);
        float v0 = sprite.getV(0);
        float v1 = sprite.getV(dv);
        vertex(ax, ay, az, u0, v1, nx, ny, nz, light);
        vertex(bx, by, bz, u1, v1, nx, ny, nz, light);
        vertex(cx, cy, cz, u1, v0, nx, ny, nz, light);
        vertex(dx, dy, dz, u0, v0, nx, ny, nz, light);
    }

    private void vertex(float x, float y, float z, float u, float v, float nx, float ny, float nz, int light) {
        consumer.addVertex(pose, x, y, z).setColor(255, 255, 255, 255).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
