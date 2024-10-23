import http from 'k6/http';
import { check, sleep } from 'k6';

// 設定起始價格
let buyPrice = 50000;
let sellPrice = 50005;

// 訂單提交函數
function submitOrder(symbol, price, quantity, side, orderType, jwtToken) {
    const url = 'http://localhost:8081/orders/submit';
    const payload = JSON.stringify({
        symbol: symbol,
        price: price,
        quantity: quantity,
        side: side,
        orderType: orderType
    });

    const params = {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${jwtToken}`,
        },
    };

    const response = http.post(url, payload, params);
    check(response, { 'order submission successful': (r) => r.status === 200 });
}

export let options = {
    vus: 100, // 使用 50 個虛擬用戶
    duration: '10s', // 測試持續時間 30 秒
    rps: 100, // 限制每秒最多 100 個請求
    thresholds: {
        checks: ['rate>0.95'],  // 95%的請求成功率
        http_req_duration: ['p(95)<500'],  // 95%的請求在500毫秒內完成
    },
};

export default function () {
    const jwtToken = 'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjEiLCJ1c2VySWQiOiIxODM0NjY2NjE1NTQ4MDIyNzg0IiwiaWF0IjoxNzI2NDA1NDI3LCJleHAiOjE3MjY0NDE0Mjd9.JaiBDlvckAEwLVgqhPFdAOSYLAcCqUk2N9jfggur53w';

    // 模擬提交限價買單
    submitOrder('BTCUSDT', buyPrice, 1, 'BUY', 'LIMIT', jwtToken);
    buyPrice += 1; // 每次提交訂單後價格增加 1
    sleep(0.1); // 等待 0.1 秒

    // 模擬提交限價賣單
    submitOrder('BTCUSDT', sellPrice, 1, 'SELL', 'LIMIT', jwtToken);
    sellPrice -= 1; // 每次提交訂單後價格減少 1
    sleep(0.1); // 等待 0.1 秒
}
