# User Service API Documentation

## 📖 Overview

This document provides comprehensive API documentation for the User Service, including endpoint descriptions, request/response formats, authentication requirements, and usage examples.

## 🔗 Quick Links

- **Swagger UI**: http://localhost:8081/swagger-ui.html
- **OpenAPI Spec**: http://localhost:8081/v3/api-docs
- **Health Check**: http://localhost:8081/actuator/health
- **API Examples**: [api-examples.md](../src/main/resources/api-examples.md)

## 🔐 Authentication

### JWT Token Authentication

All protected endpoints require a valid JWT token in the Authorization header:

```http
Authorization: Bearer <your-jwt-token>
```

### Token Lifecycle

1. **Obtain Token**: Login or register to receive JWT token
2. **Use Token**: Include in Authorization header for protected endpoints
3. **Refresh Token**: Use refresh endpoint when token expires
4. **Token Expiry**: Access tokens expire after 1 hour

## 📋 API Endpoints Summary

### Authentication Endpoints

| Endpoint | Method | Description | Auth Required |
|----------|--------|-------------|---------------|
| `/api/v1/auth/register` | POST | Register new user | ❌ |
| `/api/v1/auth/login` | POST | User login | ❌ |
| `/api/v1/auth/refresh` | POST | Refresh JWT token | ❌ |
| `/api/v1/auth/logout` | POST | User logout | ✅ |

### User Management Endpoints

| Endpoint | Method | Description | Auth Required | Role |
|----------|--------|-------------|---------------|------|
| `/api/v1/users/profile` | GET | Get user profile | ✅ | Any |
| `/api/v1/users/profile` | PUT | Update user profile | ✅ | Any |
| `/api/v1/users/change-password` | PUT | Change password | ✅ | Any |
| `/api/v1/users` | GET | List all users (paginated) | ✅ | Admin |
| `/api/v1/users/{id}` | GET | Get user by ID | ✅ | Admin |
| `/api/v1/users/{id}/status` | PUT | Update user status | ✅ | Admin |

### Legacy Endpoints (Deprecated)

| Endpoint | Method | Description | Auth Required |
|----------|--------|-------------|---------------|
| `/legacy/api/v1/users/{id}` | GET | Get user by ID | ❌ |
| `/legacy/api/v1/users/username/{username}` | GET | Get user by username | ❌ |

## 🔧 Request/Response Formats

### Standard Response Format

All API responses follow this consistent format:

```json
{
  "success": boolean,
  "message": "string",
  "data": object | null,
  "errors": object | null,
  "code": "string",
  "path": "string",
  "timestamp": "ISO-8601 datetime"
}
```

### Success Response Example

```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": {
    "id": 1,
    "username": "john_doe",
    "email": "john@example.com"
  },
  "errors": null,
  "code": "OK",
  "path": "/api/v1/users/profile",
  "timestamp": "2024-01-01T10:00:00Z"
}
```

### Error Response Example

```json
{
  "success": false,
  "message": "Validation failed",
  "data": null,
  "errors": {
    "email": "Email already exists",
    "password": "Password too weak"
  },
  "code": "BAD_REQUEST",
  "path": "/api/v1/auth/register",
  "timestamp": "2024-01-01T10:00:00Z"
}
```

## 📝 Data Models

### UserRegistrationDto

```json
{
  "username": "string (3-50 chars, required)",
  "email": "string (valid email, required)",
  "password": "string (8+ chars, complex, required)",
  "firstName": "string (2-50 chars, required)",
  "lastName": "string (2-50 chars, required)",
  "phone": "string (Vietnamese format, optional)",
  "address": "string (optional)"
}
```

### UserLoginDto

```json
{
  "username": "string (required)",
  "password": "string (required)"
}
```

### AuthResponse

```json
{
  "token": "string (JWT token)",
  "type": "Bearer",
  "user": {
    "id": "number",
    "username": "string",
    "email": "string",
    "firstName": "string",
    "lastName": "string",
    "phone": "string",
    "status": "ACTIVE|INACTIVE|SUSPENDED",
    "roles": ["CUSTOMER|ADMIN"],
    "createdAt": "ISO-8601 datetime",
    "updatedAt": "ISO-8601 datetime",
    "lastLoginAt": "ISO-8601 datetime"
  }
}
```

### UserProfileDto

```json
{
  "id": "number",
  "username": "string",
  "email": "string",
  "firstName": "string",
  "lastName": "string",
  "phone": "string",
  "address": "string",
  "status": "string",
  "roles": ["string"],
  "createdAt": "ISO-8601 datetime",
  "updatedAt": "ISO-8601 datetime",
  "lastLoginAt": "ISO-8601 datetime"
}
```

