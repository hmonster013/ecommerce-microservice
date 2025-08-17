# Product Catalog Service

## 📋 Overview
Product Catalog Service là core microservice quản lý sản phẩm, danh mục, inventory và reviews cho e-commerce platform.

## 🏗️ Architecture
- **Framework**: Spring Boot 3.3.5
- **Database**: PostgreSQL với Flyway migrations
- **Cache**: Redis
- **Service Discovery**: Eureka Client
- **Documentation**: OpenAPI 3 (Swagger)
- **Security**: Spring Security với CORS support

## 🚀 Features
- Product management (CRUD operations)
- Category hierarchy management
- Inventory tracking
- Product reviews and ratings
- Advanced search and filtering
- Caching with Redis
- Full-text search support

## 📁 Project Structure
```
src/main/java/org/de013/productcatalog/
├── config/           # Configuration classes
│   ├── CacheConfig.java
│   ├── DatabaseConfig.java
│   ├── OpenApiConfig.java
│   └── SecurityConfig.java
├── controller/       # REST controllers
├── service/         # Business logic
├── repository/      # Data access layer
├── entity/          # JPA entities
├── dto/             # Data transfer objects
└── ProductCatalogApplication.java
```

## 🔧 Configuration
### Environment Variables
- `SPRING_DATASOURCE_URL`: Database connection URL
- `SPRING_DATASOURCE_USERNAME`: Database username
- `SPRING_DATASOURCE_PASSWORD`: Database password
- `SPRING_REDIS_HOST`: Redis host
- `SPRING_REDIS_PORT`: Redis port
- `EUREKA_CLIENT_SERVICE_URL_DEFAULTZONE`: Eureka server URL

### Profiles
- `dev`: Development profile with debug logging
- `docker`: Docker environment profile
- `prod`: Production profile (to be configured)

## 🗄️ Database Schema
### Tables Created (Phase 1)
- `categories`: Product categories with hierarchy support
- `products`: Main product information
- `product_categories`: Many-to-many relationship between products and categories

### Migrations
- V1: Create categories table with hierarchy support
- V2: Create products table with full-text search
- V3: Create product-categories junction table

## 🔍 API Endpoints (Planned)
```
GET    /api/v1/products              # List products with filtering
GET    /api/v1/products/{id}         # Get product details
POST   /api/v1/products              # Create product (admin)
PUT    /api/v1/products/{id}         # Update product (admin)
DELETE /api/v1/products/{id}         # Delete product (admin)

GET    /api/v1/categories            # List categories
GET    /api/v1/categories/tree       # Get category tree
GET    /api/v1/categories/{id}/products # Get products by category
```

## 🧪 Testing
```bash
# Run tests
./mvnw test -pl product-catalog-service

# Run with specific profile
./mvnw test -pl product-catalog-service -Dspring.profiles.active=test
```

## 🐳 Docker
```bash
# Build image
docker build -t product-catalog-service .

# Run with docker-compose
docker-compose up product-catalog-service
```

## 📊 Monitoring
- Health check: `/actuator/health`
- Metrics: `/actuator/metrics`
- Info: `/actuator/info`

## 📚 Documentation
- Swagger UI: `http://localhost:8082/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8082/v3/api-docs`

## ✅ Phase 1 Completed
- [x] Maven project setup with Spring Boot 3.3.5
- [x] Dependencies: JPA, PostgreSQL, Validation, Security, Redis
- [x] Common module integration
- [x] Docker configuration with multi-stage build
- [x] PostgreSQL database configuration
- [x] Flyway migration setup (3 initial migrations)
- [x] Database connection testing
- [x] Application properties with environment support
- [x] Eureka client configuration
- [x] Security configuration with CORS
- [x] Logging configuration
- [x] Redis caching configuration
- [x] OpenAPI documentation setup

## ✅ Phase 2 Completed - Core Entities & Database Design
- [x] **Base Entity** with audit fields (createdAt, updatedAt, createdBy, updatedBy)
- [x] **Product Entity** with full validation, pricing, and metadata
- [x] **Category Entity** with hierarchy support and self-referencing relationships
- [x] **Product-Category Relationship** with Many-to-Many mapping and primary category support
- [x] **Product Variant Entity** with variant types (SIZE, COLOR, MATERIAL, etc.)
- [x] **Product Image Entity** with image types and display ordering
- [x] **Inventory Entity** with stock tracking, reservations, and alerts
- [x] **Review Entity** with rating system and moderation workflow
- [x] **Enums**: ProductStatus, VariantType, ImageType, ReviewStatus
- [x] **Database Migrations** (V1-V8):
  - V1: Categories table with hierarchy
  - V2: Products table with full-text search
  - V3: Product-Categories junction table
  - V4: Product variants table
  - V5: Product images table
  - V6: Inventory table with auto-creation trigger
  - V7: Reviews table with moderation support
  - V8: Sample data with realistic products and relationships

### 🏗️ Entity Relationships
- **Product** ↔ **Category** (Many-to-Many via ProductCategory)
- **Product** → **ProductVariant** (One-to-Many)
- **Product** → **ProductImage** (One-to-Many)
- **Product** ↔ **Inventory** (One-to-One)
- **Product** → **Review** (One-to-Many)
- **Category** → **Category** (Self-referencing for hierarchy)

## 🔄 Next Steps (Phase 3)
- Create DTOs and request/response models
- Implement repository layer with custom queries
- Create service layer business logic
- Implement REST controllers
- Add validation and exception handling
