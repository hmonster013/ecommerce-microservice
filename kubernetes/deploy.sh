#!/bin/bash

echo "Deploying E-commerce Microservices to Kubernetes..."
echo ""

echo "Deploying ConfigMap and Secrets..."
kubectl apply -f configmap.yaml
kubectl apply -f secrets.yaml
kubectl create configmap keycloak-realm-config --from-file=ecommerce-realm-realm.json=../keycloak/realm-config/ecommerce-realm-realm.json --dry-run=client -o yaml | kubectl apply -f -

echo "Deploying Infrastructure..."
kubectl apply -f infrastructure/
sleep 60

echo "Deploying Observability..."
kubectl apply -f observability/
sleep 60

echo "Deploying Services..."
kubectl apply -f services/config-server.yaml
sleep 3
kubectl apply -f services/eureka-server.yaml
sleep 3
kubectl apply -f services/

echo ""
echo "Deployment completed!"
echo ""
echo "Access URLs:"
echo "  API Gateway:   http://localhost:8080"
echo "  Eureka:        http://localhost:8761"
echo "  Keycloak:      http://localhost:8090"
echo "  Grafana:       http://localhost:3000"
echo ""
kubectl get pods
