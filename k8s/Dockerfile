# ── zota-server K8s Dockerfile ──
# Build: mvn package -DskipTests -pl hawkbit-monolith/hawkbit-update-server -am
# Then:  docker build -f k8s/Dockerfile -t zota-server .

FROM harbor.intra.zeron.ai/library/eclipse-temurin:21-jre-alpine

ENV TZ=Asia/Shanghai

ARG APP_DIR=/app
WORKDIR ${APP_DIR}

RUN addgroup -S hawkbit_group && adduser -D hawkbit -G hawkbit_group \
  && mkdir -p artifactrepo && chown -R hawkbit artifactrepo

COPY hawkbit-monolith/hawkbit-update-server/target/hawkbit-update-server-*-SNAPSHOT.jar /app/server.jar
COPY docker-entrypoint.sh /app/docker-entrypoint.sh

RUN chmod +x /app/docker-entrypoint.sh \
  && chown hawkbit:hawkbit_group /app/server.jar /app/docker-entrypoint.sh

USER hawkbit

ENV zota_JAR=/app/server.jar
ENV PROFILES=postgresql
ENV SERVER_PORT=8090

EXPOSE 8090

ENTRYPOINT ["/app/docker-entrypoint.sh"]
