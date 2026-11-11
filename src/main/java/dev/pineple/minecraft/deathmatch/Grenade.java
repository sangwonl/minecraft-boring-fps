package dev.pineple.minecraft.deathmatch;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.metadata.item.SnowballMeta;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.particle.Particle;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.timer.TaskSchedule;

/** A thrown grenade: one per life, bounces around for a fuse, then its {@link Kind} decides what happens. */
final class Grenade implements Weapon {
    private static final double THROW_SPEED = 1.0; // blocks per tick
    private static final int FUSE_TICKS = 32;
    private static final double HE_RADIUS = 7, FLASH_RADIUS = 40;
    private static final float HE_DAMAGE = 19;
    private static final int FLASH_TICKS = 70, SMOKE_SECONDS = 15;
    private static final double BACK_TURNED = 0.4; // share of the blindness you still get looking away

    enum Kind {
        /** Damage falls off with distance; any cover in between blocks it. Hurts the thrower too. */
        HE("HE Grenade", Material.FIRE_CHARGE, "grenade_he") {
            @Override void detonate(Vec at, Combatant thrower, Match match) {
                Arena arena = match.arena();
                arena.particles(Particle.EXPLOSION_EMITTER, at, 0, 0, 1);
                arena.sound(SoundEvent.ENTITY_GENERIC_EXPLODE, 4f, 1f, at);
                for (Combatant target : match.combatants()) {
                    double distance = target.center().distance(at);
                    if (distance < HE_RADIUS && arena.clearLine(at, target.center()))
                        match.damage(thrower, target, (float) (HE_DAMAGE * (1 - distance / HE_RADIUS)), label, false);
                }
            }
        },
        /** Blinds whoever has a clear line to it, less when they look away. Blindness is the closest a vanilla client has to a white-out. */
        FLASH("Flashbang", Material.SNOWBALL, "grenade_flash") {
            @Override void detonate(Vec at, Combatant thrower, Match match) {
                Arena arena = match.arena();
                arena.particles(Particle.EXPLOSION, at, 0, 0, 1);
                arena.sound(SoundEvent.ENTITY_FIREWORK_ROCKET_TWINKLE, 4f, 1.5f, at);
                Vec burst = at.add(0, 0.5, 0); // it pops just above where it lies, so a kerb does not hide it
                int enemies = 0;
                for (Combatant target : match.combatants()) {
                    Pos eye = target.eye();
                    Vec toFlash = burst.sub(eye);
                    double distance = toFlash.length();
                    if (distance > FLASH_RADIUS || !arena.clearLine(eye, burst)) continue;
                    boolean facing = distance < 0.5 || eye.direction().dot(toFlash.normalize()) > 0.2;
                    int full = (int) (FLASH_TICKS * (1 - distance / FLASH_RADIUS)) + 10;
                    target.blind(facing ? full : (int) (full * BACK_TURNED));
                    if (target != thrower) enemies++;
                }
                thrower.player().sendActionBar(Component.text("Flashed " + enemies, NamedTextColor.WHITE));
            }
        },
        /** Blocks vision only, bullets pass. Invisible to clients on "Particles: Minimal". */
        SMOKE("Smoke", Material.FIREWORK_STAR, "grenade_smoke") {
            @Override void detonate(Vec at, Combatant thrower, Match match) {
                Arena arena = match.arena();
                arena.sound(SoundEvent.BLOCK_FIRE_EXTINGUISH, 2f, 0.7f, at);
                int[] puffs = {0};
                arena.world().scheduler().submitTask(() -> {
                    arena.particles(Particle.CAMPFIRE_COSY_SMOKE, at.add(0, 1.2, 0), 1.8f, 0.003f, 40);
                    return puffs[0]++ < SMOKE_SECONDS * 2 ? TaskSchedule.tick(10) : TaskSchedule.stop();
                });
            }
        };

        final String label;
        final Material material;
        final String modelId;

        Kind(String label, Material material, String modelId) {
            this.label = label;
            this.material = material;
            this.modelId = modelId;
        }

        ItemStack stack() { return Weapon.named(material, label, modelId); }

        abstract void detonate(Vec at, Combatant thrower, Match match);
    }

    private final Kind kind;
    private boolean available = true;

    Grenade(Kind kind) { this.kind = kind; }

    @Override public ItemStack item() { return available ? kind.stack() : ItemStack.AIR; }
    @Override public int ammo() { return available ? 1 : 0; }
    @Override public void refill() { available = true; }

    @Override public void primary(Combatant user, Match match) {
        if (!available) return;
        available = false;
        user.dropProtection();
        user.showLoadout();

        Arena arena = match.arena();
        Pos eye = user.eye();
        Vec aim = eye.direction();
        double[] pos = {eye.x(), eye.y(), eye.z()};
        double[] velocity = {aim.x() * THROW_SPEED, aim.y() * THROW_SPEED + 0.1, aim.z() * THROW_SPEED};
        int[] age = {0};
        Entity body = new Entity(EntityType.SNOWBALL); // moved by hand each tick, so no built-in gravity
        body.setNoGravity(true);
        body.editEntityMeta(SnowballMeta.class, meta -> meta.setItem(kind.stack()));
        arena.sound(SoundEvent.ENTITY_SNOWBALL_THROW, 1f, 0.6f, eye);
        body.setInstance(arena.world(), eye).thenRun(() -> arena.world().scheduler().submitTask(() -> {
            if (age[0]++ >= FUSE_TICKS) {
                body.remove();
                kind.detonate(new Vec(pos[0], pos[1], pos[2]), user, match);
                return TaskSchedule.stop();
            }
            Ballistics.bounceStep(pos, velocity, arena::solid);
            body.teleport(new Pos(pos[0], pos[1], pos[2]));
            return TaskSchedule.tick(1);
        }));
    }
}
