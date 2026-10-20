#!/usr/bin/env bash
# Prod profile chạy trong container thật, nhưng DB/keys chỉ của CI; không đọc .env.
# Gọi: bash tools/ci-container-smoke.sh IMAGE jdbc:postgresql://localhost:5432/tankbattle_smoke
set -euo pipefail

image="${1:?Thiếu tên image}"
database_url="${2:?Thiếu URL database test}"
# Guard trước mọi kết nối: chỉ loopback và đúng DB smoke, không nhận host Neon.
if [[ ! "$database_url" =~ ^jdbc:postgresql://(localhost|127\.0\.0\.1):[0-9]+/tankbattle_smoke$ ]]; then
  echo "Từ chối DB ngoài loopback/tankbattle_smoke." >&2
  exit 1
fi
key_dir="$(mktemp -d)"
container="tankbattle-smoke-${RANDOM}"
cleanup() {
  docker rm -f "$container" >/dev/null 2>&1 || true
  rm -f "$key_dir/jwt-private.pem" "$key_dir/jwt-public.pem" "$key_dir/error.json"
  rmdir "$key_dir"
}
trap cleanup EXIT

java tools/GenerateJwtKeys.java "$key_dir"
# Container uid/gid 1000 phải đọc được key test một lần qua bind mount.
chmod 755 "$key_dir"
chmod 444 "$key_dir/"*.pem

docker run --detach --name "$container" --network host \
  --mount "type=bind,source=$key_dir,target=/etc/secrets,readonly" \
  --env SPRING_PROFILES_ACTIVE=prod \
  --env PORT=18080 \
  --env "SPRING_DATASOURCE_URL=$database_url" \
  --env "SPRING_FLYWAY_URL=$database_url" \
  --env SPRING_DATASOURCE_USERNAME=ci_test_only \
  --env SPRING_DATASOURCE_PASSWORD=ci_test_only \
  --env AUTH_ISSUER=https://ci.example.invalid \
  --env JWT_PRIVATE_KEY=file:/etc/secrets/jwt-private.pem \
  --env JWT_PUBLIC_KEY=file:/etc/secrets/jwt-public.pem \
  --env API_DOCS_ENABLED=false \
  --env BOOTSTRAP_ADMIN_PASSWORD=BootstrapSmoke!2026 \
  --env BOOTSTRAP_ADMIN_ENABLED=true \
  --env BOOTSTRAP_ADMIN_USERNAME=admin \
  "$image" >/dev/null

# Chờ Flyway + JPA + hai security chain khởi động, tối đa khoảng 3 phút.
ready=false
for attempt in $(seq 1 90); do
  if curl --fail --silent --connect-timeout 1 --max-time 3 http://127.0.0.1:18080/actuator/health/readiness \
    | python3 -c 'import json,sys; sys.exit(0 if json.load(sys.stdin)["status"] == "UP" else 1)' 2>/dev/null; then
    ready=true
    break
  fi
  if [[ "$(docker inspect --format '{{.State.Running}}' "$container")" != "true" ]]; then
    break
  fi
  sleep 2
done
if [[ "$ready" != true ]]; then
  docker logs "$container"
  echo "Container prod profile không sẵn sàng với DB test." >&2
  exit 1
fi

# Không cấu hình Google vẫn cần trả lỗi auth đúng HTTP status, code và ngôn ngữ.
curl --silent --output "$key_dir/error.json" --write-out '%{http_code}' \
  --header 'Accept-Language: en' http://127.0.0.1:18080/api/auth/me \
  | python3 -c 'import sys; assert sys.stdin.read() == "401"'
python3 -c 'import json,sys; data=json.load(open(sys.argv[1])); assert data["code"]=="AUTH_001" and data["locale"]=="en"' "$key_dir/error.json"

docker stop --time 30 "$container" >/dev/null
echo "CONTAINER_PROD_PROFILE_SMOKE_OK"
