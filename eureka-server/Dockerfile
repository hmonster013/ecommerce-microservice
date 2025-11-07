FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache curl wget

WORKDIR /app

# Download OpenTelemetry Java agent
RUN wget -q https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.1.0/opentelemetry-javaagent.jar

COPY target/*.jar app.jar

EXPOSE 8761

ENTRYPOINT ["java", "-jar", "app.jar"]
