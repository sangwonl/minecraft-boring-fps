package dev.pineple.minecraft.deathmatch;

import net.minestom.server.entity.Player;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.entity.EntityAttackEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerChangeHeldSlotEvent;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerHandAnimationEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.event.player.PlayerSwapItemEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;

/** Translates Minecraft input events into match and weapon calls. No game rules live here. */
final class Controls {
    private Controls() {}

    static void bind(Match match, GlobalEventHandler events) {
        // Connection lifecycle
        events.addListener(AsyncPlayerConfigurationEvent.class, e -> {
            e.setSpawningInstance(match.arena().world());
            e.getPlayer().setRespawnPoint(match.arena().randomSpawn());
            ResourcePack.sendTo(e.getPlayer());
        });
        events.addListener(PlayerSpawnEvent.class, e -> {
            if (e.isFirstSpawn()) match.join(e.getPlayer());
        });
        events.addListener(PlayerDisconnectEvent.class, e -> match.leave(e.getPlayer()));

        // Weapon input: left-click (arm swing), right-click, F, hotbar switch, left-click on a player
        events.addListener(PlayerHandAnimationEvent.class, e -> {
            Combatant c = match.combatant(e.getPlayer());
            if (c != null && e.getHand() == PlayerHand.MAIN) c.held().primary(c, match);
        });
        events.addListener(PlayerUseItemEvent.class, e -> {
            e.setCancelled(true);
            Combatant c = match.combatant(e.getPlayer());
            if (c != null && e.getHand() == PlayerHand.MAIN) c.held().secondary(c, match);
        });
        events.addListener(PlayerSwapItemEvent.class, e -> {
            e.setCancelled(true);
            Combatant c = match.combatant(e.getPlayer());
            if (c != null) c.held().reload(c);
        });
        events.addListener(PlayerChangeHeldSlotEvent.class, e -> {
            Combatant c = match.combatant(e.getPlayer());
            if (c != null) c.switched(e.getOldSlot(), e.getNewSlot());
        });
        events.addListener(EntityAttackEvent.class, e -> {
            if (!(e.getEntity() instanceof Player attacker) || !(e.getTarget() instanceof Player target)) return;
            Combatant a = match.combatant(attacker), t = match.combatant(target);
            if (a != null && t != null) a.held().strike(a, t, match);
        });

        // The loadout is fixed: no dropping or rearranging
        events.addListener(ItemDropEvent.class, e -> e.setCancelled(true));
        events.addListener(InventoryPreClickEvent.class, e -> e.setCancelled(true));
    }
}
