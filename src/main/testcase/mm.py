import asyncio
import aiohttp
import random
import time
import numpy as np

# 配置參數
BASE_URL = 'https://terry987.xyz'
SYMBOLS = {
    'ETHUSDT': {'initial_price': 3000, 'price_range': (1000, 5000), 'stddev': 50, 'volatility': 0.01},
    'BTCUSDT': {'initial_price': 50000, 'price_range': (30000, 70000), 'stddev': 500, 'volatility': 0.02}
}
ORDER_QUANTITY_RANGE = (0.1, 1.0)  # 訂單數量範圍
MARKET_ORDER_PROBABILITY = 0.05  # 市價單的概率
CONCURRENCY = 100  # 同時提交訂單的數量（併發數）

# JWT 令牌
JWT_TOKENS = [
    'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjEiLCJ1c2VySWQiOiIxODM0NjY2NjE1NTQ4MDIyNzg0IiwiaWF0IjoxNzI2NjIyMTk5LCJleHAiOjE3NjI2MjIxOTl9.Bsn-OfJVXNIQyPI_oY6lrIQgLWANTXjoOf0fnTX9sVs',
    'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjEiLCJ1c2VySWQiOiIxODM3NzkxMDUwNTQ3MjA0MDk2IiwiaWF0IjoxNzI3MDAwNDUyLCJleHAiOjE3NjMwMDA0NTJ9.-aG6aKwea99JrEKxlqWgaTr4yN5vcuneJ221VHhaYEM'
]

# 使用正弦波模型控制價格在一個方向內逐漸波動
def generate_price(current_price, price_range, time_step, max_percent_change):
    # 每個時間點的價格變化是根據正弦波變化，來模擬緩慢波動
    change_percent = max_percent_change * np.sin(time_step)
    new_price = current_price * (1 + change_percent)
    
    # 確保價格在設定的範圍內
    return max(price_range[0], min(price_range[1], new_price))

async def submit_order(session, side, symbol, current_price, price_range, time_step, max_percent_change):
    quantity = round(random.uniform(*ORDER_QUANTITY_RANGE), 2)
    is_market_order = random.random() < MARKET_ORDER_PROBABILITY

    payload = {
        'symbol': symbol,
        'quantity': quantity,
        'side': side,
        'orderType': 'MARKET' if is_market_order else 'LIMIT'
    }

    if not is_market_order:
        price = generate_price(current_price, price_range, time_step, max_percent_change)
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
        interval = 1 / orders_per_second
        end_time = time.time() + run_duration
        prices = {symbol: config['initial_price'] for symbol, config in SYMBOLS.items()}
        time_step = 0  # 用來控制價格波動的時間步長
        
        while time.time() < end_time:
            if mixed_test:
                symbols = list(SYMBOLS.keys())
            else:
                symbols = ['BTCUSDT']
            
            for symbol in symbols:
                config = SYMBOLS[symbol]
                current_price = prices[symbol]
                side = random.choice(['BUY', 'SELL'])
                task = asyncio.ensure_future(
                    submit_order(session, side, symbol, current_price, config['price_range'], time_step, config['volatility'])
                )
                # 更新價格使用逐步的時間步長控制
                time_step += 0.1  # 每次遞增時間步長
                prices[symbol] = generate_price(current_price, config['price_range'], time_step, config['volatility'])
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
