# Minestom 26.3 snapshot + Geyser (independent test)

This project is **not connected to your existing Paper server**.

## Prerequisites
Docker Compose, openssl, internet for Maven snapshot/plugin/image downloads.

## Start
```sh
./setup.sh
docker compose up -d --build
docker compose logs -f minestom velocity
```

Run `./setup.sh` before the first `docker compose up`. The secret is a file bind mount; if Docker previously created `velocity/config/forwarding.secret` as a directory, stop the stack and rerun the setup script. It removes that path only when the directory is empty.

If Velocity still reports `/server/forwarding.secret: Is a directory`, the bad path is also persisted under `velocity/data`. After stopping the stack and running `./setup.sh`, remove only that empty directory, then start again:
```sh
docker compose down
./setup.sh
if [ -d velocity/data/forwarding.secret ]; then
  rmdir velocity/data/forwarding.secret || exit 1
fi
docker compose up -d --build
```
The `rmdir` command fails safely if the path is not empty; inspect it before removing anything in that case. This preserves the rest of `velocity/data`.

Java Edition: `<host-ip>:25566`.
Bedrock Edition: `<host-ip>:19133` (TCP and UDP). For Switch, a LAN discovery/transfer bridge such as Bedrock Pass may be needed.

## First-start Geyser config
Geyser-Velocity and Floodgate-Velocity are downloaded on proxy startup.
The compose maps external `19133` to **internal default `19132`** for both TCP and UDP.

Compose mounts `velocity/config/velocity.toml` directly at `/server/velocity.toml`, which is the config file Velocity loads from its working directory. The forwarding secret is mounted at `/server/forwarding.secret` and shared with Minestom.

Keep the empty `[forced-hosts]` section in `velocity.toml`. Omitting it makes Velocity use sample mappings to `lobby`, `factions`, and `minigames`, which are not defined in this single-backend setup.

Inspect the generated Geyser config to confirm `bedrock.port: 19132` and `bedrock.transport: nethernet` (for recent Bedrock versions). Plugin configuration lives under `velocity/data`. Floodgate is normally auto-detected on the Velocity plugin when the Java address is `auto`; otherwise set `auth-type: floodgate` if present. Restart Velocity after changes.

## Security / caveats
- Backend Minestom port is not exposed on the host. Velocity authenticates and forwards identity using `forwarding.secret`.
- Snapshot `26_3-SNAPSHOT` changes over time. Use `--no-cache` to refresh dependency only when you intend to.
- The build and Switch connection have not been executed/tested here; API changes or unsupported protocol behavior may need adjustments.
- Stop: `docker compose down` (does not remove `velocity/data`).
- The Minestom world is a minimal flat ephemeral world; it is not persisted.
- If you use an older Java runtime/unsupported image, use a Java 25-capable host/image.

Sources:
- https://github.com/Minestom/Minestom/pull/3369
- https://minestom.net/docs/setup/dependencies
- https://minestom.net/docs/setup/your-first-server
- https://minestom.net/docs/authentication/proxies
- https://geysermc.org/wiki/geyser/setup/
