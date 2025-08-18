# Shopping Cart Service

A comprehensive microservice for managing shopping carts in an e-commerce platform, built with Spring Boot and designed for high performance and scalability.

## 🚀 Features

### Core Functionality
- **Cart Management**: Create, update, delete shopping carts for users and guests
- **Item Management**: Add, remove, update cart items with real-time validation
- **Multi-User Support**: Support for both authenticated users and guest sessions
- **Real-time Pricing**: Dynamic pricing with discounts, taxes, and shipping calculations
- **Cart Analytics**: Comprehensive tracking and analytics for cart behavior

### Performance & Scalability
- **Multi-layer Caching**: L1 (Caffeine) + L2 (Redis) caching strategy
- **Database Optimization**: Query optimization, connection pooling, batch operations
- **Performance Monitoring**: Real-time metrics and health checks
- **Cache Warming**: Proactive cache loading for optimal performance

### Security & Authentication
- **JWT Authentication**: Secure token-based authentication
- **Role-based Access Control**: Fine-grained permissions (ADMIN, MANAGER, USER, GUEST)
- **Cart Ownership Protection**: Secure cart access validation
- **Session Management**: Guest session handling with Redis storage

### Integration & External Services
- **Product Catalog Integration**: Real-time product validation and pricing
- **User Service Integration**: User authentication and profile management
- **Service Discovery**: Eureka client for microservice discovery
- **Circuit Breaker**: Resilient external service communication

## 🏗️ Architecture

### Technology Stack
- **Framework**: Spring Boot 3.2.x
- **Database**: PostgreSQL with JPA/Hibernate
- **Cache**: Redis + Caffeine (multi-layer)
- **Security**: Spring Security + JWT
- **Service Discovery**: Netflix Eureka
- **Documentation**: OpenAPI 3 (Swagger)
- **Monitoring**: Micrometer + Spring Boot Actuator

### Microservice Design
- **Domain-Driven Design**: Clear domain boundaries and entities
- **Clean Architecture**: Layered architecture with separation of concerns
- **Event-Driven**: Asynchronous processing for analytics and notifications
- **Resilience Patterns**: Circuit breaker, retry, fallback mechanisms

## 📋 Prerequisites

- **Java**: 17 or higher
- **Maven**: 3.8 or higher
- **PostgreSQL**: 13 or higher
- **Redis**: 6 or higher
- **Docker**: (optional) for containerized deployment

## 🚀 Quick Start

### 1. Clone the Repository
```bash
git clone https://github.com/example/ecommerce-microservice.git
cd ecommerce-microservice/shopping-cart-service
```

### 2. Configure Database
```bash
# Create PostgreSQL database
createdb shopping_cart_db

# Update application.yml with your database credentials
```

### 3. Configure Redis
```bash
# Start Redis server
redis-server

# Or using Docker
docker run -d -p 6379:6379 redis:alpine
```

### 4. Build and Run
```bash
# Build the application
./mvnw clean compile

# Run the application
./mvnw spring-boot:run
```

### 5. Access the API
- **API Base URL**: http://localhost:8083
- **Swagger UI**: http://localhost:8083/swagger-ui.html
- **Health Check**: http://localhost:8083/actuator/health

## 📖 API Documentation

### Core Endpoints

#### Cart Management
```http
POST   /api/v1/carts              # Create new cart
GET    /api/v1/carts/{id}         # Get cart details
PUT    /api/v1/carts/{id}         # Update cart
DELETE /api/v1/carts/{id}         # Delete cart
GET    /api/v1/carts/user/{userId} # Get user's carts
```

#### Cart Items
```http
POST   /api/v1/carts/{id}/items           # Add item to cart
GET    /api/v1/carts/{id}/items           # Get cart items
PUT    /api/v1/carts/{id}/items/{itemId}  # Update cart item
DELETE /api/v1/carts/{id}/items/{itemId}  # Remove cart item
```

#### Guest Operations
```http
POST   /api/v1/guest/session              # Create guest session
POST   /api/v1/guest/token/refresh        # Refresh guest token
POST   /api/v1/carts/guest                # Create guest cart
```

