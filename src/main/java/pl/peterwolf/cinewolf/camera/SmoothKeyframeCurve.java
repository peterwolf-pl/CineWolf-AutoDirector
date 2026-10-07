package pl.peterwolf.cinewolf.camera;

import pl.peterwolf.cinewolf.model.Vec3d;

/**
 * Centripetal Catmull-Rom used by Flashback {@code InterpolationType.SMOOTH}.
 *
 * <p>Keyframe times are cinematic seconds. Duplicated endpoints (no neighbour before the first key or
 * after the last, and HOLD cuts) match Flashback, which repeats the segment endpoint instead of looking
 * across a missing or held neighbour. A two-point span is therefore smoothstep, not a straight chord.</p>
 */
public final class SmoothKeyframeCurve {
    private static final double MIN_RATIO = 0.4;
    private static final double MAX_RATIO = 2.5;

    private SmoothKeyframeCurve() {
    }

    public static Vec3d position(Vec3d before, Vec3d start, Vec3d end, Vec3d after,
                                 double beforeTime, double startTime, double endTime, double afterTime,
                                 double time) {
        double amount = amount(startTime, endTime, time);
        double dt01 = startTime - beforeTime;
        double dt02 = endTime - beforeTime;
        double dt03 = afterTime - beforeTime;
        double tj01 = distanceKnot(before, start);
        double tj12 = distanceKnot(start, end);
        double tj23 = distanceKnot(end, after);
        double[] knots = knots(tj01, tj12, tj23, dt01, dt02, dt03);
        if (knots == null) return start;
        double parameter = knots[1] + (knots[2] - knots[1]) * amount;
        Vec3d a1 = lerp(before, start, parameter, knots[0], knots[1]);
        Vec3d a2 = lerp(start, end, parameter, knots[1], knots[2]);
        Vec3d a3 = lerp(end, after, parameter, knots[2], knots[3]);
        Vec3d b1 = lerp(a1, a2, parameter, knots[0], knots[2]);
        Vec3d b2 = lerp(a2, a3, parameter, knots[1], knots[3]);
        return lerp(b1, b2, parameter, knots[1], knots[2]);
    }

    public static double degrees(double before, double start, double end, double after,
                                 double beforeTime, double startTime, double endTime, double afterTime,
                                 double time) {
        double unwrappedBefore = wrapDegrees(before);
        double unwrappedStart = unwrappedBefore + wrapDegrees(start - before);
        double unwrappedEnd = unwrappedStart + wrapDegrees(end - start);
        double unwrappedAfter = unwrappedEnd + wrapDegrees(after - end);
        return value(unwrappedBefore, unwrappedStart, unwrappedEnd, unwrappedAfter,
                beforeTime, startTime, endTime, afterTime, time, true);
    }

    public static double value(double before, double start, double end, double after,
                               double beforeTime, double startTime, double endTime, double afterTime,
                               double time) {
        return value(before, start, end, after, beforeTime, startTime, endTime, afterTime, time, false);
    }

    private static double value(double before, double start, double end, double after,
                                double beforeTime, double startTime, double endTime, double afterTime,
                                double time, boolean angular) {
        double amount = amount(startTime, endTime, time);
        double dt01 = startTime - beforeTime;
        double dt02 = endTime - beforeTime;
        double dt03 = afterTime - beforeTime;
        double tj01 = scalarKnot(before, start, angular);
        double tj12 = scalarKnot(start, end, angular);
        double tj23 = scalarKnot(end, after, angular);
        double[] knots = knots(tj01, tj12, tj23, dt01, dt02, dt03);
        if (knots == null) return start;
        double parameter = knots[1] + (knots[2] - knots[1]) * amount;
        double a1 = lerp(before, start, parameter, knots[0], knots[1]);
        double a2 = lerp(start, end, parameter, knots[1], knots[2]);
        double a3 = lerp(end, after, parameter, knots[2], knots[3]);
        double b1 = lerp(a1, a2, parameter, knots[0], knots[2]);
        double b2 = lerp(a2, a3, parameter, knots[1], knots[3]);
        return lerp(b1, b2, parameter, knots[1], knots[2]);
    }

    private static double amount(double startTime, double endTime, double time) {
        double span = endTime - startTime;
        if (span <= 1.0e-9) return 0.0;
        return Math.max(0.0, Math.min(1.0, (time - startTime) / span));
    }

    /** @return cumulative knots, or null when the span has no spatial length */
    private static double[] knots(double tj01, double tj12, double tj23, double dt01, double dt02, double dt03) {
        double average = (tj01 + tj12 + tj23) / 3.0;
        if (average == 0.0) return null;
        double scale = (dt03 / 3.0) / average;
        double knot01 = scaledKnot(tj01, scale, dt01);
        double knot12 = scaledKnot(tj12, scale, dt02 - dt01);
        double knot23 = scaledKnot(tj23, scale, dt03 - dt02);
        return new double[] {0.0, knot01, knot01 + knot12, knot01 + knot12 + knot23};
    }

    private static double scaledKnot(double tj, double scale, double deltaTime) {
        if (deltaTime <= 0.0 || tj == 0.0) return 0.0;
        double ratio = Math.max(MIN_RATIO, Math.min(MAX_RATIO, (tj * scale) / deltaTime));
        return (tj * scale) / ratio;
    }

    private static double distanceKnot(Vec3d from, Vec3d to) {
        return Math.pow(from.distanceTo(to), 0.5);
    }

    private static double scalarKnot(double from, double to, boolean angular) {
        double delta = angular ? wrapDegrees(to - from) : to - from;
        return Math.sqrt(Math.abs(delta));
    }

    private static Vec3d lerp(Vec3d from, Vec3d to, double parameter, double left, double right) {
        if (left == right) return from.lerp(to, 0.5);
        return from.lerp(to, (parameter - left) / (right - left));
    }

    private static double lerp(double from, double to, double parameter, double left, double right) {
        if (left == right) return from + (to - from) * 0.5;
        double amount = (parameter - left) / (right - left);
        return from + (to - from) * amount;
    }

    private static double wrapDegrees(double value) {
        double wrapped = value % 360.0;
        if (wrapped >= 180.0) wrapped -= 360.0;
        if (wrapped < -180.0) wrapped += 360.0;
        return wrapped;
    }
}
