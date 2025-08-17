# User Service

## 📋 Overview

The **User Service** is a core microservice in the e-commerce platform that handles user management, authentication, and authorization. It provides secure user registration, login, profile management, and role-based access control using JWT tokens.

## 🚀 Features

### Authentication & Authorization
- ✅ User registration with email validation
- ✅ Secure login with JWT token generation
- ✅ Token refresh mechanism
- ✅ Role-based access control (RBAC)
- ✅ Password strength validation
- ✅ Account status management

### User Management
- ✅ User profile management
- ✅ Password change functionality
- ✅ User search and filtering
- ✅ Admin user management
- ✅ Account activation/deactivation

### Security Features
- ✅ BCrypt password hashing
- ✅ JWT token-based authentication
- ✅ Rate limiting protection
- ✅ Input validation and sanitization
- ✅ SQL injection and XSS prevention
- ✅ Custom validation annotations

### API Documentation
- ✅ Swagger/OpenAPI 3 documentation
- ✅ Interactive API explorer
- ✅ Request/response examples
- ✅ Authentication documentation

## 🏗️ Architecture

### Technology Stack
- **Framework**: Spring Boot 3.2
- **Security**: Spring Security 6
- **Database**: PostgreSQL
- **Migration**: Flyway
- **Documentation**: SpringDoc OpenAPI 3
- **Validation**: Jakarta Validation
- **Build Tool**: Maven

### Package Structure
```
user-service/
├── src/main/java/org/de013/userservice/
│   ├── config/          # Configuration classes
│   ├── controller/      # REST controllers
│   ├── dto/            # Data Transfer Objects
│   ├── entity/         # JPA entities
│   ├── exception/      # Custom exceptions
│   ├── repository/     # Data repositories
│   ├── security/       # Security components
│   ├── service/        # Business logic
│   ├── util/          # Utility classes
│   └── validator/     # Custom validators
├── src/main/resources/
│   ├── db/migration/   # Flyway migrations
│   ├── api-examples.md # API usage examples
│   └── application.yml # Configuration
└── src/test/          # Test classes
```

## 🔧 Setup Instructions

### Prerequisites
- Java 17 or higher
- Maven 3.6+
- PostgreSQL 12+
- Docker (optional)

### Local Development Setup

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd ecommerce-microservice/user-service
   ```

2. **Setup PostgreSQL Database**
   ```bash
   # Using Docker
   docker run --name postgres-user \
     -e POSTGRES_DB=user_service_db \
     -e POSTGRES_USER=user_service \
     -e POSTGRES_PASSWORD=password \
     -p 5432:5432 -d postgres:15
   
   # Or use existing PostgreSQL instance
   createdb user_service_db
   ```

3. **Configure Application**
   ```yaml
   # src/main/resources/application-local.yml
   spring:
     datasource:
       url: jdbc:postgresql://localhost:5432/user_service_db
       username: user_service
       password: password
     
   jwt:
     secret: your-secret-key-here
     expiration: 3600000  # 1 hour
   ```

4. **Run the Application**
   ```bash
   # Using Maven
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
   
   # Or build and run JAR
   ./mvnw clean package
   java -jar target/user-service-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
   ```

5. **Verify Setup**
   - Health Check: http://localhost:8081/actuator/health
   - API Documentation: http://localhost:8081/swagger-ui.html
   - API Docs JSON: http://localhost:8081/v3/api-docs

### Docker Setup

1. **Build Docker Image**
   ```bash
   docker build -t user-service:latest .
   ```

2. **Run with Docker Compose**
   ```bash
   # From project root
   docker-compose up user-service
   ```

## 📚 API Documentation

### Base URL
- **Local**: http://localhost:8081
- **API Gateway**: http://localhost:8080/api/users
- **Production**: https://api.de013.org/users

### Authentication Endpoints

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/v1/auth/register` | Register new user | No |
| POST | `/api/v1/auth/login` | User login | No |
| POST | `/api/v1/auth/refresh` | Refresh JWT token | No |
| POST | `/api/v1/auth/logout` | User logout | Yes |

### User Management Endpoints

| Method | Endpoint | Description | Auth Required | Role |
|--------|----------|-------------|---------------|------|
| GET | `/api/v1/users/profile` | Get user profile | Yes | Any |
| PUT | `/api/v1/users/profile` | Update profile | Yes | Any |
| PUT | `/api/v1/users/change-password` | Change password | Yes | Any |
| GET | `/api/v1/users` | List all users | Yes | Admin |
| GET | `/api/v1/users/{id}` | Get user by ID | Yes | Admin |
| PUT | `/api/v1/users/{id}/status` | Update user status | Yes | Admin |

