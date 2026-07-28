# syntax=docker/dockerfile:1

FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl workflow-app -am -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system workflow \
    && useradd --system --gid workflow --home-dir /app --shell /usr/sbin/nologin workflow \
    && mkdir -p /app/logs /app/uploads \
    && chown -R workflow:workflow /app

ENV SERVER_PORT=8080
ENV FILE_STORAGE_PATH=/app/uploads
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

COPY --from=build --chown=workflow:workflow \
    /app/workflow-app/target/workflow-server-1.0.0.jar \
    /app/workflow-server.jar

USER workflow
EXPOSE 8080 9090

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD curl --fail --silent --show-error http://127.0.0.1:8080/livez >/dev/null || exit 1

ENTRYPOINT ["java", "-jar", "/app/workflow-server.jar"]
