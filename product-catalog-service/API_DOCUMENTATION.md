# 📚 Product Catalog Service - API Documentation

## 🚀 Overview

The **Product Catalog Service** is an enterprise-grade microservice that provides comprehensive product management capabilities with advanced search, caching, and analytics features. This service is designed for high-performance e-commerce platforms with sophisticated product catalog requirements.

## 🔗 API Access

### Swagger UI
- **Development**: http://localhost:8082/swagger-ui.html
- **API Gateway**: http://localhost:8080/swagger-ui.html
- **Production**: https://api.de013.org/swagger-ui.html

### OpenAPI Specification
- **JSON**: http://localhost:8082/api-docs
- **YAML**: http://localhost:8082/api-docs.yaml

## 📋 API Endpoints Overview

### 🛍️ Products API (`/api/v1/products`)

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| `GET` | `/` | Get all products with pagination | No |
| `GET` | `/{id}` | Get product by ID | No |
| `POST` | `/` | Create new product | Admin |
| `PUT` | `/{id}` | Update product | Admin |
| `DELETE` | `/{id}` | Delete product | Admin |
| `GET` | `/featured` | Get featured products | No |
| `GET` | `/category/{categoryId}` | Get products by category | No |
| `GET` | `/brand/{brand}` | Get products by brand | No |

### 🔍 Search API (`/api/v1/products/search`)

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| `GET` | `/` | Advanced product search | No |
| `GET` | `/suggestions` | Get search suggestions | No |
| `GET` | `/popular` | Get popular searches | No |
| `GET` | `/trending` | Get trending products | No |

### 📂 Categories API (`/api/v1/categories`)

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| `GET` | `/` | Get all categories | No |
| `GET` | `/{id}` | Get category by ID | No |
| `POST` | `/` | Create new category | Admin |
| `PUT` | `/{id}` | Update category | Admin |
| `DELETE` | `/{id}` | Delete category | Admin |
| `GET` | `/tree` | Get category hierarchy | No |

### 📦 Inventory API (`/api/v1/inventory`)

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| `GET` | `/product/{productId}` | Get product inventory | No |
| `PUT` | `/product/{productId}` | Update inventory | Admin |
| `GET` | `/low-stock` | Get low stock products | Admin |

### ⭐ Reviews API (`/api/v1/reviews`)

| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| `GET` | `/product/{productId}` | Get product reviews | No |
| `POST` | `/product/{productId}` | Create review | User |
| `PUT` | `/{id}` | Update review | User/Admin |
| `DELETE` | `/{id}` | Delete review | User/Admin |

## 🔍 Advanced Search Features

### Search Parameters

```http
GET /api/v1/products/search?query=wireless&category=1&minPrice=50&maxPrice=500&sortBy=price&sortDir=asc&page=0&size=20
```

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `query` | String | Full-text search query | `wireless headphones` |
| `categoryId` | Long | Filter by category ID | `1` |
| `brand` | String | Filter by brand name | `TechBrand` |
| `minPrice` | BigDecimal | Minimum price filter | `50.00` |
| `maxPrice` | BigDecimal | Maximum price filter | `500.00` |
| `minRating` | Double | Minimum rating (1-5) | `4.0` |
| `inStock` | Boolean | In-stock products only | `true` |
| `featured` | Boolean | Featured products only | `false` |
| `sortBy` | String | Sort field | `relevance`, `price`, `name`, `rating`, `date` |
| `sortDir` | String | Sort direction | `asc`, `desc` |
| `page` | Integer | Page number (0-based) | `0` |
| `size` | Integer | Page size (1-100) | `20` |

### Search Response Example

