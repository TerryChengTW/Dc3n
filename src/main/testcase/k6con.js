import http from 'k6/http';
import { sleep, check } from 'k6';

// 基本配置
const BASE_URL = 'http://localhost:8081/orders/submit';
const SYMBOL = 'BTCUSDT';
const PRICE_RANGE = [30000, 70000]; // 價格範圍
const ORDER_QUANTITY_RANGE = [0.1, 1.0]; // 訂單數量範圍
const JWT_TOKENS = [
  'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjMiLCJ1c2VySWQiOiIxODM4MDI5MDA0NjE2MTEwMDgwIiwiaWF0IjoxNzI3MDU1NjA5LCJleHAiOjE3NjMwNTU2MDl9.DU31c_NFobpFS8VfjlMCaV5kSVgBvPst6K7DcaanMWc',
  'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjQiLCJ1c2VySWQiOiIxODM4MDI5MDQzMjQ5ODQ0MjI0IiwiaWF0IjoxNzI3MDU1MzE5LCJleHAiOjE3NjMwNTUzMTl9.jqyeE6C1N6XDRsYU4uOlvuQG4H46EDBwPzbcQ5ip3Js'
];

// k6 配置
export let options = {
  stages: [
    { duration: '5s', target: 500 }, // 1 分鐘內增加到 100 個虛擬用戶
    { duration: '5m', target: 500 }, // 保持 100 個虛擬用戶 5 分鐘
    { duration: '1m', target: 0 },   // 1 分鐘內降到 0 個虛擬用戶
  ],
};

// 隨機生成訂單價格和數量
function generateOrder() {
  const price = Math.floor(Math.random() * (PRICE_RANGE[1] - PRICE_RANGE[0] + 1)) + PRICE_RANGE[0];
  const quantity = (Math.random() * (ORDER_QUANTITY_RANGE[1] - ORDER_QUANTITY_RANGE[0]) + ORDER_QUANTITY_RANGE[0]).toFixed(2);
  const side = Math.random() > 0.5 ? 'BUY' : 'SELL';

  return { price, quantity, side };
}

export default function () {
  const order = generateOrder();

  // 設定訂單 payload
  const payload = JSON.stringify({
    symbol: SYMBOL,
    price: order.price,
    quantity: order.quantity,
    side: order.side,
    orderType: 'LIMIT',
  });

  // 隨機選擇 JWT token
  const headers = {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${JWT_TOKENS[Math.floor(Math.random() * JWT_TOKENS.length)]}`,
  };

  // 發送 POST 請求
  const res = http.post(BASE_URL, payload, { headers });

  // 檢查請求是否成功
  check(res, {
    'is status 200': (r) => r.status === 200,
  });

}
