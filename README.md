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

## Game: free-for-all gun deathmatch
60×60 arena, 10-minute rounds, instant respawn far from enemies, 2s spawn protection.

### Weapon models and sounds (optional resource pack)
`resourcepack/` holds 3D weapon models and gunshot sounds (sources and licence in `resourcepack/CREDITS.md`).
It is zipped into the server jar and served by the game server itself on container port 8080 (host `25567`).
Tell the server the address players' clients can reach, then start as usual:
```sh
RESOURCE_PACK_URL=http://<host-ip>:25567/pack.zip docker compose up -d --build
```
Clients are asked to accept the pack on join (required). Without `RESOURCE_PACK_URL` the game runs with vanilla
items and sounds. Bedrock clients do not get Java resource packs.

Bedrock players get a separate pack through Geyser instead: `./setup.sh` zips `bedrock/pack` plus the sounds into
`velocity/data/plugins/Geyser-Velocity/packs/dm.mcpack` and installs `bedrock/mappings.json` as Geyser custom item
mappings. Geyser pushes the pack on join; restart Velocity after re-running `setup.sh`. On Bedrock the weapons are
flat icons (rendered from the Java models), not 3D models. Regenerate `bedrock/` after changing the Java pack with
`python3 tools/bedrock.py .` (needs numpy and Pillow).

Code lives in `src/main/java/dev/pineple/minecraft/deathmatch/`:

| Class | Responsibility |
|---|---|
| `Match` | Rules and lifecycle: join/leave, damage and kill credit, respawn, round timer. |
| `Combatant` | One player in the match: hitbox, health, score, spawn protection, their `Loadout`. |
| `Weapon` | Interface for a hotbar slot. `Gun`, `Knife`, `Grenade` implement it and own their state. |
| `Gun.Spec` / `Grenade.Kind` | Tuning table for guns / what each grenade does on detonation. |
| `Arena` | The map: construction, spawn points, cover and line-of-sight queries. |
| `Hud` | Sidebar, round timer, kill feed. |
| `Controls` | Maps Minecraft input events to `Match` and `Weapon` calls. |
| `Ballistics` | Pure ray/bounce math, self-checked at boot. |
| `ResourcePack` | Serves the pack and maps weapon ids to its models and sounds, with vanilla fallback. |

| Slot | Item | Notes |
|---|---|---|
| 1-5 | Rifle / SMG / Shotgun / Sniper / Pistol | Left-click to fire (one shot per click), `F` to reload (auto when empty). Ammo = XP level number. Headshots 4×. |
| 6 | Knife | Left-click. |
| 7-9 | HE / Flashbang / Smoke | Left-click to throw; bounces off walls and floor, 1.6s fuse. One of each per life. |

- **Spray and recoil**: sustained fire widens spread and kicks the view up; jumping adds spread. Pause ~0.5s to reset.
- **Wall penetration**: bullets pass thin cover with reduced damage (kill feed shows `▒`). Planks are soft, stone/brick
  cost double, iron blocks are bulletproof. Shotgun does not penetrate.
- **Sniper scope**: right-click toggles zoom (also slows you). Unscoped shots are inaccurate. Needs the client's
  "FOV Effects" setting above 0.
- **Smoke** blocks vision only and is invisible to clients on "Particles: Minimal". **Flash** uses the Blindness effect.

## Security / caveats
- Backend Minestom port is not exposed on the host. Velocity authenticates and forwards identity using `forwarding.secret`.
- Snapshot `26_3-SNAPSHOT` changes over time. Use `--no-cache` to refresh dependency only when you intend to.
- The build and Switch connection have not been executed/tested here; API changes or unsupported protocol behavior may need adjustments.
- Stop: `docker compose down` (does not remove `velocity/data`).
- The arena is generated in code at boot; nothing is persisted.
- If you use an older Java runtime/unsupported image, use a Java 25-capable host/image.

Sources:
- https://github.com/Minestom/Minestom/pull/3369
- https://minestom.net/docs/setup/dependencies
- https://minestom.net/docs/setup/your-first-server
- https://minestom.net/docs/authentication/proxies
- https://geysermc.org/wiki/geyser/setup/