### ChangePasswordDto

```json
{
  "currentPassword": "string (required)",
  "newPassword": "string (8+ chars, complex, required)",
  "confirmPassword": "string (must match newPassword, required)"
}
```

## ⚠️ Error Codes

### HTTP Status Codes

| Code | Description | When It Occurs |
|------|-------------|----------------|
| 200 | OK | Successful operation |
| 201 | Created | Resource created successfully |
| 400 | Bad Request | Invalid input data or validation errors |
| 401 | Unauthorized | Missing or invalid authentication token |
| 403 | Forbidden | Insufficient permissions |
| 404 | Not Found | Resource not found |
| 409 | Conflict | Resource already exists |
| 429 | Too Many Requests | Rate limit exceeded |
| 500 | Internal Server Error | Unexpected server error |

### Custom Error Codes

| Code | Description |
|------|-------------|
| `USER_NOT_FOUND` | User does not exist |
| `EMAIL_ALREADY_EXISTS` | Email address already registered |
| `INVALID_CREDENTIALS` | Invalid username or password |
| `TOKEN_EXPIRED` | JWT token has expired |
| `TOKEN_INVALID` | JWT token is invalid or malformed |
| `ACCOUNT_LOCKED` | User account is locked |
| `ACCOUNT_INACTIVE` | User account is inactive |
| `VALIDATION_FAILED` | Input validation failed |

## 🔒 Security Considerations

### Password Requirements

- Minimum 8 characters
- At least one uppercase letter (A-Z)
- At least one lowercase letter (a-z)
- At least one digit (0-9)
- At least one special character (@$!%*?&)
- Cannot be a common password
- Cannot contain user's personal information

### Rate Limiting

| Endpoint Category | Limit | Window |
|------------------|-------|---------|
| Authentication | 5 requests | 1 minute |
| General API | 100 requests | 1 minute |
| Admin API | 200 requests | 1 minute |

### Token Security

- JWT tokens are signed with HMAC SHA-256
- Tokens expire after 1 hour
- Refresh tokens expire after 7 days
- Use HTTPS in production
- Store tokens securely (not in localStorage)

## 📊 Validation Rules

### Email Validation

- Must be valid email format
- Maximum 254 characters
- Local part maximum 64 characters
- Disposable email domains not allowed
- Blocked domains not allowed

### Phone Validation

- Vietnamese format: `+84` or `84` or `0` followed by valid mobile prefix
- Valid prefixes: 32-39, 56-59, 70, 76-79, 81-89, 90-96, 99
- Example: `+84901234567`, `0901234567`

### Name Validation

- 2-50 characters
- Letters, spaces, apostrophes, hyphens allowed
- No consecutive spaces
- No leading/trailing spaces
- No numbers only
- No profanity or harmful content

## 🧪 Testing the API

### Using cURL

```bash
# Register a new user
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

### Using Swagger UI

1. Navigate to http://localhost:8081/swagger-ui.html
2. Click "Authorize" button
3. Enter `Bearer <your-jwt-token>`
4. Test endpoints interactively

## 📈 Monitoring & Metrics

### Health Endpoints

- **Overall Health**: `/actuator/health`
- **Liveness Probe**: `/actuator/health/liveness`
- **Readiness Probe**: `/actuator/health/readiness`
- **Database Health**: `/actuator/health/db`

### Metrics Endpoints

- **All Metrics**: `/actuator/metrics`
- **Prometheus Format**: `/actuator/prometheus`
- **Application Info**: `/actuator/info`

## 🔄 API Versioning

### Current Version: v1

- Base path: `/api/v1`
- Stable and production-ready
- Backward compatibility maintained

### Future Versions

- New versions will be introduced as `/api/v2`, etc.
- Previous versions will be deprecated with advance notice
- Migration guides will be provided

## 📞 Support & Resources

### Documentation

- **API Examples**: [api-examples.md](../src/main/resources/api-examples.md)
- **Service README**: [README.md](../README.md)
- **Validation Messages**: [ValidationMessages.properties](../src/main/resources/ValidationMessages.properties)

### Development

- **Source Code**: GitHub Repository
- **Issue Tracking**: GitHub Issues
- **API Changes**: Check CHANGELOG.md

### Contact

- **Email**: dev@de013.org
- **Documentation**: This file and linked resources
- **Interactive Testing**: Swagger UI
