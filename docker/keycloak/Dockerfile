# Multi-stage build for Keycloak 26.4.5 with Profile Sync Event Listener SPI
FROM maven:3.9.6-eclipse-temurin-17 AS builder
WORKDIR /build

COPY keycloak-profile-sync/pom.xml .
COPY keycloak-profile-sync/src src

RUN mvn clean package -DskipTests

FROM quay.io/keycloak/keycloak:26.4.5
COPY --from=builder /build/target/keycloak-profile-sync-*.jar /opt/keycloak/providers/
COPY docker/keycloak/themes/ecommerce /opt/keycloak/themes/ecommerce
RUN /opt/keycloak/bin/kc.sh build
