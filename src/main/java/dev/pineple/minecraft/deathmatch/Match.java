package dev.pineple.minecraft.deathmatch;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Player;
import net.minestom.server.sound.SoundEvent;
import net.minestom.server.timer.TaskSchedule;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The rules and lifecycle of a free-for-all deathmatch: who is playing, what damage means,
 * who gets the kill, when a round ends. Weapons report damage here; they never decide outcomes themselves.
 */
final class Match {
    private static final int ROUND_SECONDS = 600;

    private final Arena arena;
    private final Hud hud;
    private final Map<UUID, Combatant> combatants = new ConcurrentHashMap<>();
    private int secondsLeft = ROUND_SECONDS;

    Match(Arena arena) {
        this.arena = arena;
        this.hud = new Hud(arena.world());
    }

    Arena arena() { return arena; }
    Collection<Combatant> combatants() { return combatants.values(); }

    /** Null until the player has joined, and again after they leave. */
    Combatant combatant(Player player) { return combatants.get(player.getUuid()); }

    void start() {
        MinecraftServer.getSchedulerManager().buildTask(this::tickSecond).repeat(TaskSchedule.seconds(1)).schedule();
    }

    // ---- players come and go -----------------------------------------------------------------------------------

    void join(Player player) {
        Combatant c = new Combatant(player);
        combatants.put(player.getUuid(), c);
        hud.add(c);
        respawn(c);
    }

    void leave(Player player) {
        Combatant c = combatants.remove(player.getUuid());
        if (c == null) return;
        hud.remove(c);
        combatants.values().forEach(hud::list); // a freed sidebar line goes to someone who was unlisted
    }

    // ---- combat rules ------------------------------------------------------------------------------------------

    /** Every source of harm goes through here. attacker == victim is self-damage (own grenade). */
    void damage(Combatant attacker, Combatant victim, float amount, String cause, boolean headshot) {
        boolean attackerLeft = combatant(attacker.player()) != attacker; // e.g. their grenade outlived them
        if (attackerLeft || victim.isProtected()) return;
        attacker.hear(SoundEvent.ENTITY_ARROW_HIT_PLAYER, 0.6f, headshot ? 2f : 1f);
        if (victim.survives(amount)) {
            victim.wound(attacker, amount);
            return;
        }
        if (attacker == victim) {
            hud.suicide(victim); // no kill credit
        } else {
            attacker.addKill();
            attacker.hear(SoundEvent.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
            hud.score(attacker);
            hud.kill(attacker, victim, cause, headshot);
        }
        respawn(victim); // health never reaches 0: no death screen, instant respawn
    }

    private void respawn(Combatant c) {
        c.spawnAt(arena.spawnAwayFrom(combatants.values().stream()
                .filter(other -> other != c).map(other -> other.player().getPosition()).toList()));
    }

    // ---- round -------------------------------------------------------------------------------------------------

    private void tickSecond() {
        if (--secondsLeft <= 0) endRound();
        hud.time(secondsLeft, ROUND_SECONDS);
    }

    private void endRound() {
        combatants.values().stream().max(Comparator.comparingInt(Combatant::kills)).ifPresent(hud::winner);
        // Everyone moves at once, so spread them over spawns already handed out rather than current positions.
        List<Pos> taken = new ArrayList<>();
        for (Combatant c : combatants.values()) {
            c.resetKills();
            hud.score(c);
            Pos spawn = arena.spawnAwayFrom(taken);
            taken.add(spawn);
            c.spawnAt(spawn);
        }
        secondsLeft = ROUND_SECONDS;
    }
}
