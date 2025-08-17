#!/bin/bash

echo "Opening Swagger UI for all microservices..."

echo ""
echo "Opening API Gateway Swagger (All APIs)..."
if command -v xdg-open > /dev/null; then
    xdg-open http://localhost:8080/swagger-ui.html
elif command -v open > /dev/null; then
    open http://localhost:8080/swagger-ui.html
fi

sleep 2

echo "Opening User Service Swagger..."
if command -v xdg-open > /dev/null; then
    xdg-open http://localhost:8081/swagger-ui.html
elif command -v open > /dev/null; then
    open http://localhost:8081/swagger-ui.html
fi

sleep 1

echo "Opening Product Catalog Swagger..."
if command -v xdg-open > /dev/null; then
    xdg-open http://localhost:8082/swagger-ui.html
elif command -v open > /dev/null; then
    open http://localhost:8082/swagger-ui.html
fi

sleep 1

echo "Opening Shopping Cart Swagger..."
if command -v xdg-open > /dev/null; then
    xdg-open http://localhost:8083/swagger-ui.html
elif command -v open > /dev/null; then
    open http://localhost:8083/swagger-ui.html
fi

sleep 1

echo "Opening Order Service Swagger..."
if command -v xdg-open > /dev/null; then
    xdg-open http://localhost:8084/swagger-ui.html
elif command -v open > /dev/null; then
    open http://localhost:8084/swagger-ui.html
fi

sleep 1

echo "Opening Payment Service Swagger..."
if command -v xdg-open > /dev/null; then
    xdg-open http://localhost:8085/swagger-ui.html
elif command -v open > /dev/null; then
    open http://localhost:8085/swagger-ui.html
fi

sleep 1

echo "Opening Notification Service Swagger..."
if command -v xdg-open > /dev/null; then
    xdg-open http://localhost:8086/swagger-ui.html
elif command -v open > /dev/null; then
    open http://localhost:8086/swagger-ui.html
fi

echo ""
echo "All Swagger UIs opened!"
echo ""
echo "Main API Gateway Swagger: http://localhost:8080/swagger-ui.html"
echo "Eureka Dashboard: http://localhost:8761"
echo ""
