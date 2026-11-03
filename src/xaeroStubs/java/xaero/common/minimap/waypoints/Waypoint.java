package xaero.common.minimap.waypoints;

import xaero.hud.minimap.waypoint.WaypointColor;
import xaero.hud.minimap.waypoint.WaypointPurpose;

/** Compile-time stand-in for Xaero's Minimap (see build.gradle): only what Reiko's Train Mod uses. */
public class Waypoint {
    public Waypoint(int x, int y, int z, String name, String initials, WaypointColor color, WaypointPurpose purpose, boolean temp,
            boolean yIncluded) {
        throw new UnsupportedOperationException();
    }

    public int getX() {
        throw new UnsupportedOperationException();
    }

    public void setX(int x) {
        throw new UnsupportedOperationException();
    }

    public int getY() {
        throw new UnsupportedOperationException();
    }

    public void setY(int y) {
        throw new UnsupportedOperationException();
    }

    public int getZ() {
        throw new UnsupportedOperationException();
    }

    public void setZ(int z) {
        throw new UnsupportedOperationException();
    }

    public String getName() {
        throw new UnsupportedOperationException();
    }

    public void setName(String name) {
        throw new UnsupportedOperationException();
    }

    public String getInitials() {
        throw new UnsupportedOperationException();
    }

    public void setInitials(String initials) {
        throw new UnsupportedOperationException();
    }

    public void setWaypointColor(WaypointColor color) {
        throw new UnsupportedOperationException();
    }
}
