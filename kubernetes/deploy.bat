@echo off
echo Deploying E-commerce Microservices to Kubernetes...
echo.

echo Deploying ConfigMap and Secrets...
kubectl apply -f configmap.yaml
kubectl apply -f secrets.yaml
kubectl create configmap keycloak-realm-config --from-file=ecommerce-realm-realm.json=../docker/keycloak/realm-config/ecommerce-realm-realm.json --dry-run=client -o yaml | kubectl apply -f -

echo Deploying Infrastructure...
kubectl apply -f infrastructure/
timeout /t 60 /nobreak >nul

echo Deploying Observability...
kubectl apply -f observability/
timeout /t 60 /nobreak >nul

echo Deploying Services...
kubectl apply -f services/config-server.yaml
timeout /t 3 /nobreak >nul
kubectl apply -f services/eureka-server.yaml
timeout /t 3 /nobreak >nul
kubectl apply -f services/

echo.
echo Deployment completed!
echo.
echo Access URLs:
echo   API Gateway:   http://localhost:8080
echo   Eureka:        http://localhost:8761
echo   Keycloak:      http://localhost:8090
echo   Grafana:       http://localhost:3000
echo.
kubectl get pods
