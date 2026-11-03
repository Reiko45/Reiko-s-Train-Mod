package com.reiko.trains.client;

import javax.annotation.Nullable;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * A train car's model, baked from one of the generated geometry classes, with its moving parts: wheels that turn
 * as the car rolls, coupling rods that follow the driving wheels' crank pins, crates for a wagon's load, and an
 * electric's pantograph (parts "panto_up" and "panto_down"), which rises to the wire.
 */
public final class TrainModel {
    private final ModelPart root;
    private final ModelPart[] wheels;
    private final float[] wheelRadii;
    private final float[] wheelPhase;
    private final ModelPart[] rods;
    private final float[] rodX;
    private final float[] rodY;
    private final float crankRadius;
    private final float driverRadius;
    private final ModelPart[] cargo;
    @Nullable
    private final ModelPart pantographUp;
    @Nullable
    private final ModelPart pantographDown;

    public TrainModel(ModelPart root, String[] wheels, float[] wheelRadii, String[] rods, float crankRadius, float driverRadius, String[] cargo) {
        this.root = root;
        this.wheels = new ModelPart[wheels.length];
        this.wheelPhase = new float[wheels.length];
        for (int i = 0; i < wheels.length; i++) {
            this.wheels[i] = root.getChild(wheels[i]);
            // The two sides' cranks are a quarter turn apart, as on a real engine.
            this.wheelPhase[i] = wheels[i].contains("_r") ? Mth.HALF_PI : 0.0F;
        }
        this.wheelRadii = wheelRadii;
        this.rods = new ModelPart[rods.length];
        this.rodX = new float[rods.length];
        this.rodY = new float[rods.length];
        for (int i = 0; i < rods.length; i++) {
            this.rods[i] = root.getChild(rods[i]);
            this.rodX[i] = this.rods[i].x;
            this.rodY[i] = this.rods[i].y;
        }
        this.crankRadius = crankRadius;
        this.driverRadius = driverRadius;
        this.cargo = new ModelPart[cargo.length];
        for (int i = 0; i < cargo.length; i++) {
            this.cargo[i] = root.getChild(cargo[i]);
        }
        this.pantographUp = root.hasChild("panto_up") ? root.getChild("panto_up") : null;
        this.pantographDown = root.hasChild("panto_down") ? root.getChild("panto_down") : null;
    }

    /** Raises the pantograph, if it has one: 0 folded down, 1 right up against the wire. */
    public void pantograph(float raised) {
        if (pantographUp == null || pantographDown == null) {
            return;
        }
        boolean up = raised > 0.05F;
        pantographUp.visible = up;
        pantographUp.yScale = Math.max(0.05F, raised);
        pantographDown.visible = !up;
    }

    public static TrainModel simple(ModelPart root, String[] wheels, float[] wheelRadii) {
        return new TrainModel(root, wheels, wheelRadii, new String[0], 0.0F, 1.0F, new String[0]);
    }

    /**
     * Poses the moving parts.
     *
     * @param distance how far the car has rolled, in blocks (forwards positive)
     * @param load     how many of the cargo crates to show
     */
    public void setup(float distance, int load) {
        // Rolling forwards (towards -x in model space), a wheel turns so its top goes forwards: a negative angle.
        for (int i = 0; i < wheels.length; i++) {
            wheels[i].zRot = -distance * 16.0F / wheelRadii[i] + wheelPhase[i];
        }
        float crank = -distance * 16.0F / driverRadius;
        for (int i = 0; i < rods.length; i++) {
            float angle = crank + (i == 0 ? 0.0F : Mth.HALF_PI);
            rods[i].x = rodX[i] + crankRadius * Mth.cos(angle);
            rods[i].y = rodY[i] + crankRadius * Mth.sin(angle);
        }
        for (int i = 0; i < cargo.length; i++) {
            cargo[i].visible = i < load;
        }
    }

    public void render(PoseStack poseStack, VertexConsumer consumer, int light, int overlay) {
        root.render(poseStack, consumer, light, overlay);
    }
}
