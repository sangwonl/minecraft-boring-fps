package dev.pineple.minecraft.deathmatch;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.minestom.server.entity.Player;
import net.minestom.server.sound.SoundEvent;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/**
 * The optional weapon models and sounds (namespace "dm", built from /resourcepack into pack.zip).
 * Active only when RESOURCE_PACK_URL says where clients can download it; otherwise everything
 * falls back to vanilla items and sounds, so the game still runs without it.
 */
final class ResourcePack {
    private ResourcePack() {}

    private static final String NAMESPACE = "dm:";
    private static ResourcePackRequest request;

    /** Serves pack.zip over HTTP and prepares the request sent to joining players. */
    static void serve() {
        String url = System.getenv("RESOURCE_PACK_URL");
        if (url == null || url.isBlank()) {
            System.out.println("RESOURCE_PACK_URL not set: using vanilla items and sounds");
            return;
        }
        try (InputStream in = ResourcePack.class.getResourceAsStream("/pack.zip")) {
            if (in == null) throw new IOException("pack.zip missing from the jar");
            byte[] zip = in.readAllBytes();
            byte[] sha1 = MessageDigest.getInstance("SHA-1").digest(zip);
            int port = Integer.parseInt(System.getenv().getOrDefault("RESOURCE_PACK_PORT", "8080"));
            HttpServer http = HttpServer.create(new InetSocketAddress(port), 0);
            http.createContext("/pack.zip", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, zip.length);
                try (var body = exchange.getResponseBody()) {
                    body.write(zip);
                }
            });
            http.start();
            request = ResourcePackRequest.resourcePackRequest()
                    .packs(ResourcePackInfo.resourcePackInfo(UUID.nameUUIDFromBytes(sha1), URI.create(url),
                            HexFormat.of().formatHex(sha1)))
                    .required(true) // without it every weapon would render as a missing model
                    .prompt(Component.text("Weapon models and sounds for the deathmatch"))
                    .build();
            System.out.println("Serving resource pack on :" + port + ", clients fetch " + url);
        } catch (Exception e) {
            throw new IllegalStateException("cannot serve the resource pack", e);
        }
    }

    static boolean active() { return request != null; }

    static void sendTo(Player player) {
        if (request != null) player.sendResourcePacks(request);
    }

    /** Item model id for the item_model component. */
    static String model(String id) { return NAMESPACE + id; }

    /** The pack's sound when active, otherwise the given vanilla one. */
    static SoundEvent sound(String id, SoundEvent vanilla) {
        return active() ? SoundEvent.of(NAMESPACE + id, null) : vanilla;
    }
}
