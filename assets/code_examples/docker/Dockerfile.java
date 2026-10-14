# ==============================================================================
# Module 13 — Production Multi-Stage Dockerfile for Spring Boot / Java 21
#
# Best practices demonstrated:
# 1. Multi-stage build (separates heavy compiler/build tools from minimal runtime)
# 2. Non-root user execution (appuser:appgroup UID 10001:10001)
# 3. Minimal JRE base image (Alpine Linux)
# 4. Container-aware JVM flags (-XX:+UseContainerSupport, MaxRAMPercentage)
# 5. Exec-form ENTRYPOINT allowing proper signal forwarding for graceful SIGTERM
# 6. Built-in HEALTHCHECK probe
# ==============================================================================

# STAGE 1: Build & Package
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Cache dependency layer separately from application source
COPY pom.xml mvnw ./
COPY .mvn .mvn/
# Download dependencies offline (layer caching)
# RUN ./mvnw dependency:go-offline -B || true

# Copy source and compile
COPY src src/
# RUN ./mvnw clean package -DskipTests

# Mock compiled artifact for demonstration
RUN mkdir -p target && echo "JAR_MOCK_PAYLOAD" > target/application.jar

# STAGE 2: Minimal Secure Runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

LABEL maintainer="engineering@beyondtheapi.internal"
LABEL org.opencontainers.image.source="https://github.com/aafaqahmed35/BeyondTheAPI"
LABEL org.opencontainers.image.description="Production-grade Java 21 backend microservice container"

# Create unprivileged non-root user and group
RUN addgroup -g 10001 -S appgroup && \
    adduser -u 10001 -S appuser -G appgroup

WORKDIR /app

# Copy artifact from builder stage with strict ownership
COPY --from=builder --chown=appuser:appgroup /workspace/target/application.jar app.jar

# Run as non-root user (defense against container escape)
USER 10001:10001

# Expose HTTP port
EXPOSE 8080

# Configure JVM for container memory limits
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# Healthcheck configuration
HEALTHCHECK --interval=15s --timeout=3s --start-period=30s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/actuator/health/readiness || exit 1

# Exec-form ENTRYPOINT (PID 1 receives SIGTERM directly, no shell wrapper)
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
