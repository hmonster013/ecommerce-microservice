package org.de013.shoppingcart.documentation;

/**
 * API Examples Documentation
 * Contains example requests and responses for Swagger documentation
 */
public class ApiExamples {

    // ==================== CART CREATION EXAMPLES ====================
    
    public static final String CREATE_USER_CART_REQUEST = """
        {
          "cartType": "USER",
          "currency": "USD",
          "notes": "Birthday shopping cart"
        }
        """;
    
    public static final String CREATE_USER_CART_RESPONSE = """
        {
          "success": true,
          "cart": {
            "id": 12345,
            "userId": "user-123",
            "cartType": "USER",
            "status": "ACTIVE",
            "currency": "USD",
            "itemCount": 0,
            "totalQuantity": 0,
            "subtotal": 0.00,
            "taxAmount": 0.00,
            "shippingAmount": 0.00,
            "discountAmount": 0.00,
            "totalAmount": 0.00,
            "createdAt": "2024-01-15T10:30:00Z",
            "updatedAt": "2024-01-15T10:30:00Z",
            "expiresAt": "2024-01-16T10:30:00Z",
            "notes": "Birthday shopping cart"
          },
          "message": "Cart created successfully",
          "timestamp": "2024-01-15T10:30:00Z"
        }
        """;
    
    public static final String CREATE_GUEST_CART_RESPONSE = """
        {
          "success": true,
          "cart": {
            "id": 12346,
            "userId": null,
            "sessionId": "guest-session-abc123",
            "cartType": "GUEST",
            "status": "ACTIVE",
            "currency": "USD",
            "itemCount": 0,
            "totalQuantity": 0,
            "subtotal": 0.00,
            "taxAmount": 0.00,
            "shippingAmount": 0.00,
            "discountAmount": 0.00,
            "totalAmount": 0.00,
            "createdAt": "2024-01-15T10:30:00Z",
            "updatedAt": "2024-01-15T10:30:00Z",
            "expiresAt": "2024-01-15T12:30:00Z"
          },
          "token": "eyJhbGciOiJIUzUxMiJ9...",
          "message": "Guest cart created successfully",
          "timestamp": "2024-01-15T10:30:00Z"
        }
        """;

    // ==================== ADD ITEM EXAMPLES ====================
    
    public static final String ADD_ITEM_REQUEST = """
        {
          "productId": "prod-456",
          "variantId": "variant-789",
          "quantity": 2,
          "unitPrice": 29.99,
          "currency": "USD",
          "productName": "Wireless Bluetooth Headphones",
          "productDescription": "High-quality wireless headphones with noise cancellation",
          "productImageUrl": "https://example.com/images/headphones.jpg",
          "categoryId": "electronics",
          "categoryName": "Electronics",
          "productSku": "WBH-001",
          "variantAttributes": {
            "color": "Black",
            "size": "Standard"
          },
          "specialInstructions": "Gift wrap requested"
        }
        """;
    
    public static final String ADD_ITEM_RESPONSE = """
        {
          "success": true,
          "cartItem": {
            "id": 67890,
            "cartId": 12345,
            "productId": "prod-456",
            "variantId": "variant-789",
            "quantity": 2,
            "unitPrice": 29.99,
            "totalPrice": 59.98,
            "currency": "USD",
            "productName": "Wireless Bluetooth Headphones",
            "productDescription": "High-quality wireless headphones with noise cancellation",
            "productImageUrl": "https://example.com/images/headphones.jpg",
            "categoryId": "electronics",
            "categoryName": "Electronics",
            "productSku": "WBH-001",
            "variantAttributes": {
              "color": "Black",
              "size": "Standard"
            },
            "specialInstructions": "Gift wrap requested",
            "addedAt": "2024-01-15T10:35:00Z",
            "updatedAt": "2024-01-15T10:35:00Z",
            "availabilityStatus": "IN_STOCK",
            "stockQuantity": 150
          },
          "cartSummary": {
            "itemCount": 1,
            "totalQuantity": 2,
            "subtotal": 59.98,
            "taxAmount": 4.80,
            "shippingAmount": 5.99,
            "discountAmount": 0.00,
            "totalAmount": 70.77
          },
          "message": "Item added to cart successfully",
          "timestamp": "2024-01-15T10:35:00Z"
        }
        """;

    // ==================== CART SUMMARY EXAMPLES ====================
    
    public static final String CART_SUMMARY_RESPONSE = """
        {
          "success": true,
          "cart": {
            "id": 12345,
            "userId": "user-123",
            "cartType": "USER",
            "status": "ACTIVE",
            "currency": "USD",
            "itemCount": 3,
            "totalQuantity": 5,
            "subtotal": 149.97,
            "taxAmount": 12.00,
            "shippingAmount": 9.99,
            "discountAmount": 15.00,
            "totalAmount": 156.96,
            "createdAt": "2024-01-15T10:30:00Z",
            "updatedAt": "2024-01-15T11:45:00Z",
            "lastActivityAt": "2024-01-15T11:45:00Z",
            "expiresAt": "2024-01-16T10:30:00Z"
          },
          "items": [
            {
              "id": 67890,
              "productId": "prod-456",
              "productName": "Wireless Bluetooth Headphones",
              "quantity": 2,
              "unitPrice": 29.99,
              "totalPrice": 59.98,
              "productImageUrl": "https://example.com/images/headphones.jpg"
            },
            {
              "id": 67891,
              "productId": "prod-789",
              "productName": "Smartphone Case",
              "quantity": 1,
              "unitPrice": 19.99,
              "totalPrice": 19.99,
              "productImageUrl": "https://example.com/images/case.jpg"
            },
            {
              "id": 67892,
              "productId": "prod-101",
              "productName": "USB-C Cable",
              "quantity": 2,
              "unitPrice": 35.00,
              "totalPrice": 70.00,
              "productImageUrl": "https://example.com/images/cable.jpg"
            }
          ],
          "pricing": {
            "subtotal": 149.97,
            "discounts": [
              {
                "type": "BULK_DISCOUNT",
                "description": "10% off for 3+ items",
                "amount": 15.00
              }
            ],
            "taxBreakdown": [
              {
                "jurisdiction": "CA",
                "rate": 0.08,
                "amount": 12.00
              }
            ],
            "shipping": {
              "method": "STANDARD",
              "cost": 9.99,
              "estimatedDays": 3
            },
            "totalAmount": 156.96
          },
          "timestamp": "2024-01-15T11:45:00Z"
        }
        """;

