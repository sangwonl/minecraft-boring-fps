package dev.pineple.minecraft;

import net.minestom.server.MinecraftServer;
import net.minestom.server.Auth;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.kyori.adventure.text.Component;

import java.nio.file.Files;
import java.nio.file.Path;

public final class HelloMinestom {
    public static void main(String[] args) throws Exception {
        String secret = Files.readString(Path.of(System.getenv("VELOCITY_SECRET_FILE"))).trim();
        MinecraftServer server = MinecraftServer.init(new Auth.Velocity(secret));

        InstanceContainer world = MinecraftServer.getInstanceManager().createInstanceContainer();
        world.setGenerator(unit -> unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK));

        MinecraftServer.getGlobalEventHandler().addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(world);
            event.getPlayer().setRespawnPoint(new Pos(0, 42, 0));
        });
        MinecraftServer.getGlobalEventHandler().addListener(PlayerSpawnEvent.class, event -> {
            if (event.isFirstSpawn()) {
                event.getPlayer().sendMessage(Component.text("Hello from Minestom 26.3 snapshot!"));
            }
        });

        server.start("0.0.0.0", 25565);
    }
}
