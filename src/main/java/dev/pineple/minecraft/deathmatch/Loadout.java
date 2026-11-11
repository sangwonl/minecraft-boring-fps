package dev.pineple.minecraft.deathmatch;

import net.minestom.server.entity.Player;

import java.util.List;

/** One combatant's nine hotbar slots. Every slot holds a weapon, so {@link #at} never returns null. */
final class Loadout {
    private final List<Weapon> slots;

    private Loadout(List<Weapon> slots) {
        if (slots.size() != 9) throw new IllegalArgumentException("a loadout fills all 9 hotbar slots");
        this.slots = slots;
    }

    static Loadout standard() {
        return new Loadout(List.of(
                new Gun(Gun.Spec.RIFLE), new Gun(Gun.Spec.SMG), new Gun(Gun.Spec.SHOTGUN),
                new Gun(Gun.Spec.SNIPER), new Gun(Gun.Spec.PISTOL), new Knife(),
                new Grenade(Grenade.Kind.HE), new Grenade(Grenade.Kind.FLASH), new Grenade(Grenade.Kind.SMOKE)));
    }

    Weapon at(int slot) { return slots.get(slot); }

    void refill(Combatant owner) {
        for (Weapon weapon : slots) {
            weapon.holster(owner);
            weapon.refill();
        }
    }

    void show(Player player) {
        for (int i = 0; i < slots.size(); i++) player.getInventory().setItemStack(i, slots.get(i).item());
    }
}
