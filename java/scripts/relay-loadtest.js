import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend } from 'k6/metrics';

// 自定义指标
const relayFailRate = new Rate('relay_errors');
const relayLatency = new Trend('relay_latency_ms');

export const options = {
  stages: [
    { duration: '10s', target: 5 },   // 热身
    { duration: '20s', target: 20 },  // 爬升
    { duration: '30s', target: 50 },  // 峰值
    { duration: '10s', target: 0 },   // 冷却
  ],
  thresholds: {
    http_req_duration: ['p(95)<5000'],  // 95% 请求在 5s 内
    relay_errors: ['rate<0.05'],        // 错误率 < 5%
  },
};

const BASE = 'http://localhost:8081';

// 需要先准备好 API Key（见 setup 说明）
// 方式：登录 admin → 创建渠道指向 mock → 创建 API Key
const API_KEY = __ENV.API_KEY || 'sk-octopus-xxxxxxxx';

export default function () {
  const payload = JSON.stringify({
    model: 'gpt-4o-mock',
    messages: [
      { role: 'system', content: 'You are a helpful assistant.' },
      { role: 'user', content: 'Hello, tell me a story.' },
    ],
    temperature: 0.7,
    max_tokens: 100,
  });

  const res = http.post(`${BASE}/v1/chat/completions`, payload, {
    headers: {
      'Authorization': `Bearer ${API_KEY}`,
      'Content-Type': 'application/json',
    },
    timeout: '30s',
  });

  relayLatency.add(res.timings.duration);

  const success = check(res, {
    'status is 200': (r) => r.status === 200,
    'has choices': (r) => r.json('choices') !== undefined,
    'has id': (r) => r.json('id') !== undefined,
    'response time < 5s': (r) => r.timings.duration < 5000,
  });

  if (!success) {
    relayFailRate.add(1);
    console.error(`FAIL: ${res.status} ${res.body ? res.body.substring(0, 200) : ''}`);
  }

  sleep(0.1); // 100ms 间隔，模拟正常使用节奏
}
