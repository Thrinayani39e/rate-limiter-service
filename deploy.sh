#!/usr/bin/env bash
set -euo pipefail

# ── config ────────────────────────────────────────────────────────────────────
GITHUB_USERNAME="${GITHUB_USERNAME:-}"
STACK_NAME="rate-limiter"
SERVICE_NAME="rate-limiter-app"
NETWORK_NAME="rate-limiter-net"

# ── helpers ───────────────────────────────────────────────────────────────────
log() { printf "==> %s\n" "$*"; }
err() { printf "!!  %s\n" "$*" >&2; exit 1; }

# ── require GITHUB_USERNAME ───────────────────────────────────────────────────
if [[ -z "$GITHUB_USERNAME" ]]; then
  err "Set GITHUB_USERNAME before running: export GITHUB_USERNAME=yourhandle"
fi

REGISTRY="ghcr.io/${GITHUB_USERNAME}"
IMAGE="${REGISTRY}/${SERVICE_NAME}"
TAG="$(git rev-parse --short HEAD 2>/dev/null || date +%s)"
FULL_IMAGE="${IMAGE}:${TAG}"

# ── 1. initialise swarm (idempotent) ──────────────────────────────────────────
if ! docker info --format '{{.Swarm.LocalNodeState}}' 2>/dev/null | grep -q "active"; then
  log "Initialising Docker Swarm..."
  docker swarm init
else
  log "Swarm already active — skipping init"
fi

# ── 2. create overlay network (idempotent) ────────────────────────────────────
if ! docker network inspect "$NETWORK_NAME" &>/dev/null; then
  log "Creating overlay network: $NETWORK_NAME"
  docker network create --driver overlay --attachable "$NETWORK_NAME"
else
  log "Network $NETWORK_NAME already exists — skipping"
fi

# ── 3. authenticate with ghcr.io ──────────────────────────────────────────────
log "Logging in to ghcr.io as $GITHUB_USERNAME"
echo "Paste your GitHub Personal Access Token (needs write:packages scope):"
docker login ghcr.io -u "$GITHUB_USERNAME" --password-stdin

# ── 4. build & push ───────────────────────────────────────────────────────────
log "Building image for linux/amd64: $FULL_IMAGE"
docker buildx build \
  --platform linux/amd64 \
  -t "$FULL_IMAGE" \
  -t "${IMAGE}:latest" \
  --push \
  .

# ── 5. deploy stack ───────────────────────────────────────────────────────────
log "Deploying stack: $STACK_NAME"
IMAGE="$FULL_IMAGE" docker stack deploy \
  --compose-file compose.swarm.yml \
  --with-registry-auth \
  "$STACK_NAME"

log "Waiting for app service to converge..."
sleep 10
docker service ls --filter "label=com.docker.stack.namespace=$STACK_NAME"

printf "\n✓ Deployed. Check health:\n"
printf "  docker service ps %s_%s\n" "$STACK_NAME" "$SERVICE_NAME"
printf "  curl http://localhost:8080/api/rate-limit/check\n"
printf "  curl http://localhost:8080/swagger-ui/index.html\n"
printf "\nImage: %s\n" "$FULL_IMAGE"
