package dev.pineple.minecraft.deathmatch;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.entity.attribute.AttributeModifier;
import net.minestom.server.entity.attribute.AttributeOperation;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;

import java.util.concurrent.ThreadLocalRandom;

/** A hitscan firearm. The {@link Spec} is the shared blueprint; the instance is one player's copy with its own magazine. */
final class Gun implements Weapon {
    /**
     * damage: Minecraft health points (20 = full). pellets: rays per click. recoil: degrees of view kick per shot.
     * penetration: cover a bullet can pass (1 = one plank block, see Arena.density). scope: right-click zoom, wild unscoped.
     * All values are original guesses, tune by feel.
     */
    record Spec(String name, Material material, float damage, int cooldownMs, int magazine, int reloadMs,
                double spread, int pellets, double range, float soundPitch, float recoil, double penetration,
                boolean scope) {
        /** Names the model ("rifle") and sounds ("rifle_fire") in the resource pack. */
        String id() { return name.toLowerCase(); }

        // Left-click fires one shot per click (semi-auto); cooldownMs caps the click rate.
        static final Spec RIFLE = new Spec("Rifle", Material.IRON_HOE, 6, 150, 30, 2400, 0.008, 1, 100, 1.3f, 1.2f, 2, false);
        static final Spec SMG = new Spec("SMG", Material.GOLDEN_HOE, 2.5f, 150, 25, 2000, 0.025, 2, 50, 1.8f, 0.8f, 1, false);
        static final Spec SHOTGUN = new Spec("Shotgun", Material.IRON_SHOVEL, 2.5f, 900, 7, 2800, 0.07, 8, 30, 0.6f, 4, 0, false);
        static final Spec SNIPER = new Spec("Sniper", Material.BLAZE_ROD, 23, 1500, 5, 3000, 0, 1, 150, 0.4f, 5, 3, true);
        static final Spec PISTOL = new Spec("Pistol", Material.STICK, 4.5f, 150, 12, 1500, 0.015, 1, 60, 1.6f, 1.5f, 1, false);
    }

    private static final float HEADSHOT = 4;
    // Spray: each shot within HEAT_RESET_MS of the last adds heat; heat widens spread and strengthens the kick.
    private static final int HEAT_RESET_MS = 450, HEAT_MAX = 10;
    private static final double HEAT_SPREAD = 0.35, AIR_SPREAD = 0.08, UNSCOPED_SPREAD = 0.08;
    private static final float HEAT_KICK = 0.15f;
    private static final double PENETRATION_DAMAGE_LOSS = 0.6; // lost when the whole penetration budget is used
    // Zoom is the vanilla "slower = narrower FOV" effect, so scoping also slows you. -0.85 is roughly 1.7x zoom.
    private static final AttributeModifier SCOPE =
            new AttributeModifier("dm:scope", -0.85, AttributeOperation.ADD_MULTIPLIED_TOTAL);

    private final Spec spec;
    private int ammo;
    private int heat;
    private long nextShot, lastShot, lastSecondary;
    private boolean scoped;
    private Task reloading;

    Gun(Spec spec) {
        this.spec = spec;
        this.ammo = spec.magazine;
    }

    @Override public ItemStack item() { return Weapon.named(spec.material, spec.name, spec.id()); }
    @Override public int ammo() { return ammo; }

    @Override public void primary(Combatant user, Match match) {
        long now = System.currentTimeMillis();
        if (reloading != null || now < nextShot) return;
        if (ammo == 0) {
            reload(user);
            return;
        }
        nextShot = now + spec.cooldownMs;
        ammo--;
        heat = now - lastShot > HEAT_RESET_MS ? 0 : Math.min(heat + 1, HEAT_MAX);
        lastShot = now;
        user.dropProtection(); // shooting gives up spawn protection
        user.showAmmo();

        ThreadLocalRandom random = ThreadLocalRandom.current();
        double spread = spec.spread * (1 + HEAT_SPREAD * heat) + (user.airborne() ? AIR_SPREAD : 0)
                + (spec.scope && !scoped ? UNSCOPED_SPREAD : 0);
        float kick = spec.recoil * (1 + HEAT_KICK * heat);
        user.kick((float) random.nextGaussian() * kick * 0.4f, -kick);

        Arena arena = match.arena();
        Pos eye = user.eye();
        arena.sound(ResourcePack.sound(spec.id() + "_fire", SoundEvent.ENTITY_FIREWORK_ROCKET_BLAST), 1.5f,
                ResourcePack.active() ? 0.95f + random.nextFloat() * 0.1f : spec.soundPitch, eye);
        // No lag compensation: targets are tested where the server sees them now. Keep a position history
        // per combatant and rewind by the shooter's latency if high-ping play matters.
        for (int i = 0; i < spec.pellets; i++) {
            Vec dir = eye.direction().add(random.nextGaussian() * spread, random.nextGaussian() * spread,
                    random.nextGaussian() * spread).normalize();
            double reach = arena.bulletReach(eye, dir, spec.range, spec.penetration);
            Combatant victim = null;
            boolean headshot = false;
            for (Combatant other : match.combatants()) {
                Combatant.Hit hit = other == user ? null : other.hitBy(eye, dir);
                if (hit == null || hit.distance() >= reach) continue;
                reach = hit.distance();
                victim = other;
                headshot = hit.headshot();
            }
            arena.tracer(eye, dir, reach);
            if (victim == null) continue;
            double cover = arena.cover(eye, dir, reach);
            float kept = cover == 0 ? 1 : (float) (1 - PENETRATION_DAMAGE_LOSS * cover / spec.penetration);
            match.damage(user, victim, spec.damage * (headshot ? HEADSHOT : 1) * kept,
                    spec.name + (cover > 0 ? " ▒" : ""), headshot);
        }
    }

    /** Right-click toggles the scope on guns that have one. A held button repeats every 200ms: only the press counts. */
    @Override public void secondary(Combatant user, Match match) {
        long now = System.currentTimeMillis();
        boolean held = now - lastSecondary < 300;
        lastSecondary = now;
        if (spec.scope && !held) scope(user, !scoped);
    }

    @Override public void reload(Combatant user) {
        if (reloading != null || ammo == spec.magazine) return;
        user.player().sendActionBar(Component.text("Reloading " + spec.name + "...", NamedTextColor.YELLOW));
        user.hear(ResourcePack.sound("reload", SoundEvent.BLOCK_IRON_DOOR_CLOSE), 0.6f, ResourcePack.active() ? 1f : 1.6f);
        reloading = MinecraftServer.getSchedulerManager().buildTask(() -> {
            reloading = null;
            ammo = spec.magazine;
            if (user.held() == this) user.showAmmo();
        }).delay(TaskSchedule.millis(spec.reloadMs)).schedule();
    }

    @Override public void holster(Combatant user) { scope(user, false); }

    @Override public void refill() {
        if (reloading != null) reloading.cancel(); // a reload in flight must not finish in the next life
        reloading = null;
        ammo = spec.magazine;
        heat = 0;
    }

    private void scope(Combatant user, boolean on) {
        if (scoped == on) return;
        scoped = on;
        var speed = user.player().getAttribute(Attribute.MOVEMENT_SPEED);
        if (on) speed.addModifier(SCOPE);
        else speed.removeModifier(SCOPE);
        user.hear(SoundEvent.ITEM_SPYGLASS_USE, 1f, on ? 1f : 0.7f);
    }
}
