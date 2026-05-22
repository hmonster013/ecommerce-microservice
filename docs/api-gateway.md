# API Gateway

> Edge service cho hệ thống e-commerce microservices. Đóng vai trò là **single entry point** cho mọi traffic từ client (web, mobile, third-party) đi vào hệ thống, chịu trách nhiệm routing, authentication, rate limiting, circuit breaking và aggregation OpenAPI docs.

Tài liệu này đi sâu vào module `api-gateway/`. Xem [`architecture.md`](architecture.md) cho bức tranh tổng thể của toàn hệ thống.

---

## 1. Vai trò trong hệ thống

Các service như `user-service`, `product-catalog-service`, `order-service`, `payment-service`... đều **không expose trực tiếp** ra ngoài. Mọi request bắt buộc phải đi qua API Gateway, nơi sẽ:

```
                      ┌─────────────────────────────────────────┐
   Client ──HTTPS───► │             API GATEWAY :8080            │
   (Web/Mobile)       │  ┌──────────────────────────────────┐   │
                      │  │ 1. CORS                          │   │
                      │  │ 2. Global Rate Limit (Bucket4j)  │   │
                      │  │ 3. JWT Validation (Keycloak)     │   │
                      │  │ 4. Authorization (RBAC)          │   │
                      │  │ 5. User Context Propagation      │   │
                      │  │ 6. Path Rewrite                  │   │
                      │  │ 7. Circuit Breaker (Resilience4j)│   │
                      │  │ 8. Load Balancing (Eureka)       │   │
                      │  └──────────────────────────────────┘   │
                      └────────┬────────────────────────────────┘
                               │ lb://service-name
            ┌──────────────────┼──────────────────┬──────────────┐
            ▼                  ▼                  ▼              ▼
       user-service    product-catalog       order-service    ...
```

Nhờ vậy, các downstream service chỉ cần tập trung vào business logic; những vấn đề cross-cutting (security, throttling, resilience) được tập trung một chỗ.

---

## 2. Tech stack

| Lớp | Công nghệ | Lý do chọn |
|---|---|---|
| Runtime | Spring Boot 3.3.5 + Java 17 | LTS, hỗ trợ records / pattern matching |
| Gateway | **Spring Cloud Gateway** (reactive, WebFlux) | Non-blocking, throughput cao hơn nhiều so với Zuul 1 blocking |
| Service discovery | Spring Cloud Netflix Eureka Client | Đăng ký/khám phá service động trong cụm |
| Config | Spring Cloud Config Client | Centralized config (`CONFIG_SERVER_URL=:8071`) |
| Security | Spring Security + OAuth2 Resource Server (JWT) | Validate token Keycloak qua JWK Set URI |
| IdP | **Keycloak** (Admin Client 23.0.7) | Quản lý user, roles, federated identity |
| Rate limiting | **Bucket4j 7.6.0** (in-memory token bucket) | Token bucket cho throttling chính xác |
| Resilience | Resilience4j (reactor flavor) | Circuit breaker bất đồng bộ, không block event loop |
| Cache | Spring Data Redis | Sẵn sàng cho distributed rate limit / session |
| Observability | Actuator + Micrometer + Prometheus | Metrics scrape; logs có `trace_id`/`span_id` (MDC) |
| API Docs | springdoc-openapi (WebFlux UI) | Aggregate Swagger UI từ tất cả service |

---

## 3. Cấu trúc package

