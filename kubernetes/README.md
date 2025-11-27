# Kubernetes Deployment

Complete Kubernetes manifests for deploying the e-commerce microservices platform.

## Quick Start

### 1. Setup Configuration

```bash
# Copy and configure
cp configmap.yaml.example configmap.yaml
cp secrets.yaml.example secrets.yaml

# Edit with your actual values
nano configmap.yaml  # Update email, Twilio SID, etc.
nano secrets.yaml    # Update passwords, API keys, tokens
```

⚠️ **Security**: `configmap.yaml` and `secrets.yaml` are gitignored. Never commit actual secrets!

### 2. Deploy

**Automated (Recommended):**
```bash
./deploy.bat      # Windows
./deploy.sh       # Linux/Mac
```

**Manual:**
```bash
# 1. Apply configs
kubectl apply -f configmap.yaml
kubectl apply -f secrets.yaml
kubectl create configmap keycloak-realm-config \
  --from-file=ecommerce-realm-realm.json=../keycloak/realm-config/ecommerce-realm-realm.json \
  --dry-run=client -o yaml | kubectl apply -f -

# 2. Deploy infrastructure
kubectl apply -f infrastructure/

# 3. Deploy observability (optional)
kubectl apply -f observability/

# 4. Deploy services
kubectl apply -f services/config-server.yaml
sleep 30
kubectl apply -f services/eureka-server.yaml
sleep 30
kubectl apply -f services/
```

### 3. Verify

```bash
kubectl get pods
kubectl get svc
```

## Access Services

| Service | URL | Description |
|---------|-----|-------------|
| API Gateway | http://localhost:8080 | Main entry point |
| Eureka | http://localhost:8761 | Service discovery |
| Keycloak | http://localhost:8090 | Authentication |
| Grafana | http://localhost:3000 | Monitoring dashboard |

## Architecture

**Infrastructure (6 services):**
- PostgreSQL, Redis, RabbitMQ, Kafka, Keycloak + DB

**Core Services (3):**
- Config Server, Eureka Server, API Gateway

**Business Services (6):**
- User, Product Catalog, Shopping Cart, Order, Payment, Notification

**Observability (5):**
- Prometheus, Tempo, Grafana, Loki Stack, Alloy

## Troubleshooting

**Pods not starting?**
```bash
kubectl describe pod <pod-name>
kubectl logs <pod-name>
```

**Service connection issues?**
```bash
kubectl get endpoints
```

**Check all events:**
```bash
kubectl get events --sort-by='.lastTimestamp'
```

## Cleanup

```bash
./cleanup.bat  # Windows
./cleanup.sh   # Linux/Mac
```

## Production Checklist

- [ ] Use external secrets manager (Vault, AWS Secrets Manager)
- [ ] Add resource requests/limits
- [ ] Enable persistent storage with PVCs
- [ ] Configure Ingress for external access
- [ ] Enable TLS/SSL
- [ ] Implement network policies
- [ ] Set up monitoring alerts
- [ ] Configure database backups
- [ ] Increase replicas for HA
- [ ] Review and harden RBAC policies

## Notes

- Deploy time: ~5-10 minutes depending on cluster resources
- Requires ~8GB RAM for full stack
- Services use Eureka for discovery, no direct K8s service calls needed
- OpenTelemetry configs included (requires agent in Docker images)
