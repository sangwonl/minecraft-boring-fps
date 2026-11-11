#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

secret_file=velocity/config/forwarding.secret

if [[ -d "$secret_file" ]]; then
  if ! rmdir "$secret_file"; then
    echo "Error: $secret_file is a directory and is not empty or still in use." >&2
    echo 'Stop Docker Compose and inspect that path before removing it.' >&2
    exit 1
  fi
  echo "Removed empty directory at $secret_file."
fi

if [[ -e "$secret_file" && ! -f "$secret_file" ]]; then
  echo "Error: $secret_file exists but is not a regular file." >&2
  exit 1
fi

if [[ ! -f "$secret_file" ]]; then
  umask 077
  openssl rand -hex 32 > "$secret_file"
  echo 'Generated forwarding secret.'
else
  echo 'Using existing forwarding secret.'
fi

# Docker Desktop cannot create a file mountpoint inside the bind-mounted velocity/data on the fly,
# so the two files mounted over it must already exist there.
mkdir -p velocity/data
for mountpoint in velocity/data/velocity.toml velocity/data/forwarding.secret; do
  [[ -e "$mountpoint" ]] || : > "$mountpoint"
done

# Bedrock players get their own pack (icons + sounds) and item mappings through Geyser.
geyser=velocity/data/plugins/Geyser-Velocity
stage=$(mktemp -d)
mkdir -p "$geyser/packs" "$geyser/custom_mappings" "$stage/sounds/dm"
cp -R bedrock/pack/. "$stage"
cp resourcepack/assets/dm/sounds/*.ogg "$stage/sounds/dm/"
rm -f "$geyser/packs/dm.mcpack"
(cd "$stage" && zip -qr - .) > "$geyser/packs/dm.mcpack"
cp bedrock/mappings.json "$geyser/custom_mappings/dm.json"
rm -rf "$stage"
echo 'Installed Bedrock pack and item mappings for Geyser.'

echo 'Run: docker compose up -d --build'
echo 'Java: host-ip:25566 | Bedrock: host-ip:19133'