```
org.de013.apigateway
├── ApiGatewayApplication.java        # Bootstrap, @EnableDiscoveryClient
├── config/
│   ├── GatewayRoutesConfig.java      # Khai báo route programmatically (RouteLocatorBuilder)
│   ├── SecurityConfig.java           # WebFlux Security + CORS + JWT decoder
│   ├── KeycloakRoleConverter.java    # Map realm_access.roles → ROLE_*
│   ├── RateLimitConfig.java          # Bucket4j buckets + @ConfigurationProperties
│   ├── RedisConfig.java              # Reactive Redis template
│   ├── WebClientConfig.java          # WebClient bean cho Keycloak API
│   ├── OpenApiConfig.java            # Aggregate Swagger
│   └── JacksonConfig.java            # ObjectMapper tuỳ chỉnh (JSR310, etc.)
├── filter/
│   └── GlobalRateLimitFilter.java    # Order = -150, sau CORS, trước auth
├── security/
│   └── KeycloakUserContextFilter.java# Order = -50, inject X-User-* header
├── controller/
│   ├── AuthController.java           # /api/auth/{register,login,refresh,logout}
│   └── FallbackController.java       # /fallback/{serviceName}
├── service/
│   ├── KeycloakService.java          # Gọi Keycloak Admin API + OIDC token endpoint
│   └── UserServiceClient.java        # Sync user profile xuống user-service sau khi tạo
├── constant/ApiPaths.java            # Tập trung mọi path/route/CB name
├── dto/auth/                         # LoginRequest, RegisterRequest, AuthResponse, ...
└── exception/                        # GatewayException + GatewayExceptionHandler
```

---

## 4. Request lifecycle (chi tiết)

Khi một request `GET /api/order-service/orders/my-orders` đến gateway:

1. **CORS** (`SecurityConfig#corsConfigurationSource`) — preflight `OPTIONS` được cache 3600s. Cho phép `localhost:*` trong dev profile.
2. **GlobalRateLimitFilter** (`order = -150`):
   - Trích IP từ `X-Forwarded-For` / `X-Real-IP` / `remoteAddress` → IP bucket (mặc định **1000 req/phút/IP**).
   - Nếu có `Authorization: Bearer ...`, parse `sub` claim → user bucket (**10000 req/phút/user**).
   - Khi vượt → trả `429 Too Many Requests` kèm `Retry-After`, `X-RateLimit-Remaining`, `X-RateLimit-Reset`.
3. **Spring Security filter chain** (`order = -100`):
   - Lấy JWT từ header, validate signature qua **JWK Set URI** của Keycloak (lazy fetch + cache).
   - `KeycloakRoleConverter` đọc `realm_access.roles` → tạo `GrantedAuthority` dạng `ROLE_ADMIN`, `ROLE_CUSTOMER`...
   - `authorizeExchange` match path → quyết định cho phép / 403.
4. **KeycloakUserContextFilter** (`order = -50`):
   - Đọc principal đã authenticated, mutate request thêm 3 header `X-User-Id`, `X-User-Username`, `X-User-Email`.
   - Downstream service không cần parse lại JWT — chỉ đọc header (kết hợp với `UserContext` ở module `common`).
5. **Route matching** (`GatewayRoutesConfig`):
   - Path `/api/order-service/**` khớp route `ORDER_SERVICE`.
   - `RewritePath` strip prefix: `/api/order-service/orders/my-orders` → `/orders/my-orders`.
   - URI `lb://order-service` → Eureka resolve sang instance thật.
6. **CircuitBreaker** (Resilience4j wrap reactive call):
   - Nếu service lỗi liên tục → mở mạch, forward sang `/fallback/order-service` → trả `503` JSON chuẩn.
7. **Response** quay ngược lại client với rate-limit headers gắn thêm.

---

## 5. Tính năng nổi bật

### 5.1 Routing có cấu trúc, zero hard-code path

Tất cả constants tập trung trong `constant/ApiPaths.java`:

```java
public static final String USER_SERVICE = "user-service";
public static final String ROUTE_USER_SERVICE = API + "/" + USER_SERVICE + "/**";
public static final String LB_USER_SERVICE = "lb://" + USER_SERVICE;
public static final String FALLBACK_USER_SERVICE = "forward:" + FALLBACK + "/" + USER_SERVICE;
```

→ Thêm service mới chỉ cần khai báo constant + 1 block `.route(...)` trong `GatewayRoutesConfig`. Tránh được lỗi typo path scattered nhiều nơi.

### 5.2 Authentication-as-a-Service tích hợp Keycloak

Thay vì để mỗi service tự gọi Keycloak, gateway expose **`/api/auth/*`** wrap lại Keycloak OIDC:

| Endpoint | Hành vi |
|---|---|
| `POST /api/auth/register` | Tạo user qua Keycloak Admin API → gán role `CUSTOMER` → **sync xuống `user-service` DB** → auto-login trả JWT |
| `POST /api/auth/login` | Password grant → trả `access_token` + `refresh_token` |
| `POST /api/auth/refresh` | Refresh token grant |
| `POST /api/auth/logout` | Revoke refresh token (idempotent) |

Đặc biệt khi register, gateway orchestrate 4 bước với **Reactor `flatMap` chain** (`AuthController#register`):
```
createUser → assignRole → syncUser (gọi user-service qua WebClient) → getToken
```
Nếu bước nào fail → `onErrorResume` trả `KeycloakException` thống nhất.

### 5.3 Rate limiting hai tầng (IP + User)

`GlobalRateLimitFilter` áp dụng **defense-in-depth**:

- **Tầng IP** (coarse-grained): chặn DDoS từ một IP ngay cả khi chưa authenticated.
- **Tầng User** (fine-grained): chặn abuse từ một account đã login dù IP có đổi (mobile, NAT...).

Bucket được tạo lazy qua `ConcurrentHashMap.computeIfAbsent` — không cần đăng ký trước, scale theo số IP/user thực tế đang hoạt động.

Response chuẩn (RFC 6585):
```
HTTP/1.1 429 Too Many Requests
Retry-After: 12
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1716372480
Content-Type: application/json
```

### 5.4 Circuit Breaker per-service

Mỗi route gắn 1 circuit breaker riêng (`userServiceCircuitBreaker`, `productServiceCircuitBreaker`...). Khi mạch mở:
- Traffic không gọi xuống service đang chết → tiết kiệm thread, tránh cascade failure.
- Forward sang `FallbackController#serviceFallback` → trả `503` JSON có `traceId`, `path`, `method`, `message` thân thiện.

### 5.5 Aggregated Swagger UI

Mỗi downstream service đều expose `/v3/api-docs`. Gateway route riêng để aggregate:
```
/api/user-service/v3/api-docs              → user-service:/v3/api-docs
/api/product-catalog-service/v3/api-docs   → product-catalog-service:/v3/api-docs
...
```
→ Một Swagger UI duy nhất ở gateway hiển thị API của toàn bộ hệ thống — rất hữu ích khi demo.

### 5.6 Authorization theo RBAC (declarative)

`SecurityConfig` khai báo policy bằng DSL chứ không scatter `@PreAuthorize`:

```java
.pathMatchers(HttpMethod.GET,  "/api/product-catalog-service/products/**").permitAll()
.pathMatchers(HttpMethod.POST, "/api/product-catalog-service/products/**").hasRole("ADMIN")
.pathMatchers("/api/shopping-cart-service/cart/**").hasAnyRole("ADMIN","CUSTOMER","MANAGER")
.pathMatchers(HttpMethod.POST, "/api/payment-service/webhooks/**").permitAll()  // Stripe webhook
```

Roles: `ADMIN`, `MANAGER`, `SUPPORT`, `CUSTOMER` — lấy từ `realm_access.roles` trong JWT của Keycloak.

### 5.7 User Context Propagation

Sau khi JWT validated, `KeycloakUserContextFilter` mutate request (sử dụng `ServerHttpRequestDecorator`) để chèn 3 header:
```
X-User-Id:       <keycloak sub>
X-User-Username: <preferred_username>
X-User-Email:    <email>
```

→ Downstream service KHÔNG cần dependency Keycloak, chỉ inject `UserContext` (module `common`) là có user hiện tại. **Decouple** rất sạch.

### 5.8 Observability sẵn sàng

- `management.endpoints.web.exposure.include = health, metrics, info, prometheus, gateway`
- Logging pattern có `trace_id`, `span_id` (MDC) → tích hợp được với Tempo/Jaeger.
- Endpoint `/actuator/gateway/routes` để inspect route khi vận hành.

---

## 6. Configuration

`application.yml` rất gọn — toàn bộ config nặng (Keycloak URL, rate-limit, Eureka...) được **lấy từ Config Server** (`localhost:8071`).

