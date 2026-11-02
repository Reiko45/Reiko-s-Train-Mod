package com.reiko.trains.client;

import com.reiko.trains.registry.ModSounds;
import com.reiko.trains.train.DieselLocomotive;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

/**
 * A diesel's engine, looping for as long as the locomotive's about: quiet when it's shut down, ticking over when it's
 * running, and louder and higher as the throttle opens (it eases between them, as a real engine winds up).
 */
public class DieselEngineSound extends AbstractTickableSoundInstance {
    private final DieselLocomotive loco;
    private float load;

    public DieselEngineSound(DieselLocomotive loco) {
        super(ModSounds.DIESEL_ENGINE.get(), SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.loco = loco;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01F;
        this.pitch = 0.8F;
        this.attenuation = SoundInstance.Attenuation.LINEAR;
        this.x = loco.getX();
        this.y = loco.getY() + 1.5;
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
        y = loco.getY() + 1.5;
        z = loco.getZ();
        load += (loco.engineLoad() - load) * 0.05F;
        volume = loco.isRunning() ? 0.35F + 0.55F * load : 0.0F;
        pitch = Mth.clamp(0.8F + 0.6F * load, 0.5F, 2.0F);
    }
}
