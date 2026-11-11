package dev.pineple.minecraft.deathmatch;

/** Pure geometry and motion, no world access, so it can be checked at boot without a server. */
final class Ballistics {
    private Ballistics() {}

    /** Grenade flight: blocks/tick² of gravity, speed kept on the bounced axis and on the other two. */
    static final double GRAVITY = 0.05, BOUNCE = 0.45, ROLL = 0.8;

    interface Solid { boolean at(double x, double y, double z); }

    /** Slab test: distance along the ray (origin o, direction d) to the box [min, max], or -1 on a miss. */
    static double rayBox(double ox, double oy, double oz, double dx, double dy, double dz,
                         double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double near = 0, far = Double.MAX_VALUE;
        double[][] axes = {{ox, dx, minX, maxX}, {oy, dy, minY, maxY}, {oz, dz, minZ, maxZ}};
        for (double[] a : axes) {
            if (a[1] == 0) {
                if (a[0] < a[2] || a[0] > a[3]) return -1;
                continue;
            }
            double t1 = (a[2] - a[0]) / a[1], t2 = (a[3] - a[0]) / a[1];
            near = Math.max(near, Math.min(t1, t2));
            far = Math.min(far, Math.max(t1, t2));
        }
        return near <= far ? near : -1;
    }

    /** One tick of grenade flight: gravity, then move one axis at a time and reflect off whatever is solid. */
    static void bounceStep(double[] pos, double[] v, Solid solid) {
        v[1] -= GRAVITY;
        for (int a = 0; a < 3; a++) {
            v[a] = Math.clamp(v[a], -0.95, 0.95); // under one block per tick, so it cannot tunnel through a wall
            pos[a] += v[a];
            if (!solid.at(pos[0], pos[1], pos[2])) continue;
            pos[a] -= v[a];
            v[a] *= -BOUNCE;
            v[(a + 1) % 3] *= ROLL;
            v[(a + 2) % 3] *= ROLL;
        }
    }

    /** Fails boot if the hit-test or bounce math breaks. */
    static void selfCheck() {
        // Standing player box at origin: 0.6 wide, 1.8 tall. Shooter 10 blocks away on +x.
        double body = rayBox(10, 1, 0, -1, 0, 0, -0.3, 0, -0.3, 0.3, 1.8, 0.3);
        double over = rayBox(10, 2, 0, -1, 0, 0, -0.3, 0, -0.3, 0.3, 1.8, 0.3);
        double behind = rayBox(10, 1, 0, 1, 0, 0, -0.3, 0, -0.3, 0.3, 1.8, 0.3);
        if (Math.abs(body - 9.7) > 1e-9 || over != -1 || behind != -1)
            throw new AssertionError("rayBox: " + body + " " + over + " " + behind);
        // Thrown along +x over a floor at y=0 towards a wall at x=5: must bounce back and come to rest.
        double[] pos = {0, 3, 0}, v = {0.9, 0, 0};
        boolean bounced = false;
        for (int i = 0; i < 200; i++) {
            bounceStep(pos, v, (x, y, z) -> y < 0 || x > 5);
            bounced |= v[0] < 0;
            if (pos[1] < 0 || pos[0] > 5) throw new AssertionError("grenade tunnelled at tick " + i);
        }
        if (!bounced || pos[1] > 0.2 || Math.abs(v[0]) > 0.01)
            throw new AssertionError("grenade did not bounce and settle: y=" + pos[1] + " vx=" + v[0]);
    }
}
