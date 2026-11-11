package dev.pineple.minecraft.deathmatch;

import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

final class Knife implements Weapon {
    private static final float DAMAGE = 11;
    private static final double REACH = 4;
    private static final int COOLDOWN_MS = 500; // the server does not enforce the vanilla attack cooldown

    private long nextStrike;

    @Override public ItemStack item() { return Weapon.named(Material.IRON_SWORD, "Knife", "knife"); }

    @Override public void strike(Combatant user, Combatant target, Match match) {
        long now = System.currentTimeMillis();
        if (now < nextStrike || user.player().getDistanceSquared(target.player()) >= REACH * REACH) return;
        nextStrike = now + COOLDOWN_MS;
        user.dropProtection();
        match.damage(user, target, DAMAGE, "Knife", false);
    }
}
