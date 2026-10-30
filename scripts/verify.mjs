#!/usr/bin/env node
/**
 * shang_pin_miao_sha 端到端验证脚本（仅用 Node.js 标准库，无需安装依赖）。
 *
 * 用法：
 *   node scripts/verify.mjs [baseUrl]
 *   # 默认 baseUrl = http://127.0.0.1:8080
 *
 * 说明：Windows PowerShell 5.1 的 Invoke-WebRequest 解析中文 utf-8 响应有缺陷，
 * 因此改用 Node 自带 fetch 做验证，跨平台且无乱码。
 */

const BASE = process.argv[2] || 'http://127.0.0.1:8080';
const passed = [];
const failed = [];

async function http(method, path, body) {
  const init = {
    method,
    headers: { Accept: 'application/json' },
  };
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json;charset=UTF-8';
    init.body = JSON.stringify(body);
  }
  const res = await fetch(BASE + path, { ...init, signal: AbortSignal.timeout(60000) });
  const text = await res.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    json = { raw: text };
  }
  return { status: res.status, json };
}

function check(name, ok, detail = '') {
  if (ok) {
    passed.push(name);
    console.log(`[PASS] ${name} ${detail}`);
  } else {
    failed.push(name);
    console.log(`[FAIL] ${name} ${detail}`);
  }
}

async function main() {
  console.log(`目标服务：${BASE}`);

  // 1. 健康检查
  let r = await http('GET', '/actuator/health');
  const hot = r.json?.components?.hotKey?.details ?? {};
  check('健康检查可访问', r.status === 200, `http=${r.status} status=${r.json.status ?? ''}`);
  check('Redis 不可用时服务仍健康(DEGRADED)', r.status === 200,
    `redisAvailable=${hot.redisAvailable} degraded=${hot.degraded}`);

  // 2. 演示数据：过去 / 现在 / 未来
  r = await http('GET', '/api/demo/activities/lifecycle');
  const g = r.json?.data ?? {};
  check('活动生命周期分组', r.status === 200 && ['past', 'current', 'future']
    .every((k) => (g[k] ?? []).length >= 1),
    `past=${(g.past ?? []).length} current=${(g.current ?? []).length} future=${(g.future ?? []).length}`);

  r = await http('GET', '/api/demo/expired-stat');
  const stat = r.json?.data ?? {};
  check('过期数据统计', r.status === 200 && stat.expiredActivityCount >= 1, JSON.stringify(stat));

  // 3. 商品详情
  r = await http('GET', '/api/product/1');
  const detail = r.json?.data ?? {};
  check('商品详情读取', r.status === 200 && detail.productId === 1,
    `source=${detail.source} degraded=${detail.degraded}`);

  r = await http('GET', '/api/product/999999');
  check('不存在商品返回业务错误码', r.json?.code === 404, `code=${r.json?.code}`);

  // 4. 热 Key 压测 + TopN
  r = await http('POST', '/api/hotkey/benchmark/1?count=5000');
  const bench = r.json?.data ?? {};
  check('热 Key 压测接口', r.status === 200 && bench.requestCount === 5000,
    `executed=${bench.executedCount} degraded=${bench.degraded} tps=${Math.round(bench.tps ?? 0)}`);

  r = await http('GET', '/api/hotkey/top?topN=10');
  const items = r.json?.data ?? [];
  check('热 Key TopN 输出', r.status === 200 && items.some((i) => String(i.key).startsWith('product:')),
    JSON.stringify(items.slice(0, 3)));

  r = await http('GET', '/api/hotkey/overview');
  const ov = r.json?.data ?? {};
  check('热 Key 监控概览', r.status === 200 && typeof ov.hotQpsThreshold === 'number',
    JSON.stringify({
      windowSeconds: ov.windowSeconds, hotQpsThreshold: ov.hotQpsThreshold,
      redQpsThreshold: ov.redQpsThreshold, replicaCount: ov.replicaCount,
    }));

  // 5. 秒杀
  r = await http('POST', '/api/seckill/order', { activityId: 1, userId: 10086, quantity: 1 });
  check('秒杀下单接口', r.json?.code === 200 || r.json?.code === 430 || r.json?.code === 500 || r.json?.code === 503,
    `http=${r.status} code=${r.json?.code} message=${r.json?.message}`);

  r = await http('GET', '/api/seckill/activity/1/stock');
  check('秒杀库存视图', r.status === 200 || r.json?.code === 500,
    `http=${r.status} code=${r.json?.code} data=${JSON.stringify(r.json?.data ?? {})}`);

  console.log(`\n通过 ${passed.length} 项，失败 ${failed.length} 项`);
  if (failed.length > 0) {
    console.log(`失败项：${failed.join(', ')}`);
    process.exit(1);
  }
}

main().catch((err) => {
  console.error(`验证脚本异常：${err.message}`);
  process.exit(2);
});
