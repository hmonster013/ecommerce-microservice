# k6 Load Test

Baseline load test for the platform, targeting the product listing endpoint through the API Gateway.

## Run

Prerequisites: [k6](https://k6.io/) installed and the full prod stack running
(see the main README, "Khởi động full-stack").

```bash
# Without auth (if the route is public):
k6 run -e BASE_URL=http://localhost:8080 k6/load-test.js

# With a Bearer token (if the route is secured at the gateway):
k6 run -e BASE_URL=http://localhost:8080 -e TOKEN=<jwt> k6/load-test.js
```

Get a token via the auth endpoint, e.g.:

```bash
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"<user>","password":"<pass>"}'
```

## Profile

- Ramp 0 → 20 VUs over 30s, hold 20 VUs for 1m, ramp down over 30s.
- Thresholds: `p(95) < 500ms`, error rate `< 1%`.

## Baseline results

> Chạy `k6 run ...` ở trên rồi điền số liệu vào bảng (môi trường local).

| Metric            | Value |
|-------------------|-------|
| Environment       | local (CPU / RAM …) |
| Total requests    | … |
| Requests/s (avg)  | … |
| Latency p95       | … ms |
| Latency p99       | … ms |
| Error rate        | … % |
| Date              | … |

_Note: số liệu local chỉ mang tính tham khảo, không phải benchmark production._
