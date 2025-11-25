@echo off
echo Building E-commerce Microservices Platform with Jib...

echo.
echo [1/3] Cleaning previous builds...
call mvnw.cmd clean

echo.
echo [2/3] Building all modules...
call mvnw.cmd package -DskipTests

echo.
echo [3/3] Building Docker images with Jib...
echo Building images to Docker daemon...
call mvnw.cmd compile jib:dockerBuild -DskipTests

echo.
echo Build completed successfully!
echo.
echo Images created:
echo   - de013/ecommerce-microservice/config-server:s1
echo   - de013/ecommerce-microservice/eureka-server:s1
echo   - de013/ecommerce-microservice/api-gateway:s1
echo   - de013/ecommerce-microservice/user-service:s1
echo   - de013/ecommerce-microservice/product-catalog-service:s1
echo   - de013/ecommerce-microservice/shopping-cart-service:s1
echo   - de013/ecommerce-microservice/order-service:s1
echo   - de013/ecommerce-microservice/payment-service:s1
echo   - de013/ecommerce-microservice/notification-service:s1
echo.
echo To push images to registry:
echo   mvnw.cmd compile jib:build -DskipTests
echo.
echo To start the platform:
echo   docker-compose up -d
echo.
echo To view logs:
echo   docker-compose logs -f
echo.
echo To stop the platform:
echo   docker-compose down
