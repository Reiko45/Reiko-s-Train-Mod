package com.reiko.trains.client;

import com.reiko.trains.network.ServerSettingsPayload;

/** The server's settings, as it told this client on joining (the defaults until then, or after leaving). */
public final class ServerSettings {
    private static double fuelUse = 1.0;
    private static boolean needsFuel = true;

    private ServerSettings() {
    }

    public static void receive(ServerSettingsPayload payload) {
        fuelUse = payload.fuelUse();
        needsFuel = payload.needsFuel();
    }

    public static void reset() {
        fuelUse = 1.0;
        needsFuel = true;
    }

    /** How fast fuel burns (1.0, the default, or the server's {@code fuelUse}). */
    public static double fuelUse() {
        return fuelUse;
    }

    public static boolean needsFuel() {
        return needsFuel;
    }
}