```json
{
  "content": [
    {
      "id": 1,
      "name": "Premium Wireless Headphones",
      "description": "High-quality wireless headphones with noise cancellation",
      "sku": "WH-001",
      "price": 299.99,
      "brand": "TechBrand",
      "status": "ACTIVE",
      "featured": true,
      "averageRating": 4.5,
      "reviewCount": 128,
      "categories": [
        {
          "id": 1,
          "name": "Electronics",
          "slug": "electronics"
        }
      ],
      "availability": {
        "inStock": true,
        "quantity": 50
      },
      "images": [
        {
          "id": 1,
          "url": "https://cdn.example.com/images/wh-001-main.jpg",
          "type": "MAIN",
          "displayOrder": 1
        }
      ]
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 20,
    "sort": {
      "sorted": true,
      "orderBy": "relevance"
    }
  },
  "totalElements": 156,
  "totalPages": 8,
  "first": true,
  "last": false,
  "numberOfElements": 20
}
```

## 📊 Performance Features

### Caching Strategy

| Cache Type | TTL | Description |
|------------|-----|-------------|
| Product Details | 2 hours | Individual product information |
| Category Tree | 12 hours | Category hierarchy |
| Search Results | 15 minutes | Search query results |
| Featured Products | 30 minutes | Featured product lists |
| Popular Products | 10 minutes | Popular product rankings |
| Inventory | 2 minutes | Stock availability |

### Performance Metrics

- **Response Times**: Sub-100ms for cached data
- **Throughput**: 1000+ requests/second
- **Cache Hit Rate**: 85%+ expected
- **Database Connections**: 20 max, 5 min idle
- **Search Performance**: Sub-500ms for complex queries

## 🔐 Authentication & Authorization

### Security Levels

| Level | Description | Required For |
|-------|-------------|--------------|
| **Public** | No authentication required | Product browsing, search |
| **User** | JWT token required | Reviews, favorites |
| **Admin** | Admin role required | Product/category management |

### JWT Token Format

```http
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

## 📈 Analytics & Monitoring

### Search Analytics

The service automatically tracks:
- Search queries and results
- User behavior and click-through rates
- Popular searches and trends
- Performance metrics and optimization opportunities

### Health Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Service health status |
| `/actuator/metrics` | Performance metrics |
| `/actuator/info` | Service information |

## 🚀 Getting Started

### 1. Start the Service

```bash
# Using Maven
./mvnw spring-boot:run -pl product-catalog-service

# Using Docker
docker run -p 8082:8082 product-catalog-service:latest
```

### 2. Access Swagger UI

Open your browser and navigate to:
```
http://localhost:8082/swagger-ui.html
```

### 3. Test Basic Search

```bash
curl -X GET "http://localhost:8082/api/v1/products/search?query=headphones&size=5" \
  -H "accept: application/json"
```

### 4. Create a Product (Admin)

```bash
curl -X POST "http://localhost:8082/api/v1/products" \
  -H "accept: application/json" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -d '{
    "name": "Premium Wireless Headphones",
    "description": "High-quality wireless headphones with noise cancellation",
    "sku": "WH-001",
    "price": 299.99,
    "brand": "TechBrand",
    "categoryIds": [1],
    "featured": false
  }'
```

## 🔧 Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | Service port | `8082` |
| `SPRING_DATASOURCE_URL` | Database URL | `jdbc:postgresql://localhost:5432/product_catalog_db` |
| `SPRING_DATA_REDIS_HOST` | Redis host | `localhost` |
| `SPRING_DATA_REDIS_PORT` | Redis port | `6379` |
| `EUREKA_CLIENT_SERVICE_URL` | Eureka server URL | `http://localhost:8761/eureka` |

### Application Properties

```yaml
# API Documentation
springdoc:
  api-docs:
    path: /api-docs
  swagger-ui:
    path: /swagger-ui.html
    try-it-out-enabled: true

# Caching
spring:
  cache:
    type: redis
    redis:
      time-to-live: 1800000 # 30 minutes default
```

## 📞 Support

For API support and questions:
- **Email**: product-catalog@de013.org
- **Documentation**: https://github.com/de013/ecommerce-microservice
- **Issues**: https://github.com/de013/ecommerce-microservice/issues

---

**Product Catalog Service v1.0.0** - Enterprise E-commerce Microservice
