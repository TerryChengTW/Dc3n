import asyncio
import aiohttp
import random
import time

# 配置參數
BASE_URL = 'http://localhost:8081'
SYMBOL = 'BTCUSDT'
INITIAL_PRICE = 50000
PRICE_RANGE = 5  # 價格波動範圍
ORDER_QUANTITY_RANGE = (0.1, 1.0)  # 訂單數量範圍
MARKET_ORDER_PROBABILITY = 0.05  # 市價單的概率
CONCURRENCY = 10  # 同時提交訂單的數量（併發數）

# JWT 令牌
JWT_TOKENS = [
    'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjEiLCJ1c2VySWQiOiIxODM0NjY2NjE1NTQ4MDIyNzg0IiwiaWF0IjoxNzI2NDA1NDI3LCJleHAiOjE3MjY0NDE0Mjd9.JaiBDlvckAEwLVgqhPFdAOSYLAcCqUk2N9jfggur53w',
    'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjQiLCJ1c2VySWQiOiIxODM1MjI2ODQ4NTQ0NTU5MTA0IiwiaWF0IjoxNzI2NDExMTg3LCJleHAiOjE3MjY0NDcxODd9.1_geIOCCM8fnrTB4LVJ-C_mqRKyn5rvMVqKNtTcQq3w'
]

async def submit_order(session, side):
    quantity = round(random.uniform(*ORDER_QUANTITY_RANGE), 2)
    is_market_order = random.random() < MARKET_ORDER_PROBABILITY

    payload = {
        'symbol': SYMBOL,
        'quantity': quantity,
        'side': side,
        'orderType': 'MARKET' if is_market_order else 'LIMIT'
    }

    if not is_market_order:
        price = INITIAL_PRICE + random.uniform(-PRICE_RANGE, PRICE_RANGE)
        payload['price'] = round(price, 2)

    headers = {
        'Content-Type': 'application/json',
        'Authorization': f'Bearer {random.choice(JWT_TOKENS)}'
    }

    async with session.post(f'{BASE_URL}/orders/submit', json=payload, headers=headers) as response:
        if response.status == 200:
            order_type = "MARKET" if is_market_order else f"LIMIT @ {payload.get('price', 'N/A')}"
            print(f"Order submitted: {side} {quantity} {SYMBOL} {order_type}")
        else:
            print(f"Failed to submit order: {await response.text()}")

async def market_maker(orders_per_second, run_duration):
    async with aiohttp.ClientSession() as session:
        tasks = []
        interval = 1 / orders_per_second  # 計算每次訂單之間的間隔時間
        end_time = time.time() + run_duration  # 計算結束時間
        while time.time() < end_time:
            side = random.choice(['BUY', 'SELL'])
            task = asyncio.ensure_future(submit_order(session, side))
            tasks.append(task)
            if len(tasks) >= CONCURRENCY:
                await asyncio.gather(*tasks)
                tasks = []
            await asyncio.sleep(interval)  # 控制訂單提交的速率
        if tasks:
            await asyncio.gather(*tasks)

if __name__ == '__main__':
    orders_per_second = float(input("請輸入每秒訂單數量: "))  # 讓使用者輸入每秒訂單數量
    run_duration = float(input("請輸入運行時長（秒）: "))  # 讓使用者輸入希望運行的時間
    start_time = time.time()
    asyncio.run(market_maker(orders_per_second, run_duration))
    end_time = time.time()
    print(f"Total time: {end_time - start_time} seconds")