#### Analytics
```http
GET    /api/v1/analytics/cart/{id}        # Get cart analytics
GET    /api/v1/analytics/user/{userId}    # Get user analytics
POST   /api/v1/analytics/events           # Track custom events
```

### Authentication

Most endpoints require JWT authentication:

```bash
# Get authentication token from User Service
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username": "user@example.com", "password": "password"}'

# Use token in subsequent requests
curl -X GET http://localhost:8083/api/v1/carts \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

### Guest Access

For guest users, create a guest session first:

```bash
# Create guest session
curl -X POST http://localhost:8083/api/v1/guest/session

# Use guest token for cart operations
curl -X POST http://localhost:8083/api/v1/carts/guest \
  -H "Authorization: Bearer GUEST_JWT_TOKEN"
```

## 🔧 Configuration

### Application Properties

Key configuration options in `application.yml`:

```yaml
shopping-cart:
  cache:
    caffeine:
      max-size: 10000
      expire-after-write: 300
    redis:
      default-ttl: 3600
  performance:
    monitoring:
      enabled: true
    database:
      batch-size: 50
  validation:
    max-items-per-cart: 100
    max-quantity-per-item: 99
```

### Environment Variables

```bash
# Database Configuration
DB_HOST=localhost
DB_PORT=5432
DB_NAME=shopping_cart_db
DB_USERNAME=postgres
DB_PASSWORD=password

# Redis Configuration
REDIS_HOST=localhost
REDIS_PORT=6379

# JWT Configuration
JWT_SECRET=your-secret-key
JWT_EXPIRATION=86400000

# External Services
PRODUCT_CATALOG_URL=http://localhost:8082
USER_SERVICE_URL=http://localhost:8081
```

## 🧪 Testing

### Run Tests
```bash
# Run all tests
./mvnw test

# Run integration tests
./mvnw test -Dtest=**/*IntegrationTest

# Run with coverage
./mvnw test jacoco:report
```

### API Testing with curl

```bash
# Create a cart
curl -X POST http://localhost:8083/api/v1/carts \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"cartType": "USER", "currency": "USD"}'

# Add item to cart
curl -X POST http://localhost:8083/api/v1/carts/1/items \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "productId": "prod-123",
    "quantity": 2,
    "unitPrice": 29.99,
    "productName": "Wireless Headphones"
  }'
```

## 📊 Monitoring & Health

### Health Checks
```bash
# Application health
curl http://localhost:8083/actuator/health

# Detailed health with components
curl http://localhost:8083/actuator/health/details

# Performance metrics
curl http://localhost:8083/api/v1/performance/metrics \
  -H "Authorization: Bearer ADMIN_TOKEN"
```

### Performance Monitoring
- **Cache Statistics**: Monitor cache hit ratios and performance
- **Database Metrics**: Track query performance and connection pool status
- **External Service Metrics**: Monitor external service call latency and success rates
- **Custom Metrics**: Business-specific metrics for cart operations

## 🐳 Docker Deployment

### Build Docker Image
```bash
# Build application
./mvnw clean package -DskipTests

# Build Docker image
docker build -t shopping-cart-service:latest .
```

### Docker Compose
```yaml
version: '3.8'
services:
  shopping-cart-service:
    image: shopping-cart-service:latest
    ports:
      - "8083:8083"
    environment:
      - DB_HOST=postgres
      - REDIS_HOST=redis
    depends_on:
      - postgres
      - redis
  
  postgres:
    image: postgres:13
    environment:
      POSTGRES_DB: shopping_cart_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: password
  
  redis:
    image: redis:alpine
    ports:
      - "6379:6379"
```

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 📞 Support

- **Documentation**: [API Documentation](http://localhost:8083/swagger-ui.html)
- **Issues**: [GitHub Issues](https://github.com/example/ecommerce-microservice/issues)
- **Email**: shopping-cart-dev@example.com

## 🗺️ Roadmap

- [ ] GraphQL API support
- [ ] Real-time cart synchronization with WebSockets
- [ ] Advanced recommendation engine
- [ ] Multi-currency support enhancement
- [ ] Mobile SDK for cart operations
- [ ] Advanced analytics dashboard
