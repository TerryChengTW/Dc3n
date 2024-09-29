import asyncio
import aiohttp
import time

# 配置參數
BASE_URL = 'http://localhost:8081'
SYMBOL = 'BTCUSDT'
BUY_PRICE = 50000
SELL_PRICE = 50000
BUY_ORDER_QUANTITY = 1.0  # 每次買單數量為 1 BTC
SELL_ORDER_QUANTITY = 0.01  # 每次賣單數量為 0.01 BTC
JWT_TOKENS = [
    'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjMiLCJ1c2VySWQiOiIxODM4MDI5MDA0NjE2MTEwMDgwIiwiaWF0IjoxNzI3MDU1NjA5LCJleHAiOjE3NjMwNTU2MDl9.DU31c_NFobpFS8VfjlMCaV5kSVgBvPst6K7DcaanMWc',
    'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjQiLCJ1c2VySWQiOiIxODM4MDI5MDQzMjQ5ODQ0MjI0IiwiaWF0IjoxNzI3MDU1MzE5LCJleHAiOjE3NjMwNTUzMTl9.jqyeE6C1N6XDRsYU4uOlvuQG4H46EDBwPzbcQ5ip3Js'
]

# 記錄訂單計數
order_count = 0

async def submit_order(session, side, price, quantity):
    global order_count
    payload = {
        'symbol': SYMBOL,
        'quantity': quantity,  # 設置可變數量
        'side': side,
        'orderType': 'LIMIT',
        'price': price
    }

    headers = {
        'Content-Type': 'application/json',
        'Authorization': f'Bearer {JWT_TOKENS[0]}'
    }

    async with session.post(f'{BASE_URL}/orders/submit', json=payload, headers=headers) as response:
        if response.status == 200:
            order_count += 1
        else:
            print(f"Failed to submit order: {await response.text()}")

async def sequential_order_placement():
    connector = aiohttp.TCPConnector(limit=1000, limit_per_host=1000)
    async with aiohttp.ClientSession(connector=connector) as session:
        # 先下 10 次價格 50000 元 1 BTC 的買單
        for _ in range(10):
            await submit_order(session, 'BUY', BUY_PRICE, BUY_ORDER_QUANTITY)

        # 併發地下 1000 個 0.01 BTC 的賣單
        sell_tasks = []
        for _ in range(1000):
            task = submit_order(session, 'SELL', SELL_PRICE, SELL_ORDER_QUANTITY)
            sell_tasks.append(task)
        
        # 等待所有賣單完成
        await asyncio.gather(*sell_tasks)

if __name__ == '__main__':
    start_time = time.time()
    asyncio.run(sequential_order_placement())
    end_time = time.time()

    print(f"Total orders submitted: {order_count}")
    print(f"Total time: {end_time - start_time} seconds")
