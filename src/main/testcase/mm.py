import asyncio
import aiohttp
import random
import time
import numpy as np

# 配置參數
BASE_URL = 'https://terry987.xyz'
SYMBOLS = {
    'ETHUSDT': {'initial_price': 3000, 'price_range': (1000, 5000), 'stddev': 50, 'volatility': 0.01},
    'BTCUSDT': {'initial_price': 50000, 'price_range': (30000, 70000), 'stddev': 500, 'volatility': 0.01}
}
ORDER_QUANTITY_RANGE = (0.1, 1.0)  # 訂單數量範圍
MARKET_ORDER_PROBABILITY = 0.00  # 市價單的概率
CONCURRENCY = 100  # 控制同時提交訂單的數量

# JWT 令牌
JWT_TOKENS = [
    'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjEiLCJ1c2VySWQiOiIxODM4ODU2Njg4NjgyMjc0ODE2IiwiaWF0IjoxNzI4MzMzNjk4LCJleHAiOjE3NjQzMzM2OTh9.yVWk0K-uAc8p3Wr9bzLXSEQ5idSxKOsVGXUWOJgGHAM',
    'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjQiLCJ1c2VySWQiOiIxODM4MDI5MDQzMjQ5ODQ0MjI0IiwiaWF0IjoxNzI3MDU1MzE5LCJleHAiOjE3NjMwNTUzMTl9.jqyeE6C1N6XDRsYU4uOlvuQG4H46EDBwPzbcQ5ip3Js'
]

# 使用正弦波模型控制價格在一個方向內逐漸波動
def generate_price(current_price, price_range, time_step, max_percent_change):
    change_percent = max_percent_change * np.sin(time_step)
    new_price = current_price * (1 + change_percent)
    return max(price_range[0], min(price_range[1], new_price))

# 用於計算成功提交的訂單數量
success_count = 0
stop_flag = False

async def submit_order(session, side, symbol, current_price, price_range, time_step, max_percent_change):
    global success_count
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
            success_count += 1

async def producer(queue, orders_per_second, run_duration, mixed_test=False):
    prices = {symbol: config['initial_price'] for symbol, config in SYMBOLS.items()}
    time_step = 0
    end_time = time.time() + run_duration

    while not stop_flag and time.time() < end_time:
        if mixed_test:
            symbols = list(SYMBOLS.keys())
        else:
            symbols = ['BTCUSDT']

        for symbol in symbols:
            config = SYMBOLS[symbol]
            current_price = prices[symbol]
            side = random.choice(['BUY', 'SELL'])
            
            await queue.put((side, symbol, current_price, config['price_range'], time_step, config['volatility']))
            
            time_step += 0.1
            prices[symbol] = generate_price(current_price, config['price_range'], time_step, config['volatility'])
        
        await asyncio.sleep(1 / orders_per_second)

async def consumer(queue, session):
    while not stop_flag:
        task = await queue.get()
        side, symbol, current_price, price_range, time_step, max_percent_change = task
        
        await submit_order(session, side, symbol, current_price, price_range, time_step, max_percent_change)
        queue.task_done()

async def market_maker(orders_per_second, run_duration, mixed_test=False):
    queue = asyncio.Queue()
    
    async with aiohttp.ClientSession() as session:
        producer_task = asyncio.create_task(producer(queue, orders_per_second, run_duration, mixed_test))
        consumers = [asyncio.create_task(consumer(queue, session)) for _ in range(CONCURRENCY)]
        
        await producer_task
        await queue.join()
        for c in consumers:
            c.cancel()

async def stop_checker():
    global stop_flag
    print("Press 's' and Enter to stop...")
    while not stop_flag:
        input_text = await asyncio.to_thread(input)
        if input_text.strip().lower() == 's':
            stop_flag = True

async def main():
    mixed_test = input("是否進行混合測試（y/n）: ").strip().lower() == 'y'
    orders_per_second = float(input("請輸入每秒訂單數量: "))  # 讓使用者輸入每秒訂單數量
    run_duration = float(input("請輸入運行時長（秒）: "))  # 讓使用者輸入希望運行的時間
    
    start_time = time.time()
    
    # 開始執行 market_maker 和 stop_checker
    market_maker_task = asyncio.create_task(market_maker(orders_per_second, run_duration, mixed_test))
    stop_task = asyncio.create_task(stop_checker())
    
    # 同時等待這兩個任務，當stop_task完成（按下 's' 時），market_maker 也會停止
    await asyncio.wait([market_maker_task, stop_task], return_when=asyncio.FIRST_COMPLETED)
    
    # 結束時間
    end_time = time.time()
    print(f"Total time: {end_time - start_time} seconds")
    print(f"Total successful orders submitted: {success_count}")

if __name__ == '__main__':
    asyncio.run(main())
