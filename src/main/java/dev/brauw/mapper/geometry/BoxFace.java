package dev.brauw.mapper.geometry;

import lombok.Getter;
import org.bukkit.Axis;
import org.bukkit.util.Vector;

/** One of the six faces of an axis-aligned box. */
@Getter
public enum BoxFace {

    WEST(Axis.X, false),
    EAST(Axis.X, true),
    DOWN(Axis.Y, false),
    UP(Axis.Y, true),
    NORTH(Axis.Z, false),
    SOUTH(Axis.Z, true);

    private final Axis axis;
    private final boolean positive;

    BoxFace(Axis axis, boolean positive) {
        this.axis = axis;
        this.positive = positive;
    }

    /** @return the face on {@code axis} on its positive or negative side */
    public static BoxFace of(Axis axis, boolean positive) {
        return switch (axis) {
            case X -> positive ? EAST : WEST;
            case Y -> positive ? UP : DOWN;
            case Z -> positive ? SOUTH : NORTH;
        };
    }

    /** @return the unit vector along this face's axis */
    public Vector direction() {
        final Vector direction = new Vector();
        setComponent(direction, axis, 1);
        return direction;
    }

    static double component(Vector vector, Axis axis) {
        return switch (axis) {
            case X -> vector.getX();
            case Y -> vector.getY();
            case Z -> vector.getZ();
        };
    }

    static void setComponent(Vector vector, Axis axis, double value) {
        switch (axis) {
            case X -> vector.setX(value);
            case Y -> vector.setY(value);
            case Z -> vector.setZ(value);
        }
    }
}
