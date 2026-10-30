#!/usr/bin/env node
/**
 * 秒杀并发正确性验证（仅用 Node.js 标准库）。
 *
 * 目的：验证「本地库存配额预扣减 + Redis Lua 原子扣减 + 唯一索引幂等」三者组合下，
 *       并发抢购不会超卖，也不会出现库存「幽灵扣减」。
 *
 * 用法：
 *   node scripts/seckill-stress.mjs [baseUrl] [activityId] [并发数] [总请求数]
 *   默认：http://127.0.0.1:8080  活动1  并发50  总请求200
 */

const BASE = process.argv[2] || 'http://127.0.0.1:8080';
const ACTIVITY_ID = Number(process.argv[3] || 1);
const CONCURRENCY = Number(process.argv[4] || 50);
const TOTAL = Number(process.argv[5] || 200);

async function call(method, path, body) {
  const init = { method, headers: { Accept: 'application/json' } };
  if (body !== undefined) {
    init.headers['Content-Type'] = 'application/json;charset=UTF-8';
    init.body = JSON.stringify(body);
  }
  const res = await fetch(BASE + path, { ...init, signal: AbortSignal.timeout(60000) });
  const text = await res.text();
  try {
    return JSON.parse(text);
  } catch {
    return { raw: text };
  }
}

async function main() {
  const before = await call('GET', `/api/seckill/activity/${ACTIVITY_ID}/stock`);
  console.log('抢购前库存：', JSON.stringify(before.data));
  const stockBefore = before.data.dbRemainStock;

  let cursor = 0;
  const successOrders = [];
  const codeCount = {};

  async function worker(workerId) {
    while (true) {
      const index = cursor++;
      if (index >= TOTAL) {
        return;
      }
      // 每个请求使用不同用户，避免被幂等规则拦截
      const userId = 900000 + workerId * 100000 + index;
      const body = { activityId: ACTIVITY_ID, userId, quantity: 1 };
      try {
        const res = await call('POST', '/api/seckill/order', body);
        const code = res.code;
        codeCount[code] = (codeCount[code] || 0) + 1;
        if (code === 200 && res.data?.orderNo) {
          successOrders.push(res.data.orderNo);
        }
      } catch (err) {
        codeCount['EXCEPTION'] = (codeCount['EXCEPTION'] || 0) + 1;
      }
    }
  }

  const start = Date.now();
  await Promise.all(Array.from({ length: CONCURRENCY }, (_, i) => worker(i)));
  const cost = Date.now() - start;

  const after = await call('GET', `/api/seckill/activity/${ACTIVITY_ID}/stock`);
  console.log('抢购后库存：', JSON.stringify(after.data));
  const stockAfter = after.data.dbRemainStock;

  const uniqueOrders = new Set(successOrders).size;
  const dbConsumed = stockBefore - stockAfter;
  const total = Object.values(codeCount).reduce((a, b) => a + b, 0);

  console.log(`\n请求数=${TOTAL} 并发=${CONCURRENCY} 耗时=${cost}ms`);
  console.log('返回码分布：', JSON.stringify(codeCount));
  console.log(`成功订单数=${successOrders.length}（去重后 ${uniqueOrders}）`);
  console.log(`DB 库存扣减=${dbConsumed}，Redis 剩余=${after.data?.redisStock}，本地配额=${after.data?.localQuota}`);

  let ok = true;
  if (uniqueOrders !== successOrders.length) {
    console.error('[FAIL] 存在重复订单号');
    ok = false;
  }
  if (dbConsumed !== successOrders.length) {
    console.error(`[FAIL] 超卖/少卖：DB 扣减 ${dbConsumed} != 成功订单 ${successOrders.length}`);
    ok = false;
  }
  if (stockAfter < 0) {
    console.error('[FAIL] DB 库存出现负数');
    ok = false;
  }
  if ((after.data?.redisStock ?? 0) + (after.data?.localQuota ?? 0) + dbConsumed
      > (before.data?.redisStock ?? 0) + (before.data?.localQuota ?? 0) + stockBefore) {
    console.error('[FAIL] 总库存账目不平（疑似超卖）');
    ok = false;
  }
  console.log(ok ? '\n[PASS] 并发扣减正确：无超卖、无重复订单、库存账目自洽' : '\n[FAIL] 并发扣减存在问题');
  process.exit(ok ? 0 : 1);
}

main().catch((err) => {
  console.error(`脚本异常：${err.message}`);
  process.exit(2);
});
