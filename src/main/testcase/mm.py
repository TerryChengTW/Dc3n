import asyncio
import aiohttp
import random
import time

# 配置參數
BASE_URL = 'http://localhost:8081'
SYMBOLS = {
    'ETHUSDT': {'initial_price': 2000, 'price_range': 20},
    'BTCUSDT': {'initial_price': 50000, 'price_range': 20}
}
ORDER_QUANTITY_RANGE = (0.1, 1.0)  # 訂單數量範圍
MARKET_ORDER_PROBABILITY = 0.05  # 市價單的概率
CONCURRENCY = 100  # 同時提交訂單的數量（併發數）

# JWT 令牌
JWT_TOKENS = [
    'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjEiLCJ1c2VySWQiOiIxODM0NjY2NjE1NTQ4MDIyNzg0IiwiaWF0IjoxNzI2NTg2NDg1LCJleHAiOjE3NjI1ODY0ODV9.wZfuSfV3scEecxAnjh4y7Fd0SUYVfEQPv4NjdcSfcLA',
    'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjIiLCJ1c2VySWQiOiIxODM0NjY2NTI5ODc1MTY5MjgwIiwiaWF0IjoxNzI2NTg2NTAwLCJleHAiOjE3NjI1ODY1MDB9.opp5lWV_54bZ1Dbfy68H7EYeQZ4MFLyh0d5Xwb812tY'
    ]

async def submit_order(session, side, symbol, initial_price, price_range):
    quantity = round(random.uniform(*ORDER_QUANTITY_RANGE), 2)
    is_market_order = random.random() < MARKET_ORDER_PROBABILITY

    payload = {
        'symbol': symbol,
        'quantity': quantity,
        'side': side,
        'orderType': 'MARKET' if is_market_order else 'LIMIT'
    }

    if not is_market_order:
        price = initial_price + random.uniform(-price_range, price_range)
        payload['price'] = round(price, 2)

    headers = {
        'Content-Type': 'application/json',
        'Authorization': f'Bearer {random.choice(JWT_TOKENS)}'
    }

    async with session.post(f'{BASE_URL}/orders/submit', json=payload, headers=headers) as response:
        if response.status == 200:
            order_type = "MARKET" if is_market_order else f"LIMIT @ {payload.get('price', 'N/A')}"
            print(f"Order submitted: {side} {quantity} {symbol} {order_type}")
        else:
            print(f"Failed to submit order: {await response.text()}")

async def market_maker(orders_per_second, run_duration, mixed_test=False):
    async with aiohttp.ClientSession() as session:
        tasks = []
        interval = 1 / orders_per_second  # 計算每次訂單之間的間隔時間
        end_time = time.time() + run_duration  # 計算結束時間
        while time.time() < end_time:
            if mixed_test:
                symbols = list(SYMBOLS.keys())
            else:
                symbols = ['BTCUSDT']
            
            for symbol in symbols:
                initial_price = SYMBOLS[symbol]['initial_price']
                price_range = SYMBOLS[symbol]['price_range']
                side = random.choice(['BUY', 'SELL'])
                task = asyncio.ensure_future(submit_order(session, side, symbol, initial_price, price_range))
                tasks.append(task)
                if len(tasks) >= CONCURRENCY:
                    await asyncio.gather(*tasks)
                    tasks = []
                await asyncio.sleep(interval)  # 控制訂單提交的速率
        if tasks:
            await asyncio.gather(*tasks)

if __name__ == '__main__':
    mixed_test = input("是否進行混合測試（y/n）: ").strip().lower() == 'y'
    orders_per_second = float(input("請輸入每秒訂單數量: "))  # 讓使用者輸入每秒訂單數量
    run_duration = float(input("請輸入運行時長（秒）: "))  # 讓使用者輸入希望運行的時間
    start_time = time.time()
    asyncio.run(market_maker(orders_per_second, run_duration, mixed_test))
    end_time = time.time()
    print(f"Total time: {end_time - start_time} seconds")