#!/usr/bin/env bash
# Fills the ${JWT_SECRET} placeholder in kong.yml from the environment, then starts Kong.
# Kong's jwt_secrets.secret field does not support vault references, so the secret is
# injected here at startup and never written into the repository.
set -euo pipefail

TEMPLATE=/kong/kong.yml
OUTPUT=/tmp/kong.yml

if [ -z "${JWT_SECRET:-}" ] || [ "${#JWT_SECRET}" -lt 32 ]; then
  echo "render-config: JWT_SECRET must be set and at least 32 characters long" >&2
  exit 1
fi
case "$JWT_SECRET" in
  *'"'* | *'\'*)
    echo "render-config: JWT_SECRET must not contain double quotes or backslashes" >&2
    exit 1
    ;;
esac

# Plain string replacement (no regex), so any other character in the secret is safe.
awk 'BEGIN { s = ENVIRON["JWT_SECRET"]; p = "${JWT_SECRET}" }
     { while ((i = index($0, p)) > 0) $0 = substr($0, 1, i - 1) s substr($0, i + length(p)); print }' \
  "$TEMPLATE" > "$OUTPUT"
chmod 600 "$OUTPUT"

export KONG_DECLARATIVE_CONFIG="$OUTPUT"
exec /docker-entrypoint.sh kong docker-start
