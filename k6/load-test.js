import http from 'k6/http';
import { check, sleep } from 'k6';

/**
 * k6 Load Test — E-commerce Microservices Platform
 *
 * Target: GET /api/v1/product-catalog-service/products (through the API Gateway).
 *
 * Run:
 *   k6 run -e BASE_URL=http://localhost:8080 -e TOKEN=<jwt> k6/load-test.js
 *
 * Notes:
 * - BASE_URL defaults to the API Gateway on http://localhost:8080.
 * - TOKEN is optional: if the product listing route is secured at the gateway,
 *   pass a Bearer token via -e TOKEN=...; otherwise it can be omitted.
 * - The full prod stack must be running (see README "Khởi động full-stack").
 */

export const options = {
  stages: [
    { duration: '30s', target: 20 }, // ramp up to 20 VUs
    { duration: '1m', target: 20 },  // hold at 20 VUs
    { duration: '30s', target: 0 },  // ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% of requests under 500ms
    http_req_failed: ['rate<0.01'],   // error rate under 1%
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';

export default function () {
  const url = `${BASE_URL}/api/v1/product-catalog-service/products`;

  const headers = {
    'Content-Type': 'application/json',
    'User-Agent': 'k6-load-test',
  };
  if (TOKEN) {
    headers['Authorization'] = `Bearer ${TOKEN}`;
  }

  const res = http.get(url, { headers });

  check(res, {
    'status is 200': (r) => r.status === 200,
  });

  sleep(1); // think-time between requests
}