Ví dụ override rate-limit ở Config Server hoặc env:
```yaml
rate-limit:
  enabled: true
  per-ip:
    capacity: 1000
    refill-tokens: 1000
    refill-period-minutes: 1
  per-user:
    capacity: 10000
    refill-tokens: 10000
    refill-period-minutes: 1
```

Env cần thiết:
```
CONFIG_SERVER_URL=http://localhost:8071
KEYCLOAK_SERVER_URL=http://localhost:8090
KEYCLOAK_REALM=ecommerce
KEYCLOAK_CLIENT_ID=ecommerce-gateway
KEYCLOAK_CLIENT_SECRET=***
```

---

## 7. Build & Run

```bash
# Build (parent + module)
mvn -pl api-gateway -am clean package -DskipTests

# Run local (cần Eureka :8761, Config Server :8071, Keycloak :8090)
java -jar api-gateway/target/api-gateway-0.0.1-SNAPSHOT.jar

# Build image bằng Jib (không cần Docker daemon)
mvn -pl api-gateway jib:dockerBuild   # → de013/ecommerce-api-gateway:s3
```

Endpoints sau khi chạy:
- Gateway:     http://localhost:8080
- Swagger UI:  http://localhost:8080/swagger-ui.html
- Health:      http://localhost:8080/actuator/health
- Prometheus:  http://localhost:8080/actuator/prometheus
- Routes:      http://localhost:8080/actuator/gateway/routes

---

## 8. Hạn chế hiện tại & hướng cải thiện

| Hiện trạng | Vấn đề tiềm ẩn | Hướng nâng cấp |
|---|---|---|
| Rate-limit lưu trong `ConcurrentHashMap` JVM-local | Khi scale > 1 instance, mỗi instance giữ bucket riêng → giới hạn thực tế = `N × limit` | Chuyển sang **Bucket4j-Redis** dùng Lua script, hoặc Spring Cloud Gateway `RedisRateLimiter` |
| Parse JWT `sub` thủ công trong `GlobalRateLimitFilter` (substring) | Không robust với JSON đặc biệt | Dùng `JwtDecoder` / Nimbus để parse claim |
| Bucket map không có TTL / eviction | Có thể tăng dần memory nếu nhiều IP/user one-off | Thêm Caffeine với `expireAfterAccess` |
| `Authorization` rule khai báo bằng path matcher | Path dễ drift khi service đổi URL | Cân nhắc **Open Policy Agent / Cerbos** nếu policy phức tạp |
| `localhost:*` trong CORS | OK cho dev, không production-ready | Đọc allowed origins từ config theo profile |
| Keycloak password grant cho login | OIDC khuyến nghị **Authorization Code + PKCE** | Cho web/mobile nên chuyển sang AC+PKCE, password grant giữ cho server-to-server testing |

---

## 9. File tham chiếu chính trong code

- [`GatewayRoutesConfig.java`](../api-gateway/src/main/java/org/de013/apigateway/config/GatewayRoutesConfig.java) — entry point của toàn bộ routing.
- [`SecurityConfig.java`](../api-gateway/src/main/java/org/de013/apigateway/config/SecurityConfig.java) — security filter chain + CORS + JWT decoder.
- [`GlobalRateLimitFilter.java`](../api-gateway/src/main/java/org/de013/apigateway/filter/GlobalRateLimitFilter.java) — pipeline rate-limit.
- [`KeycloakUserContextFilter.java`](../api-gateway/src/main/java/org/de013/apigateway/security/KeycloakUserContextFilter.java) — inject user header xuống downstream.
- [`AuthController.java`](../api-gateway/src/main/java/org/de013/apigateway/controller/AuthController.java) — register/login/refresh/logout flow.
- [`KeycloakService.java`](../api-gateway/src/main/java/org/de013/apigateway/service/KeycloakService.java) — wrapper cho Keycloak Admin + OIDC API.
- [`ApiPaths.java`](../api-gateway/src/main/java/org/de013/apigateway/constant/ApiPaths.java) — single source of truth cho routes/CB names.
