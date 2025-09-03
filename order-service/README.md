# Order Service - Base Microservice

## Overview
This is a simplified base version of the Order Service microservice for the e-commerce platform. It provides core order management functionality without complex integrations.

## Features
- **Create Order**: Basic order creation with order number generation
- **Get Order**: Retrieve order details by ID
- **List User Orders**: Get paginated list of orders for a specific user
- **Security**: Basic authentication and authorization using OrderSecurityService

## API Endpoints

### Core Operations
- `POST /orders` - Create a new order (authenticated users only)
- `GET /orders/{orderId}` - Get order by ID (owner or admin only)
- `GET /orders/number/{orderNumber}` - Get order by order number (authenticated users only)
- `GET /orders/my-orders` - Get current user's orders (authenticated users only)
- `GET /orders/user/{userId}` - Get orders for specific user (admin or owner only)
- `GET /orders` - Get all orders (admin only)
- `PUT /orders/{orderId}` - Update order (admin only)
- `DELETE /orders/{orderId}` - Cancel order (admin only)

## Architecture

### Controllers
- `OrderController` - Basic REST endpoints for order operations

### Services
- `OrderService` - Core business logic interface
- `OrderServiceImpl` - Simple implementation with basic order operations
- `OrderSecurityService` - Security and authorization logic

### Entities
- `Order` - Main order entity
- `OrderItem` - Order line items
- `Address`, `Money` - Value objects
- `OrderStatus`, `OrderType` - Core enums

### Security
- Uses `UserContext` and `UserContextHolder` from common module
- Authorization handled by `OrderSecurityService`
- Supports role-based access (ADMIN, CUSTOMER)

## Configuration
- Simple development configuration in `application.yml`
- PostgreSQL database for persistence
- Eureka service discovery
- OpenAPI/Swagger documentation
- **No RabbitMQ, Redis, or Feign** - removed for simplicity

## Database
- Uses PostgreSQL with JPA/Hibernate
- Flyway migrations disabled for development
- DDL auto-update enabled for easy schema changes

## Getting Started

### Prerequisites
- Java 17+
- PostgreSQL database
- Eureka server running on port 8761
- **No RabbitMQ or Redis required**

### Running the Service
1. Start PostgreSQL and create database `order_service_db`
2. Start Eureka server
3. Run the application: `mvn spring-boot:run`
4. Service will be available on port 8084
5. Swagger UI: http://localhost:8084/swagger-ui.html

### Testing
Access the API through:
- Direct service calls: http://localhost:8084/orders
- API Gateway: http://localhost:8080/api/v1/orders (if gateway is running)

## Future Enhancements
This base version can be extended with:
- Payment integration (add RabbitMQ back)
- Inventory management
- Shipping integration
- Order tracking
- Analytics and reporting
- Event-driven architecture (add RabbitMQ back)
- Caching with Redis (add Redis back)
- Feign clients for service communication

## Development Notes
- This is a simplified version for development and learning
- Complex integrations and advanced features have been removed
- Focus is on core CRUD operations and basic security
- Suitable for building upon and adding features incrementally
