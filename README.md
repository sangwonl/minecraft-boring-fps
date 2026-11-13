# Minestom FPS Deathmatch

A free-for-all FPS deathmatch server for Minecraft, built with Minestom, Velocity, and Geyser.
Supports Java and Bedrock Edition and runs independently of any existing Paper server.

https://github.com/user-attachments/assets/ff3e583c-7e79-4114-9afe-3409f340bc3b

\[Captured by [Snapr](https://snapr.pineple.com)\]

## Features

- 60×60 arena, 10-minute rounds, instant respawn, and 2-second spawn protection
- Five guns (rifle, SMG, shotgun, sniper, pistol), a knife, and HE, flash, and smoke grenades
- Recoil, bullet spread, headshots, thin-cover penetration, and sniper scope
- 3D weapon models and sounds for Java; weapon icons for Bedrock

## Getting Started

Requires Docker Compose, `openssl`, and internet access for the initial build and plugin downloads.

```sh
# Set up secrets and generate the Bedrock resource pack
./setup.sh

# Build and start in the background
docker compose up -d --build

# Follow server logs
docker compose logs -f minestom velocity
```

| Client | Server address |
|---|---|
| Java Edition | `<server-ip>:25566` |
| Bedrock Edition | `<server-ip>:19133` (TCP and UDP) |

Switch may require a LAN discovery/transfer bridge such as Bedrock Pass.

To stop the server:

```sh
docker compose down
```

### Java Resource Pack

Set an address that players' clients can reach. Players must accept the pack when joining.
Without this setting, the game uses vanilla items and sounds.

```sh
RESOURCE_PACK_URL=http://<server-ip>:25567/pack.zip docker compose up -d --build
```

Expose port `25567` when using the pack. For Bedrock, `setup.sh` installs a separate pack distributed by Geyser. Restart Velocity after regenerating it.

## Controls

| Slot | Weapon | Controls |
|---|---|---|
| 1–5 | Rifle / SMG / Shotgun / Sniper / Pistol | Left-click to fire; `F` to reload; auto-reload when empty |
| 6 | Knife | Left-click to attack |
| 7–9 | HE / Flashbang / Smoke | Left-click to throw; one of each per life |

Ammo is displayed as the XP level number. Right-click with the sniper to toggle its scope; the client's **FOV Effects** setting must be above zero for zoom to appear.

## Configuration and Notes

- Plugin configuration is stored in `velocity/data`. Check that Geyser uses internal port `19132` and `bedrock.transport: nethernet`.
- Keep the empty `[forced-hosts]` section in `velocity/config/velocity.toml`.
- The Minestom backend is not exposed to the host. Velocity forwards player identity using the shared `forwarding.secret`.
- The `26_3-SNAPSHOT` dependency may change over time. A Java 25-capable environment is required.
- The arena is generated at startup; map and game state are not persisted. Stopping the stack preserves `velocity/data`.
- The build and Switch connection have not been verified; API or protocol changes may require adjustments.

### Fixing `forwarding.secret: Is a directory`

If Docker created the secret path as a directory, run the following. `rmdir` removes only an empty directory; if it fails, inspect the contents before removing anything.

```sh
docker compose down
./setup.sh
if [ -d velocity/data/forwarding.secret ]; then
  rmdir velocity/data/forwarding.secret || exit 1
fi
docker compose up -d --build
```

Game code lives in `src/main/java/dev/pineple/minecraft/deathmatch/`; the Java resource pack is in `resourcepack/`. See [CREDITS.md](resourcepack/CREDITS.md) for asset sources and licenses.
