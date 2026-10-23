package worldradio.signal;

/**
 * Where a radio with speakers is heard from. There is one sound per station, so the radio and its speakers cannot each
 * play it; instead the sound sits at their mean place, each weighted by the inverse square of its distance to the
 * listener. Next to one of them it comes from that block, between them from in between, and it moves without a jump
 * as the listener walks along a row of speakers.
 */
public final class SoundPlace {
    /** Keeps the weight finite inside a block: closer than this counts as this far. */
    private static final double NEAR = 0.5;

    private SoundPlace() {
    }

    /** The place for a listener at {@code ear} (x, y, z) among {@code points} (each x, y, z; at least one). */
    public static double[] blend(double[] ear, double[][] points) {
        double x = 0, y = 0, z = 0, total = 0;
        for (double[] p : points) {
            double dx = p[0] - ear[0], dy = p[1] - ear[1], dz = p[2] - ear[2];
            double w = 1.0 / Math.max(dx * dx + dy * dy + dz * dz, NEAR * NEAR);
            x += w * p[0];
            y += w * p[1];
            z += w * p[2];
            total += w;
        }
        return new double[]{x / total, y / total, z / total};
    }
}
