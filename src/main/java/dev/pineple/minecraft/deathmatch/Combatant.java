package dev.pineple.minecraft.deathmatch;

import net.kyori.adventure.sound.Sound;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.damage.Damage;
import net.minestom.server.network.packet.server.play.PlayerRotationPacket;
import net.minestom.server.potion.Potion;
import net.minestom.server.potion.PotionEffect;
import net.minestom.server.sound.SoundEvent;

/** A player taking part in the match: their body (hitbox, health, view), score, protection and weapons. */
final class Combatant {
    private static final int PROTECT_MS = 2000;
    private static final double HEAD_HEIGHT = 0.45; // top slice of the hitbox that counts as the head

    record Hit(double distance, boolean headshot) {}

    private final Player player;
    private final Loadout loadout = Loadout.standard();
    private int kills;
    private long protectedUntil;

    Combatant(Player player) {
        this.player = player;
        player.setGameMode(GameMode.ADVENTURE);
    }

    Player player() { return player; }
    String name() { return player.getUsername(); }

    // ---- score -------------------------------------------------------------------------------------------------

    int kills() { return kills; }
    void addKill() { kills++; }
    void resetKills() { kills = 0; }

    // ---- body --------------------------------------------------------------------------------------------------

    Pos eye() { return player.getPosition().add(0, player.getEyeHeight(), 0); }
    Vec center() { return player.getPosition().asVec().add(0, 0.9, 0); }
    boolean airborne() { return !player.isOnGround(); }

    /** Where a ray enters this combatant's hitbox, or null if it misses. */
    Hit hitBy(Point from, Vec dir) {
        Pos at = player.getPosition();
        BoundingBox box = player.getBoundingBox();
        double t = Ballistics.rayBox(from.x(), from.y(), from.z(), dir.x(), dir.y(), dir.z(),
                at.x() + box.minX(), at.y() + box.minY(), at.z() + box.minZ(),
                at.x() + box.maxX(), at.y() + box.maxY(), at.z() + box.maxZ());
        return t < 0 ? null : new Hit(t, from.y() + dir.y() * t >= at.y() + box.maxY() - HEAD_HEIGHT);
    }

    boolean survives(float damage) { return player.getHealth() - damage > 0; }

    /** Non-lethal damage; the match handles the lethal case so nobody ever sees a death screen. */
    void wound(Combatant by, float damage) { player.damage(Damage.fromPlayer(by.player, damage)); }

    /** Nudge the view relative to wherever the client is looking, so there is no snap-back. */
    void kick(float yaw, float pitch) {
        // Floodgate gives Bedrock players UUIDs with zero high bits. Geyser ignores the "relative" flags and
        // applies the angles as absolute, so those players get the summed view instead of a delta.
        boolean bedrock = player.getUuid().getMostSignificantBits() == 0;
        Pos view = player.getPosition();
        player.sendPacket(bedrock
                ? new PlayerRotationPacket(view.yaw() + yaw, false, Math.clamp(view.pitch() + pitch, -90f, 90f), false)
                : new PlayerRotationPacket(yaw, true, pitch, true));
    }

    void blind(int ticks) { player.addEffect(new Potion(PotionEffect.BLINDNESS, 0, ticks)); }

    void hear(SoundEvent event, float volume, float pitch) {
        player.playSound(Sound.sound(event, Sound.Source.PLAYER, volume, pitch));
    }

    // ---- spawn protection --------------------------------------------------------------------------------------

    boolean isProtected() { return System.currentTimeMillis() < protectedUntil; }
    /** Any attack (shot, stab, throw) ends it early. */
    void dropProtection() { protectedUntil = 0; }

    // ---- weapons -----------------------------------------------------------------------------------------------

    Weapon held() { return loadout.at(player.getHeldSlot()); }

    /** The client reports a slot change before the server-side held slot updates, hence explicit slots. */
    void switched(int fromSlot, int toSlot) {
        loadout.at(fromSlot).holster(this);
        player.setLevel(loadout.at(toSlot).ammo());
    }

    void showAmmo() { player.setLevel(held().ammo()); }

    void showLoadout() {
        loadout.show(player);
        showAmmo();
    }

    /** Start a new life: full health, fresh weapons, brief protection. */
    void spawnAt(Pos at) {
        loadout.refill(this);
        protectedUntil = System.currentTimeMillis() + PROTECT_MS;
        player.heal();
        showLoadout();
        player.teleport(at);
    }
}