### Interactive Documentation
Visit http://localhost:8081/swagger-ui.html for interactive API documentation with:
- Request/response examples
- Authentication testing
- Parameter descriptions
- Error code explanations

## 🔐 Security

### Authentication Flow
1. User registers or logs in with credentials
2. Server validates credentials and returns JWT token
3. Client includes token in Authorization header: `Bearer <token>`
4. Server validates token for protected endpoints
5. Token expires after 1 hour, use refresh token to get new one

### Password Requirements
- Minimum 8 characters
- At least one uppercase letter
- At least one lowercase letter
- At least one digit
- At least one special character (@$!%*?&)
- Not a common password
- Cannot contain user information

### Rate Limiting
- Authentication endpoints: 5 requests/minute per IP
- General endpoints: 100 requests/minute per user
- Admin endpoints: 200 requests/minute per admin

## 🧪 Testing

### Run Tests
```bash
# Unit tests
./mvnw test

# Integration tests
./mvnw test -Dtest="**/*IntegrationTest"

# All tests with coverage
./mvnw clean test jacoco:report
```

### Test Coverage
- Target: 80% minimum coverage
- Reports: `target/site/jacoco/index.html`

### API Testing
```bash
# Register user
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "TestPass123!",
    "firstName": "Test",
    "lastName": "User"
  }'

# Login
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "TestPass123!"
  }'

# Get profile (replace TOKEN with actual JWT)
curl -X GET http://localhost:8081/api/v1/users/profile \
  -H "Authorization: Bearer TOKEN"
```

## 🚀 Deployment

### Environment Variables
```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/user_service_db
SPRING_DATASOURCE_USERNAME=user_service
SPRING_DATASOURCE_PASSWORD=secure_password

# JWT
JWT_SECRET=your-production-secret-key
JWT_EXPIRATION=3600000

# Server
SERVER_PORT=8081
SPRING_PROFILES_ACTIVE=production
```

### Health Checks
- **Liveness**: `/actuator/health/liveness`
- **Readiness**: `/actuator/health/readiness`
- **Database**: `/actuator/health/db`

### Monitoring
- **Metrics**: `/actuator/metrics`
- **Prometheus**: `/actuator/prometheus`
- **Info**: `/actuator/info`

## 🔧 Configuration

### Application Properties
```yaml
# Core settings
spring:
  application:
    name: user-service
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:local}

# Database
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false

# Security
jwt:
  secret: ${JWT_SECRET}
  expiration: ${JWT_EXPIRATION:3600000}

# API Documentation
springdoc:
  api-docs:
    path: /v3/api-docs
  swagger-ui:
    path: /swagger-ui.html
```

## 🤝 Integration

### Service Discovery
- Registers with Eureka Server
- Service name: `user-service`
- Health check endpoint: `/actuator/health`

### API Gateway Integration
- Routes: `/api/users/**` → `user-service`
- Load balancing: Round-robin
- Circuit breaker: Enabled

### Inter-Service Communication
```java
// Validate user for other services
@GetMapping("/validate/{userId}")
public ResponseEntity<UserValidationDto> validateUser(@PathVariable Long userId);

// Get user details for other services
@GetMapping("/internal/{userId}")
public ResponseEntity<UserDetailsDto> getUserDetails(@PathVariable Long userId);
```

## 📊 Monitoring & Logging

### Logging Configuration
- **Level**: INFO (configurable)
- **Format**: JSON in production
- **Destination**: Console + File
- **Rotation**: Daily

### Metrics
- Request/response times
- Error rates
- Database connection pool
- JWT token validation
- User registration/login rates

## 🐛 Troubleshooting

### Common Issues

1. **Database Connection Failed**
   ```bash
   # Check database status
   docker ps | grep postgres
   # Check connection
   telnet localhost 5432
   ```

2. **JWT Token Invalid**
   ```bash
   # Check token expiration
   # Verify JWT secret configuration
   # Check system clock synchronization
   ```

3. **Validation Errors**
   ```bash
   # Check request format
   # Verify required fields
   # Review validation messages
   ```

### Debug Mode
```bash
# Enable debug logging
./mvnw spring-boot:run -Dlogging.level.org.de013.userservice=DEBUG
```

## 📝 Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/new-feature`
3. Commit changes: `git commit -am 'Add new feature'`
4. Push to branch: `git push origin feature/new-feature`
5. Submit pull request

### Code Standards
- Follow Spring Boot best practices
- Write unit tests for new features
- Update documentation
- Use conventional commit messages

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](../LICENSE) file for details.

## 📞 Support

- **Documentation**: [API Examples](src/main/resources/api-examples.md)
- **Issues**: GitHub Issues
- **Email**: dev@de013.org
