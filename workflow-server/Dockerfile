FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build
WORKDIR /app
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl workflow-app -am -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c
WORKDIR /app

RUN apk upgrade --no-cache \
    && apk add --no-cache curl \
    && addgroup -S -g 10001 workflow \
    && adduser -S -D -H -u 10001 -h /app -s /sbin/nologin -G workflow workflow \
    && mkdir -p /app/logs /app/uploads \
    && chown -R workflow:workflow /app

ENV SERVER_PORT=8080
ENV FILE_STORAGE_PATH=/app/uploads
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

COPY --from=build --chown=workflow:workflow \
    /app/workflow-app/target/workflow-server-1.0.0.jar \
    /app/workflow-server.jar
COPY --from=build --chown=workflow:workflow \
    /app/workflow-db-migrator/target/workflow-db-migrator-1.0.0-exec.jar \
    /app/workflow-db-migrator.jar

USER 10001:10001
EXPOSE 8080 9090

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/livez >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/workflow-server.jar"]
