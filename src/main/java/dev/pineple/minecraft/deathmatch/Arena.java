package dev.pineple.minecraft.deathmatch;

import net.kyori.adventure.sound.Sound;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.ParticlePacket;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** The map: builds the world, knows where players may spawn and how much cover lies along a line. */
final class Arena {
    private static final int HALF = 30, FLOOR = 40; // walls at ±HALF, players stand on y = FLOOR + 1

    private final InstanceContainer world = MinecraftServer.getInstanceManager().createInstanceContainer();
    private final List<Pos> spawns = new ArrayList<>();
    private final Random random = new Random();

    Arena() {
        world.setChunkSupplier(LightingChunk::new);
        world.setGenerator(unit -> unit.modifier().fillHeight(FLOOR - 1, FLOOR + 1, Block.SMOOTH_STONE));
        world.setTime(6000);
        world.defaultClock().pause();
        for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) world.loadChunk(cx, cz).join();
        buildWalls();
        buildCover();
        placeSpawns();
    }

    InstanceContainer world() { return world; }

    // ---- spawning ----------------------------------------------------------------------------------------------

    Pos randomSpawn() { return spawns.get(random.nextInt(spawns.size())); }

    /** The spawn whose nearest enemy is farthest away; random when nobody else is around. */
    Pos spawnAwayFrom(Collection<Pos> enemies) {
        return spawns.stream().max(Comparator.comparingDouble(spawn -> enemies.stream()
                .mapToDouble(enemy -> enemy.distanceSquared(spawn)).min().orElse(random.nextDouble()))).orElseThrow();
    }

    // ---- cover -------------------------------------------------------------------------------------------------

    /** How hard a block is to shoot through per block of thickness: 0 = air, 100 = bulletproof. */
    double density(double x, double y, double z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        if (!world.isChunkLoaded(bx >> 4, bz >> 4)) return 100;
        Block block = world.getBlock(bx, by, bz);
        return !block.solid() ? 0 : block.compare(Block.IRON_BLOCK) ? 100 : block.compare(Block.OAK_PLANKS) ? 1 : 2;
    }

    boolean solid(double x, double y, double z) { return density(x, y, z) > 0; }

    // Both ray walks sample every 0.2 blocks against full cubes; switch to a voxel DDA with block shapes
    // if slabs or fences are ever used as cover.

    /** Density-weighted cover along the ray from its origin to upTo. */
    double cover(Point from, Vec dir, double upTo) {
        double sum = 0;
        for (double t = 0; t < upTo; t += 0.2)
            sum += 0.2 * density(from.x() + dir.x() * t, from.y() + dir.y() * t, from.z() + dir.z() * t);
        return sum;
    }

    /** Distance at which a bullet that can pass `penetration` worth of cover stops, or max. */
    double bulletReach(Point from, Vec dir, double max, double penetration) {
        double sum = 0;
        for (double t = 0; t < max; t += 0.2) {
            sum += 0.2 * density(from.x() + dir.x() * t, from.y() + dir.y() * t, from.z() + dir.z() * t);
            if (sum > penetration) return t;
        }
        return max;
    }

    /** True when nothing solid lies between the two points. */
    boolean clearLine(Point a, Point b) {
        Vec line = new Vec(b.x() - a.x(), b.y() - a.y(), b.z() - a.z());
        return line.length() < 0.5 || cover(a, line.normalize(), line.length()) == 0;
    }

    // ---- effects everyone in the arena perceives ---------------------------------------------------------------

    void sound(SoundEvent event, float volume, float pitch, Point at) {
        world.playSound(Sound.sound(event, Sound.Source.PLAYER, volume, pitch), at.x(), at.y(), at.z());
    }

    void particles(Particle particle, Point at, float spread, float speed, int count) {
        world.sendGroupedPacket(new ParticlePacket(particle, true, true, at.x(), at.y(), at.z(),
                spread, spread / 2, spread, speed, count));
    }

    void tracer(Point from, Vec dir, double length) {
        for (double t = 2; t < length; t += 2)
            world.sendGroupedPacket(new ParticlePacket(Particle.CRIT, from.x() + dir.x() * t,
                    from.y() + dir.y() * t, from.z() + dir.z() * t, 0, 0, 0, 0, 1));
    }

    // ---- construction ------------------------------------------------------------------------------------------

    private void buildWalls() {
        for (int i = -HALF; i <= HALF; i++)
            for (int y = FLOOR + 1; y <= FLOOR + 6; y++)
                for (int[] xz : new int[][]{{i, -HALF}, {i, HALF}, {-HALF, i}, {HALF, i}})
                    world.setBlock(xz[0], y, xz[1], Block.STONE_BRICKS);
    }

    /** Random boxes, mirrored through the centre so no side is favoured. Fixed seed = same map every boot. */
    private void buildCover() {
        Block[] materials = {Block.OAK_PLANKS, Block.BRICKS, Block.STONE_BRICKS, Block.IRON_BLOCK, Block.CYAN_TERRACOTTA};
        Random layout = new Random(7);
        for (int n = 0; n < 45; n++) {
            int x = layout.nextInt(-HALF + 3, HALF - 5), z = layout.nextInt(-HALF + 3, HALF - 5);
            int w = layout.nextInt(1, 5), d = layout.nextInt(1, 5), h = layout.nextInt(1, 4);
            Block material = materials[layout.nextInt(materials.length)];
            for (int dx = 0; dx < w; dx++) for (int dz = 0; dz < d; dz++) for (int dy = 1; dy <= h; dy++) {
                world.setBlock(x + dx, FLOOR + dy, z + dz, material);
                world.setBlock(-x - dx, FLOOR + dy, -z - dz, material);
            }
        }
    }

    /** Two rings of eight spawns facing the centre, each with a 3×3×3 pocket of air carved out of the cover. */
    private void placeSpawns() {
        for (int ring : new int[]{24, 12})
            for (int a = 0; a < 8; a++) {
                double angle = Math.PI / 4 * a + (ring == 12 ? Math.PI / 8 : 0);
                int x = (int) Math.round(Math.cos(angle) * ring), z = (int) Math.round(Math.sin(angle) * ring);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) for (int dy = 1; dy <= 3; dy++)
                    world.setBlock(x + dx, FLOOR + dy, z + dz, Block.AIR);
                spawns.add(new Pos(x + 0.5, FLOOR + 1, z + 0.5).withLookAt(new Pos(0, FLOOR + 2.6, 0)));
            }
    }
}
