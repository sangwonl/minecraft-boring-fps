package dev.pineple.minecraft.deathmatch;

import net.minestom.server.MinecraftServer;

/** Entry point: wires the arena, the match and the player controls together. */
public final class Deathmatch {
    private Deathmatch() {}

    public static void start() {
        Ballistics.selfCheck();
        ResourcePack.serve(); // before any weapon item is created
        Match match = new Match(new Arena());
        Controls.bind(match, MinecraftServer.getGlobalEventHandler());
        match.start();
    }
}
