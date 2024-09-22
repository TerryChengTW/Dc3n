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
    'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjEiLCJ1c2VySWQiOiIxODM0NjY2NjE1NTQ4MDIyNzg0IiwiaWF0IjoxNzI2NjIyMTk5LCJleHAiOjE3NjI2MjIxOTl9.Bsn-OfJVXNIQyPI_oY6lrIQgLWANTXjoOf0fnTX9sVs',
    'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjIiLCJ1c2VySWQiOiIxODM0NjY2NTI5ODc1MTY5MjgwIiwiaWF0IjoxNzI2NjIyMjM5LCJleHAiOjE3NjI2MjIyMzl9.zR8dW2dUWWBTERwdJDD0yGwzFyUWliXwWTRGvBuStd0'
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
            print(f"Order submitted: {side} {quantity} {SYMBOL} LIMIT @ {price}")
        else:
            print(f"Failed to submit order: {await response.text()}")

async def sequential_order_placement():
    async with aiohttp.ClientSession() as session:
        # 先下買單
        price = BUY_INITIAL_PRICE
        while price <= BUY_MAX_PRICE:
            await submit_order(session, 'BUY', price)
            await asyncio.sleep(0.01)  # 可以調整間隔以控制下單速度
            price += 1  # 每次價格增加 1

        # 再下賣單
        price = SELL_INITIAL_PRICE
        while price >= SELL_MIN_PRICE:
            if price <= 50500:
                await submit_order(session, 'SELL', price, quantity=2.0)  # 當價格 <= 50500，賣 2 顆
            else:
                await submit_order(session, 'SELL', price)  # 否則賣 1 顆
            await asyncio.sleep(0.01)  # 可以調整間隔以控制下單速度
            price -= 1  # 每次價格減少 1


if __name__ == '__main__':
    start_time = time.time()
    asyncio.run(sequential_order_placement())
    end_time = time.time()

    print(f"Total orders submitted: {order_count}")
    print(f"Total time: {end_time - start_time} seconds")