    // ==================== ERROR RESPONSE EXAMPLES ====================
    
    public static final String ERROR_CART_NOT_FOUND = """
        {
          "success": false,
          "error": "Cart not found",
          "errorCode": "CART_1001",
          "errorCategory": "Cart Management",
          "details": {
            "cartId": 99999,
            "message": "The requested cart does not exist or has been deleted"
          },
          "timestamp": "2024-01-15T10:30:00Z",
          "path": "/api/v1/carts/99999",
          "method": "GET"
        }
        """;
    
    public static final String ERROR_INSUFFICIENT_STOCK = """
        {
          "success": false,
          "error": "Insufficient stock",
          "errorCode": "ITEM_2004",
          "errorCategory": "Cart Items",
          "details": {
            "productId": "prod-456",
            "requestedQuantity": 10,
            "availableQuantity": 3,
            "message": "Not enough stock available for the requested quantity"
          },
          "timestamp": "2024-01-15T10:30:00Z",
          "path": "/api/v1/carts/12345/items",
          "method": "POST"
        }
        """;
    
    public static final String ERROR_AUTHENTICATION_REQUIRED = """
        {
          "success": false,
          "error": "Authentication required",
          "errorCode": "AUTH_3001",
          "errorCategory": "Authentication",
          "details": {
            "message": "Authentication is required to access this resource",
            "requiredRole": "USER"
          },
          "timestamp": "2024-01-15T10:30:00Z",
          "path": "/api/v1/carts",
          "method": "POST"
        }
        """;

    // ==================== ANALYTICS EXAMPLES ====================
    
    public static final String ANALYTICS_RESPONSE = """
        {
          "success": true,
          "analytics": {
            "cartId": 12345,
            "userId": "user-123",
            "totalEvents": 15,
            "sessionDuration": 1800,
            "conversionProbability": 0.75,
            "abandonmentRisk": "LOW",
            "events": [
              {
                "eventType": "CART_CREATED",
                "timestamp": "2024-01-15T10:30:00Z",
                "details": {
                  "cartType": "USER",
                  "currency": "USD"
                }
              },
              {
                "eventType": "ITEM_ADDED",
                "timestamp": "2024-01-15T10:35:00Z",
                "details": {
                  "productId": "prod-456",
                  "quantity": 2,
                  "unitPrice": 29.99
                }
              },
              {
                "eventType": "PRICING_CALCULATED",
                "timestamp": "2024-01-15T10:35:30Z",
                "details": {
                  "subtotal": 59.98,
                  "totalAmount": 70.77
                }
              }
            ],
            "recommendations": [
              {
                "type": "CROSS_SELL",
                "productId": "prod-999",
                "productName": "Wireless Charger",
                "reason": "Frequently bought together"
              }
            ]
          },
          "timestamp": "2024-01-15T11:45:00Z"
        }
        """;

    // ==================== PERFORMANCE MONITORING EXAMPLES ====================
    
    public static final String PERFORMANCE_METRICS_RESPONSE = """
        {
          "cartOperations": 1250,
          "cacheHits": 3200,
          "cacheMisses": 180,
          "databaseQueries": 450,
          "externalServiceCalls": 75,
          "cacheHitRatio": 0.947,
          "performanceSummary": {
            "totalOperations": 1250,
            "cacheEfficiency": "94.70%",
            "databaseQueryRatio": "0.36",
            "externalServiceRatio": "0.06"
          },
          "cacheStatistics": {
            "l1Cache": "caffeine",
            "l2Cache": "redis",
            "totalHits": 3200,
            "totalMisses": 180,
            "hitRatio": 0.947,
            "missRatio": 0.053
          },
          "timingStatistics": {
            "cart.operation": {
              "count": 1250,
              "totalTime": 45000,
              "mean": 36.0,
              "max": 150.0
            },
            "database.query": {
              "count": 450,
              "totalTime": 18000,
              "mean": 40.0,
              "max": 200.0
            }
          },
          "timestamp": "2024-01-15T11:45:00Z"
        }
        """;

    // ==================== GUEST SESSION EXAMPLES ====================
    
    public static final String GUEST_SESSION_RESPONSE = """
        {
          "success": true,
          "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJndWVzdCIsInVzZXJJZCI6bnVsbCwicm9sZXMiOlsiR1VFU1QiXSwic2Vzc2lvbklkIjoiZ3Vlc3Qtc2Vzc2lvbi1hYmMxMjMiLCJpc0d1ZXN0Ijp0cnVlLCJpc3MiOiJzaG9wcGluZy1jYXJ0LXNlcnZpY2UiLCJpYXQiOjE3MDUzMTQwMDAsImV4cCI6MTcwNTMyMTIwMH0.signature",
          "sessionId": "guest-session-abc123",
          "tokenType": "Bearer",
          "expiresAt": "2024-01-15T12:30:00Z",
          "isGuest": true,
          "message": "Guest session created successfully",
          "timestamp": "2024-01-15T10:30:00Z"
        }
        """;
}
