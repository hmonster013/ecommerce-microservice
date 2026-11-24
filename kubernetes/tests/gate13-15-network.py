"""Run LOCAL network boundary checks against an explicitly selected disposable cluster."""
import argparse
import json
import subprocess

parser = argparse.ArgumentParser()
parser.add_argument("--kubectl", required=True)
parser.add_argument("--kubeconfig", required=True)
parser.add_argument("--context", required=True)
parser.add_argument("--namespace", required=True)
parser.add_argument("--probe-namespace", required=True)
parser.add_argument("--phase", choices=["baseline", "isolated"], required=True)
args = parser.parse_args()
if not args.context.startswith("kind-ec-g13-g15-"):
    parser.error("Refusing a non-fixture cluster")
kubectl = [args.kubectl, "--kubeconfig", args.kubeconfig, "--context", args.context]
apps = {"user-service": 8081, "product-catalog-service": 8082,
        "shopping-cart-service": 8083, "order-service": 8084,
        "payment-service": 8085, "notification-service": 8086}
results = []


def request(namespace, pod, url, expected_block=False, native=False):
    command = (["/bin/wget", "-T", "3", "-qO-", url] if native else
               ["curl", "--max-time", "3", "--connect-timeout", "2", "-sS", "-o", "/dev/null", "-w", "%{http_code}", url])
    result = subprocess.run(kubectl + ["-n", namespace, "exec", pod, "--"] + command,
                            capture_output=True, encoding="utf8", timeout=20)
    if expected_block:
        # A remote-command error is not evidence of network isolation.
        if native:
            assert result.returncode == 1 and "timed out" in result.stderr.lower(), result.stderr
        else:
            assert result.returncode == 28 and result.stdout.strip() == "000", (result.returncode, result.stdout, result.stderr)
    else:
        assert result.returncode == 0, (url, result.stderr)
        if not native:
            assert len(result.stdout.strip()) == 3 and result.stdout.strip() != "000", (url, result.stdout)
    results.append({"source": namespace + "/" + pod, "url": url,
                    "blocked": expected_block, "exit": result.returncode})


for namespace in [args.namespace, args.probe_namespace]:
    # DNS and a non-isolated real endpoint remain available in both phases.
    request(namespace, "probe", "http://keycloak." + args.namespace + ".svc.cluster.local:8080/realms/ecommerce-local/.well-known/openid-configuration")
    for app, port in apps.items():
        request(namespace, "probe", f"http://{app}.{args.namespace}.svc.cluster.local:{port}/actuator/health",
                args.phase == "isolated")
    if args.phase == "isolated":
        for app in [*apps, "api-gateway-management"]:
            request(namespace, "probe", f"http://{app}.{args.namespace}.svc.cluster.local:9010/actuator/prometheus", True)

if args.phase == "isolated":
    for app, port in apps.items():
        request(args.namespace, "deployment/prometheus", f"http://{app}:{port}/actuator/health", True, True)
        request(args.namespace, "deployment/prometheus", f"http://{app}:9010/actuator/prometheus", native=True)
    request(args.namespace, "deployment/prometheus", "http://api-gateway-management:9010/actuator/prometheus", native=True)
print(json.dumps({"phase": args.phase, "checks": results}, indent=2))
