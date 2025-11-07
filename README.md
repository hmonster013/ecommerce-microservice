# E-commerce Microservices Platform

> **Project URL**: https://roadmap.sh/projects/scalable-ecommerce-platform

A scalable e-commerce platform built with microservices architecture using Spring Boot, Spring Cloud, and Docker.

## 🏗️ Architecture

### Microservices
- **API Gateway** (8080) - Single entry point with JWT authentication
- **User Service** (8081) - User management and authentication
- **Product Catalog** (8082) - Product and inventory management
- **Shopping Cart** (8083) - Shopping cart operations
- **Order Service** (8084) - Order processing
- **Payment Service** (8085) - Payment processing with Stripe integration
- **Notification Service** (8086) - Email/SMS notifications

### Infrastructure
- **Eureka Server** (8761) - Service discovery
- **Config Server** (8071) - Centralized configuration
- **PostgreSQL** (5432) - Primary database (database-per-service)
- **Redis** (6379) - Caching and session storage
- **RabbitMQ** (5672, 15672) - Message broker
- **Kafka** (9092) - Event streaming

### Observability Stack
- **Grafana** (3000) - Dashboards and visualization
- **Loki** (3100) - Log aggregation
- **Prometheus** (9090) - Metrics collection
- **Tempo** (3200, 4317, 4318) - Distributed tracing
- **OpenTelemetry Java Agent** - Automatic instrumentation

## 🚀 Quick Start

### Prerequisites
- Docker & Docker Compose
- Java 17+ and Maven 3.6+ (for development)

### Run with Docker Compose
```bash
cd docker-compose/default
docker compose up -d --build
```

### Access Services
- **API Gateway**: http://localhost:8080
- **Eureka Dashboard**: http://localhost:8761
- **Grafana**: http://localhost:3000 (anonymous access enabled)
- **Prometheus**: http://localhost:9090
- **RabbitMQ Management**: http://localhost:15672 (user/password)

### Verify System Health
```bash
curl http://localhost:8080/actuator/health
```

## 📚 API Documentation

### Swagger UI
- **API Gateway (Aggregated)**: http://localhost:8080/swagger-ui.html
- Individual services: `http://localhost:{port}/swagger-ui.html`

### OpenAPI Specification
- `http://localhost:{port}/v3/api-docs`

## 📊 Monitoring & Observability

### Grafana Dashboards
Access Grafana at http://localhost:3000 (no login required)

**Pre-configured Data Sources:**
- **Loki** - Centralized logging with trace correlation
- **Prometheus** - Metrics and monitoring
- **Tempo** - Distributed tracing

**Features:**
- Trace-to-logs correlation via derived fields
- Service map and dependency graph
- Real-time metrics dashboards
- Log aggregation across all services

### OpenTelemetry Integration
All services are instrumented with OpenTelemetry Java Agent for:
- Automatic trace propagation
- Metrics export to Prometheus
- Trace export to Tempo
- Log correlation with trace IDs

**Log Format:**
```
2025-11-07 07:24:58.551 [service-name] [trace_id,span_id] -LEVEL ...
```

## 🛠️ Technology Stack

### Core
- **Spring Boot 3.2.5** - Application framework
- **Spring Cloud 2023.0.1** - Microservices ecosystem
- **Spring Security + JWT** - Authentication & authorization
- **Spring Data JPA** - Data access
- **PostgreSQL 15** - Relational database
- **Redis 7** - Caching & session store
- **RabbitMQ 3.13** - Message broker
- **Apache Kafka 3.7** - Event streaming

### Observability
- **Grafana** - Visualization
- **Loki** - Log aggregation
- **Prometheus** - Metrics
- **Tempo** - Distributed tracing
- **OpenTelemetry** - Instrumentation
- **Grafana Alloy** - Telemetry collector

### Infrastructure
- **Netflix Eureka** - Service discovery
- **Spring Cloud Config** - Configuration management
- **Spring Cloud Gateway** - API gateway
- **Docker & Docker Compose** - Containerization

## 🔧 Configuration

Configuration is managed through:
- **Spring Cloud Config Server** - Centralized configuration (Git-based)
- **Environment Variables** - Runtime configuration via `.env` files
- **Application Properties** - Service-specific settings

Key environment files:
- `.env.development` - Local development (localhost endpoints)
- `.env.example` - Docker container configuration

## 🔐 Security

- **JWT Authentication** - Token-based auth with refresh tokens
- **API Gateway Security** - Centralized authentication/authorization
- **Role-Based Access Control (RBAC)** - User/Admin roles
- **Rate Limiting** - Configurable per-endpoint limits
- **Token Blacklist** - Redis-based token revocation
- **Password Policy** - Configurable password requirements

## 📈 Key Features

- **Database per Service** - Independent data management
- **Service Discovery** - Automatic service registration with Eureka
- **Configuration Management** - Centralized with Spring Cloud Config
- **API Gateway** - Single entry point with routing and security
- **Distributed Tracing** - Full request tracing across services
- **Log Correlation** - Trace ID in every log entry
- **Health Checks** - Kubernetes-ready health endpoints
- **Resilience4j** - Circuit breaker pattern for fault tolerance
- **Event-Driven** - Kafka for async communication
- **Caching** - Redis for performance optimization

## 🚀 Development

### Build Services
```bash
mvn clean package -DskipTests
```

### Run Locally
```bash
# Start infrastructure
docker compose -f docker-compose/default/docker-compose.yml up -d postgres redis rabbitmq kafka

# Run service
cd user-service
mvn spring-boot:run
```

### Environment Variables
Configure in `.env.development` for local or `.env` for Docker.

## 📝 Project Structure

```
ecommerce-microservice/
├── api-gateway/               # API Gateway with security
├── config-server/            # Configuration server
├── eureka-server/            # Service registry
├── user-service/             # User & auth management
├── product-catalog-service/  # Product management
├── shopping-cart-service/    # Shopping cart
├── order-service/            # Order processing
├── payment-service/          # Payment with Stripe
├── notification-service/     # Email/SMS notifications
├── docker-compose/
│   ├── default/              # Main compose setup
│   └── observability/        # Monitoring configs
└── .env.development          # Local configuration
```

## 📄 License

This project is open source and available under the [MIT License](LICENSE).
