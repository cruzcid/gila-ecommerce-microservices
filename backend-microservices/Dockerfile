# ═══════════════════════════════════════════════════════════════════════════
#  Shared build stage — downloads ALL dependencies and compiles ALL modules
#  in a single Maven run, so parallel service images never compete for downloads
# ═══════════════════════════════════════════════════════════════════════════
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Copy every POM first (lets BuildKit cache the dep-resolution layer)
COPY pom.xml .
COPY api-gateway/pom.xml      ./api-gateway/
COPY product-service/pom.xml  ./product-service/
COPY order-service/pom.xml    ./order-service/
COPY payment-service/pom.xml  ./payment-service/

# Copy all source trees
COPY api-gateway/src      ./api-gateway/src
COPY product-service/src  ./product-service/src
COPY order-service/src    ./order-service/src
COPY payment-service/src  ./payment-service/src

# Build every module — one download pass, all JARs produced together
RUN --mount=type=cache,target=/root/.m2 \
    mvn package -DskipTests -B

# ── api-gateway (:8080) ────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS api-gateway
WORKDIR /app
COPY --from=builder /app/api-gateway/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# ── product-service (:8081) ────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS product-service
WORKDIR /app
COPY --from=builder /app/product-service/target/*.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]

# ── order-service (:8082) ──────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS order-service
WORKDIR /app
COPY --from=builder /app/order-service/target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]

# ── payment-service (:8083) ────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS payment-service
WORKDIR /app
COPY --from=builder /app/payment-service/target/*.jar app.jar
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
