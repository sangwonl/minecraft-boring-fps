package dev.pineple.minecraft;

import net.minestom.server.MinecraftServer;
import net.minestom.server.Auth;
import dev.pineple.minecraft.deathmatch.Deathmatch;

import java.nio.file.Files;
import java.nio.file.Path;

public final class HelloMinestom {
    public static void main(String[] args) throws Exception {
        String secret = Files.readString(Path.of(System.getenv("VELOCITY_SECRET_FILE"))).trim();
        MinecraftServer server = MinecraftServer.init(new Auth.Velocity(secret));

        Deathmatch.start();

        server.start("0.0.0.0", 25565);
    }
}
