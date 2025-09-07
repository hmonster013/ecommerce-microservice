# E-commerce Microservices Platform

> **Project URL**: https://roadmap.sh/projects/scalable-ecommerce-platform

Một nền tảng thương mại điện tử được xây dựng với kiến trúc microservices sử dụng Spring Boot, Spring Cloud, và Docker.

## 🏗️ Kiến trúc hệ thống

### Core Microservices
- **User Service** (Port 8081): Quản lý người dùng, đăng ký, xác thực
- **Product Catalog Service** (Port 8082): Quản lý sản phẩm, danh mục, kho hàng
- **Shopping Cart Service** (Port 8083): Quản lý giỏ hàng
- **Order Service** (Port 8084): Xử lý đơn hàng
- **Payment Service** (Port 8085): Xử lý thanh toán
- **Notification Service** (Port 8086): Gửi thông báo email/SMS

### Infrastructure Components
- **Eureka Server** (Port 8761): Service Discovery
- **API Gateway** (Port 8080): Điểm vào duy nhất cho tất cả requests
- **PostgreSQL** (Port 5432): Database chính
- **Redis** (Port 6379): Cache và session storage
- **Apache Kafka** (Port 9092): Message broker

## 🚀 Cách chạy hệ thống

### Yêu cầu
- Java 17+
- Maven 3.6+
- Docker & Docker Compose

### Bước 1: Build tất cả services
```bash
mvn clean package -DskipTests
```

### Bước 2: Chạy infrastructure services
```bash
docker-compose up -d postgres redis zookeeper kafka
```

### Bước 3: Chạy Eureka Server
```bash
docker-compose up -d eureka-server
```

### Bước 4: Chạy tất cả microservices
```bash
docker-compose up -d
```

### Bước 5: Kiểm tra hệ thống
- Eureka Dashboard: http://localhost:8761
- API Gateway: http://localhost:8080
- Health checks: http://localhost:8080/actuator/health

## 📚 API Documentation (Swagger)

### Swagger UI Endpoints:
- **API Gateway Swagger**: http://localhost:8080/swagger-ui.html (Tổng hợp tất cả APIs)
- **User Service**: http://localhost:8081/swagger-ui.html
- **Product Catalog**: http://localhost:8082/swagger-ui.html
- **Shopping Cart**: http://localhost:8083/swagger-ui.html
- **Order Service**: http://localhost:8084/swagger-ui.html
- **Payment Service**: http://localhost:8085/swagger-ui.html
- **Notification Service**: http://localhost:8086/swagger-ui.html

### OpenAPI JSON Endpoints:
- **API Gateway**: http://localhost:8080/v3/api-docs
- **User Service**: http://localhost:8081/v3/api-docs
- **Product Catalog**: http://localhost:8082/v3/api-docs
- **Shopping Cart**: http://localhost:8083/v3/api-docs
- **Order Service**: http://localhost:8084/v3/api-docs
- **Payment Service**: http://localhost:8085/v3/api-docs
- **Notification Service**: http://localhost:8086/v3/api-docs

## 📡 API Endpoints

### User Service (qua API Gateway)
- `POST /api/users/register` - Đăng ký người dùng
- `POST /api/users/login` - Đăng nhập
- `GET /api/users/profile` - Lấy thông tin profile
- `PUT /api/users/profile` - Cập nhật profile

### Product Catalog Service
- `GET /api/products` - Lấy danh sách sản phẩm
- `GET /api/products/{id}` - Lấy chi tiết sản phẩm
- `POST /api/products` - Tạo sản phẩm mới (Admin)
- `PUT /api/products/{id}` - Cập nhật sản phẩm (Admin)

### Shopping Cart Service
- `GET /api/cart` - Lấy giỏ hàng
- `POST /api/cart/items` - Thêm sản phẩm vào giỏ
- `PUT /api/cart/items/{id}` - Cập nhật số lượng
- `DELETE /api/cart/items/{id}` - Xóa sản phẩm khỏi giỏ

### Order Service
- `POST /api/orders` - Tạo đơn hàng
- `GET /api/orders` - Lấy danh sách đơn hàng
- `GET /api/orders/{id}` - Lấy chi tiết đơn hàng
- `PUT /api/orders/{id}/status` - Cập nhật trạng thái đơn hàng

## 🛠️ Công nghệ sử dụng

### Backend
- **Spring Boot 3.5.4** - Framework chính
- **Spring Cloud 2024.0.0** - Microservices toolkit
- **Spring Security** - Authentication & Authorization
- **Spring Data JPA** - Data access layer
- **PostgreSQL** - Primary database
- **Redis** - Caching & session storage
- **Apache Kafka** - Event streaming
- **JWT** - Token-based authentication
- **Flyway** - Database migration

### DevOps
- **Docker** - Containerization
- **Docker Compose** - Multi-container orchestration
- **Maven** - Build tool

## 📁 Cấu trúc project

```
ecommerce-microservice/
├── common/                     # Shared utilities và DTOs
├── eureka-server/             # Service discovery
├── api-gateway/               # API Gateway
├── user-service/              # User management
├── product-catalog-service/   # Product management
├── shopping-cart-service/     # Shopping cart
├── order-service/             # Order processing
├── payment-service/           # Payment processing
├── notification-service/      # Notifications
├── docker-compose.yml         # Docker orchestration
├── init-databases.sql         # Database initialization
└── README.md                  # Documentation
```

## 🔧 Configuration

Mỗi service có thể được cấu hình thông qua environment variables:

### Database Configuration
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

### Service Discovery
- `EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE`

### Redis Configuration
- `SPRING_REDIS_HOST`
- `SPRING_REDIS_PORT`

### Kafka Configuration
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`

## 🚦 Monitoring & Health Checks

Tất cả services đều expose actuator endpoints:
- `/actuator/health` - Health status
- `/actuator/info` - Service information
- `/actuator/metrics` - Metrics

## 🔐 Security

- JWT-based authentication
- Role-based access control (RBAC)
- API Gateway security filters
- Service-to-service communication security

## 📈 Scalability Features

- Horizontal scaling với Docker
- Load balancing qua API Gateway
- Caching với Redis
- Asynchronous processing với Kafka
- Database per service pattern

## 🧪 Testing

```bash
# Run unit tests
mvn test

# Run integration tests
mvn verify
```

## 📝 Development Notes

- Mỗi microservice có database riêng biệt
- Communication giữa services qua REST APIs và Kafka events
- Centralized logging và monitoring
- Circuit breaker pattern cho fault tolerance
- API versioning support

## 🤝 Contributing

1. Fork the project
2. Create feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request
