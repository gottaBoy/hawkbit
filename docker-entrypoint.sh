#!/bin/sh
# Start zota monolith jar (e.g. zota-update-server-0-SNAPSHOT.jar).
# Runs in foreground for Docker (JVM stays PID 1).
#
# Build: mvn -pl zota-monolith/zota-update-server -am package
#
# Images based on alpine/jre rarely ship bash — keep POSIX sh-only.

set -eu

JAR="${zota_JAR:-zota-update-server.jar}"
if ! test -f "$JAR"; then
  echo "[zota-entrypoint] ERROR: jar not found: $JAR"
  echo "[zota-entrypoint] cwd=$(pwd)"
  echo "[zota-entrypoint] set zota_JAR to the built jar path" >&2
  exit 1
fi

if test "${JAVA_DIAG:-}" = "1"; then
  java -version 2>&1 || true
fi

JAVA_XMS="${X_MS:-${JAVA_XMS:-512m}}"
JAVA_XMX="${X_MX:-${JAVA_XMX:-1g}}"
XX_MAX_META="${XX_MAX_METASPACE_SIZE:-250m}"
XX_META="${XX_METASPACE_SIZE:-250m}"
XSS_STACK="${XSS:-300k}"
GC="${GC:-G1}"

ACTIVE="${SPRING_PROFILES_ACTIVE:-postgresql}"
if test -z "$ACTIVE"; then
  ACTIVE="${PROFILES:-h2}"
fi
export SPRING_PROFILES_ACTIVE="$ACTIVE"

PROFILE_ARG="-Dspring.profiles.active=$SPRING_PROFILES_ACTIVE"

PORT_ARGS=""
if test -n "${SERVER_PORT:-8090}"; then
  PORT_ARGS="-Dserver.port=$SERVER_PORT"
fi

echo "[zota-entrypoint] jar=$JAR spring.profiles.active=$SPRING_PROFILES_ACTIVE"

# shellcheck disable=SC2086
exec java ${JAVA_OPTS-} ${PROFILE_ARG} ${PORT_ARGS} \
  -server \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.util.concurrent=ALL-UNNAMED \
  --add-opens java.base/java.io=ALL-UNNAMED \
  --add-opens java.base/java.net=ALL-UNNAMED \
  --add-opens java.base/java.text=ALL-UNNAMED \
  --add-opens java.base/java.math=ALL-UNNAMED \
  --add-opens java.scripting/javax.script=ALL-UNNAMED \
  --add-opens java.base/java.time=ALL-UNNAMED \
  "-Xms$JAVA_XMS" \
  "-Xmx$JAVA_XMX" \
  "-XX:MaxMetaspaceSize=$XX_MAX_META" \
  "-XX:MetaspaceSize=$XX_META" \
  "-Xss$XSS_STACK" \
  "-XX:+Use${GC}GC" \
  -XX:+UseStringDeduplication \
  -XX:+UseCompressedOops \
  -XX:+HeapDumpOnOutOfMemoryError \
  -Djava.security.egd=file:/dev/./urandom \
  -jar "$JAR" "$@"
