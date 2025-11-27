@echo off
echo Cleaning up Kubernetes resources...
echo.

kubectl delete -f services/ --ignore-not-found=true
kubectl delete -f observability/ --ignore-not-found=true
kubectl delete -f infrastructure/ --ignore-not-found=true
kubectl delete -f configmap.yaml --ignore-not-found=true 2>nul
kubectl delete -f secrets.yaml --ignore-not-found=true 2>nul

echo.
echo Cleanup completed!
kubectl get pods
