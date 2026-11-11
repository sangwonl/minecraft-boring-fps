package dev.pineple.minecraft.deathmatch;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minestom.server.component.DataComponents;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;

/**
 * Something that sits in a hotbar slot. Each combatant owns their own instances, so a weapon keeps its own
 * state (ammo, reload, scope...). Controls call these without knowing which kind of weapon is held.
 */
interface Weapon {
    /** What the slot shows right now (AIR once a grenade is thrown). */
    ItemStack item();

    /** Left-click in the air or at anything. */
    default void primary(Combatant user, Match match) {}

    /** Right-click. Repeats every 200ms while held. */
    default void secondary(Combatant user, Match match) {}

    /** Left-click on another combatant. */
    default void strike(Combatant user, Combatant target, Match match) {}

    default void reload(Combatant user) {}

    /** The user switched away from it, or died holding it. */
    default void holster(Combatant user) {}

    /** Back to factory state for a new life. */
    default void refill() {}

    /** Number shown above the hotbar while held. */
    default int ammo() { return 0; }

    /** A named hotbar item. With the resource pack active it also gets the pack's 3D model for `modelId`. */
    static ItemStack named(Material material, String name, String modelId) {
        ItemStack stack = ItemStack.of(material).withCustomName(
                Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        return ResourcePack.active() ? stack.with(DataComponents.ITEM_MODEL, ResourcePack.model(modelId)) : stack;
    }
}
