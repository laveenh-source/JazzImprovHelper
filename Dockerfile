# syntax=docker/dockerfile:1
#
# Multi-stage build: compile with the full JDK + Maven, then ship only a slim JRE and the jar.
# The final image contains no compiler, no Maven, no source code, and runs as a non-root user.

ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre-alpine

# ---------- stage 1: build ----------
FROM ${MAVEN_IMAGE} AS build
WORKDIR /build

# Dependencies first, so editing source code does not re-download the internet.
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src ./src
# Tests run in CI (they need Docker for Testcontainers); the image build only packages.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package

# ---------- stage 2: run ----------
FROM ${JRE_IMAGE}

RUN addgroup -S jazz && adduser -S -G jazz -h /app jazz \
    && mkdir -p /data/uploads && chown -R jazz:jazz /data
WORKDIR /app
COPY --from=build --chown=jazz:jazz /build/target/jazz-analyzer.jar app.jar

USER jazz
ENV STORAGE_DIR=/data/uploads \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=3s --start-period=45s --retries=5 \
    CMD wget -qO- http://localhost:8080/actuator/health/liveness || exit 1

# exec so the JVM is PID 1 and receives SIGTERM for a graceful shutdown
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
