import asyncio
import aiohttp
import time

# 配置參數
BASE_URL = 'http://localhost:8081'
SYMBOL = 'BTCUSDT'
BUY_INITIAL_PRICE = 50000
BUY_MAX_PRICE = 51000
SELL_INITIAL_PRICE = 51000
SELL_MIN_PRICE = 50000
ORDER_QUANTITY = 1.0  # 每次下單數量固定為 1 BTC
JWT_TOKENS = [
    'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjMiLCJ1c2VySWQiOiIxODM4MDI5MDA0NjE2MTEwMDgwIiwiaWF0IjoxNzI3MDU1NjA5LCJleHAiOjE3NjMwNTU2MDl9.DU31c_NFobpFS8VfjlMCaV5kSVgBvPst6K7DcaanMWc',
    'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjQiLCJ1c2VySWQiOiIxODM4MDI5MDQzMjQ5ODQ0MjI0IiwiaWF0IjoxNzI3MDU1MzE5LCJleHAiOjE3NjMwNTUzMTl9.jqyeE6C1N6XDRsYU4uOlvuQG4H46EDBwPzbcQ5ip3Js'
]

# 記錄訂單計數
order_count = 0

async def submit_order(session, side, price, quantity=ORDER_QUANTITY):
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
            #print(f"Order submitted: {side} {quantity} {SYMBOL} LIMIT @ {price}")
        else:
            print(f"Failed to submit order: {await response.text()}")

async def sequential_order_placement():
    connector = aiohttp.TCPConnector(limit=500, limit_per_host=500)
    async with aiohttp.ClientSession(connector=connector) as session:
        # 先下買單
        buy_tasks = []
        price = BUY_INITIAL_PRICE
        while price <= BUY_MAX_PRICE:
            task = submit_order(session, 'BUY', price)
            buy_tasks.append(task)
            price += 1

        # 按順序等待買單完成
        for task in buy_tasks:
            await task

        # 再下賣單
        sell_tasks = []
        price = SELL_INITIAL_PRICE
        while price >= SELL_MIN_PRICE:
            if price <= 50500:
                task = submit_order(session, 'SELL', price, quantity=2.0)
            else:
                task = submit_order(session, 'SELL', price)
            sell_tasks.append(task)
            price -= 1

        # 按順序等待賣單完成
        for task in sell_tasks:
            await task

if __name__ == '__main__':
    start_time = time.time()
    asyncio.run(sequential_order_placement())
    end_time = time.time()

    print(f"Total orders submitted: {order_count}")
    print(f"Total time: {end_time - start_time} seconds")
