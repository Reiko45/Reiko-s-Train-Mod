package com.reiko.trains.client;

import com.reiko.trains.registry.ModSounds;
import com.reiko.trains.train.ElectricLocomotive;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * An electric's traction motors, looping for as long as the locomotive's about: silent when it's switched off, a low
 * hum standing with the power on, and a whine that climbs with its speed as it gets going.
 */
public class ElectricMotorSound extends AbstractTickableSoundInstance {
    private final ElectricLocomotive loco;
    private float speed;

    public ElectricMotorSound(ElectricLocomotive loco) {
        super(ModSounds.ELECTRIC_MOTOR.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.loco = loco;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
        this.pitch = 0.6F;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = loco.getX();
        this.y = loco.getY() + 0.5;
        this.z = loco.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (loco.isRemoved()) {
            stop();
            return;
        }
        x = loco.getX();
        y = loco.getY() + 0.5;
        z = loco.getZ();
        speed += (loco.motorSpeed() - speed) * 0.1F;
        volume = loco.isRunning() ? 0.15F + 0.6F * speed : 0.0F;
        pitch = Mth.clamp(0.6F + 1.1F * speed, 0.5F, 2.0F);
    }
}
