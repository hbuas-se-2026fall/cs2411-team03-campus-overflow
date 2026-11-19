// CampusOverflow Sprint 4 压测脚本（#51 / PB-45）
//
// 用法（必须对着 compose 起的栈压，不要对着 IDEA 里的 dev 模式压）：
//   docker compose -f deploy/docker-compose.yml up -d --build
//   docker run --rm -i --network host grafana/k6 run \
//     --summary-trend-stats "avg,min,med,p(50),p(90),p(95),p(99),max" - < perf/questions-list.js
//
// 可选环境变量：
//   BASE_URL  默认 http://localhost:8080
//   RATE / DURATION / VUS  想改压测强度时覆盖（默认 50 并发、30 秒）
//
// 两个场景：
//   A  list   GET /api/v1/questions?page=0&size=20      （列表页，走 Redis 列表缓存）
//   B  detail GET /api/v1/questions/{questionId}        （详情页，QS-01 要求 P95 ≤ 300ms）
//
// 阈值：两个场景都要求 http_req_duration P95 < 300ms，且错误率 < 1%。
// 压完记得 docker compose down -v 清理（压测会写大量通知与声誉事件）。

import http from 'k6/http';
import { check } from 'k6';
import { Trend, Rate } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const VUS = Number(__ENV.VUS || 50);
const DURATION = __ENV.DURATION || '30s';

// 分场景的延迟趋势，便于报告里直接给出两条曲线的 P50/P95/P99
const listLatency = new Trend('list_latency_ms', true);
const detailLatency = new Trend('detail_latency_ms', true);
const listFailed = new Rate('list_failed');
const detailFailed = new Rate('detail_failed');

export const options = {
  scenarios: {
    list: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      exec: 'listScenario',
      tags: { scn: 'list' },
    },
    detail: {
      executor: 'constant-vus',
      vus: VUS,
      duration: DURATION,
      exec: 'detailScenario',
      startTime: DURATION, // 与列表场景串行，避免互相抢连接池影响读数
      tags: { scn: 'detail' },
    },
  },
  thresholds: {
    // QS-01：详情页 P95 ≤ 300ms；列表页按同一口径对待
    'http_req_duration{scn:list}': ['p(95)<300'],
    'http_req_duration{scn:detail}': ['p(95)<300'],
    'http_req_failed{scn:list}': ['rate<0.01'],
    'http_req_failed{scn:detail}': ['rate<0.01'],
  },
};

// 用列表里第一条问题作为详情页目标；列表为空说明演示数据没写进去，直接失败退出
export function setup() {
  const res = http.get(`${BASE_URL}/api/v1/questions?page=0&size=20`);
  if (res.status !== 200) {
    throw new Error(`setup 拉列表失败：HTTP ${res.status}`);
  }
  const body = JSON.parse(res.body);
  const items = body.items || body.content || [];
  if (!items.length) {
    throw new Error('问题列表为空，确认栈是用 prod,demo profile 起的');
  }
  return { questionId: items[0].id, total: body.total || items.length };
}

export function listScenario() {
  const res = http.get(`${BASE_URL}/api/v1/questions?page=0&size=20`, {
    tags: { name: 'GET /api/v1/questions' },
  });
  listLatency.add(res.timings.duration);
  const ok = check(res, {
    'list 200': (r) => r.status === 200,
    'list 有内容': (r) => (r.json('items') || []).length > 0 || r.status !== 200,
  });
  listFailed.add(!ok);
}

export function detailScenario(data) {
  const res = http.get(`${BASE_URL}/api/v1/questions/${data.questionId}`, {
    tags: { name: 'GET /api/v1/questions/:id' },
  });
  detailLatency.add(res.timings.duration);
  const ok = check(res, {
    'detail 200': (r) => r.status === 200,
    'detail 有标题': (r) => !!r.json('title'),
  });
  detailFailed.add(!ok);
}
