package dev.brauw.mapper.geometry;

import lombok.Value;
import org.bukkit.Axis;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

/**
 * Where a line of sight meets a box, and where it drags one of the box's faces to.
 */
@Value
public class BoxAim {

    /** Below this, a line of sight is treated as parallel to an axis. */
    private static final double PARALLEL = 1.0E-3;

    BoxFace face;
    double distance;

    /**
     * Finds the face a line of sight lands on. From outside the box that is the face it enters
     * through, and from inside it is the face it leaves through, so a region can be resized from
     * either side.
     *
     * @param box       the box
     * @param eye       where the line of sight starts
     * @param direction the line of sight, normalised
     * @param reach     how far the line of sight goes
     * @return the face and how far along the line it is, or {@code null} if the line misses the box
     */
    public static @Nullable BoxAim at(Box box, Vector eye, Vector direction, double reach) {
        double enter = Double.NEGATIVE_INFINITY;
        double exit = Double.POSITIVE_INFINITY;
        BoxFace enterFace = null;
        BoxFace exitFace = null;

        for (Axis axis : Axis.values()) {
            final double origin = BoxFace.component(eye, axis);
            final double step = BoxFace.component(direction, axis);
            final double low = BoxFace.component(box.getMin(), axis);
            final double high = BoxFace.component(box.getMax(), axis);
            if (Math.abs(step) < PARALLEL) {
                if (origin < low || origin > high) {
                    return null;
                }
                continue;
            }
            final double toLow = (low - origin) / step;
            final double toHigh = (high - origin) / step;
            final boolean lowFirst = toLow < toHigh;
            final double near = lowFirst ? toLow : toHigh;
            final double far = lowFirst ? toHigh : toLow;
            if (near > enter) {
                enter = near;
                enterFace = BoxFace.of(axis, !lowFirst);
            }
            if (far < exit) {
                exit = far;
                exitFace = BoxFace.of(axis, lowFirst);
            }
        }

        if (enter > exit || exit < 0) {
            return null;
        }
        if (enter >= 0) {
            return enter <= reach && enterFace != null ? new BoxAim(enterFace, enter) : null;
        }
        return exit <= reach && exitFace != null ? new BoxAim(exitFace, exit) : null;
    }

    /**
     * Works out where a grabbed face goes: the point on the line through the face's centre, along
     * its axis, that passes closest to the line of sight.
     *
     * @param box       the box being resized
     * @param face      the grabbed face
     * @param eye       where the line of sight starts
     * @param direction the line of sight, normalised
     * @return the face's new coordinate along its axis, or {@code null} if the line of sight runs
     * along the axis or the closest point is behind the eye
     */
    public static @Nullable Double drag(Box box, BoxFace face, Vector eye, Vector direction) {
        final Vector axis = face.direction();
        final Vector between = box.centre(face).subtract(eye);
        final double cos = axis.dot(direction);
        final double denominator = 1 - cos * cos;
        if (denominator < PARALLEL) {
            return null;
        }
        final double alongAxis = (cos * between.dot(direction) - between.dot(axis)) / denominator;
        final double alongSight = between.dot(direction) + cos * alongAxis;
        if (alongSight < 0) {
            return null;
        }
        return box.edge(face) + alongAxis;
    }
}
