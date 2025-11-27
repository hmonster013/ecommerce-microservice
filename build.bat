@echo off
echo Building E-commerce Microservices Platform...
echo.

echo Cleaning...
call mvnw.cmd clean
if %ERRORLEVEL% NEQ 0 exit /b 1

echo Building...
call mvnw.cmd install -DskipTests
if %ERRORLEVEL% NEQ 0 exit /b 1

echo Building and pushing Docker images to Docker Hub...
call mvnw.cmd compile jib:build -DskipTests -pl config-server,eureka-server,api-gateway,user-service,product-catalog-service,shopping-cart-service,order-service,payment-service,notification-service
if %ERRORLEVEL% NEQ 0 exit /b 1

echo.
echo Build and push completed!
echo Images pushed to Docker Hub with tag: s1
