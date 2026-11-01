package com.reiko.trains.registry;

import com.reiko.trains.ReikoTrains;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ReikoTrains.MODID);

    public static final Supplier<SoundEvent> STEAM_WHISTLE = register("steam_whistle");
    public static final Supplier<SoundEvent> STEAM_CHUFF = register("steam_chuff");
    public static final Supplier<SoundEvent> COUPLE = register("couple");

    private ModSounds() {
    }

    private static Supplier<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ReikoTrains.id(name)));
    }
}
