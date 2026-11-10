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

echo 'Run: docker compose up -d --build'
echo 'Java: host-ip:25566 | Bedrock: host-ip:19133'
