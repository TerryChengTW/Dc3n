import asyncio
import aiohttp
import time

# 配置參數
BASE_URL = 'http://localhost:8081'
SYMBOL = 'BTCUSDT'
PRICE_MIN = 30000
PRICE_MAX = 70000
ORDER_QUANTITY = 1.0  # 每次下單數量固定為 1 BTC
JWT_TOKENS = [
    'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjEiLCJ1c2VySWQiOiIxODM2NjcwNDY1MTI5NzEzNjY0IiwiaWF0IjoxNzI3MDcxMzIwLCJleHAiOjE3NjMwNzEzMjB9.vM9mfo6luhs2txg72o9hZGdUimojF5HcJy3hacZkBQ8',
    'eyJhbGciOiJIUzI1NiJ9.eyJ1c2VybmFtZSI6IjIiLCJ1c2VySWQiOiIxODM2NjcwNDkzNDc5MDE0NDAwIiwiaWF0IjoxNzI3MDcxMzc5LCJleHAiOjE3NjMwNzEzNzl9.u7dlVDnTFP0hSBARwXQM7X5w6Erd1E7wZqTlh128ofA'
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
        'Authorization': f'Bearer {JWT_TOKENS[order_count % len(JWT_TOKENS)]}'  # 使用不同的 JWT token
    }

    async with session.post(f'{BASE_URL}/orders/submit', json=payload, headers=headers) as response:
        if response.status == 200:
            order_count += 1
            print(f"Order submitted: {side} {quantity} {SYMBOL} LIMIT @ {price}")
        else:
            print(f"Failed to submit order: {await response.text()}")

async def alternating_buy_sell():
    async with aiohttp.ClientSession() as session:
        ascending = True
        price = PRICE_MIN

        while True:
            # 下買單
            await submit_order(session, 'BUY', price)
            await asyncio.sleep(0.1)  # 0.5 秒間隔

            # 下賣單
            await submit_order(session, 'SELL', price)
            await asyncio.sleep(0.1)  # 0.5 秒間隔

            # 更新價格
            if ascending:
                price += 10
                if price > PRICE_MAX:
                    ascending = False
            else:
                price -= 10
                if price < PRICE_MIN:
                    ascending = True

if __name__ == '__main__':
    start_time = time.time()
    asyncio.run(alternating_buy_sell())
    end_time = time.time()

    print(f"Total orders submitted: {order_count}")
    print(f"Total time: {end_time - start_time} seconds")