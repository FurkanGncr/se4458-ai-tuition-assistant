#!/usr/bin/env bash
# Fills the placeholders in kong.yml from the environment, then starts Kong.
#   ${JWT_SECRET}       HS256 secret shared with tuition-api (required, at least 32 characters)
#   ${TUITION_API_URL}  upstream URL of tuition-api (default: http://tuition-api:8081, docker compose)
# Kong's jwt_secrets.secret field does not support vault references, so the secret is
# injected here at startup and never written into the repository.
set -euo pipefail

TEMPLATE=/kong/kong.yml
OUTPUT=/tmp/kong.yml

export TUITION_API_URL="${TUITION_API_URL:-http://tuition-api:8081}"

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
case "$TUITION_API_URL" in
  http://* | https://*) ;;
  *)
    echo "render-config: TUITION_API_URL must start with http:// or https://" >&2
    exit 1
    ;;
esac
case "$TUITION_API_URL" in
  *'"'* | *'\'* | *' '*)
    echo "render-config: TUITION_API_URL must not contain quotes, backslashes or spaces" >&2
    exit 1
    ;;
esac

# Plain string replacement (no regex), so any other character in the values is safe.
awk 'function repl(line, p, s,   i) {
       while ((i = index(line, p)) > 0) line = substr(line, 1, i - 1) s substr(line, i + length(p))
       return line
     }
     { $0 = repl($0, "${JWT_SECRET}", ENVIRON["JWT_SECRET"])
       $0 = repl($0, "${TUITION_API_URL}", ENVIRON["TUITION_API_URL"])
       print }' \
  "$TEMPLATE" > "$OUTPUT"
chmod 600 "$OUTPUT"

export KONG_DECLARATIVE_CONFIG="$OUTPUT"
exec /docker-entrypoint.sh kong docker-start
