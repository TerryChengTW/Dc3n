# dc3n - Cryptocurrency Exchange Platform

**dc3n** is a professional cryptocurrency exchange platform designed to handle high-frequency trading. The platform utilizes a **single-order matching** mechanism, ensuring that each incoming order is matched with a single counterpart order, rather than being processed in bulk. This approach guarantees precise and real-time execution of trades. Additionally, **price-time priority** is enforced to ensure fairness in order matching, meaning orders are processed based on the best price first, and in cases of equal prices, the earliest submitted order is prioritized.

## Key Features

- **Single-Order Matching Engine**: dc3n uses a single-order matching mechanism, ensuring that each order is matched with only one counter-order at a time. This provides efficient and immediate order execution without batch processing.
- **Price-Time Priority**: The platform ensures fairness by following the price-time priority rule. Orders are matched based on the best price, and if multiple orders have the same price, the system prioritizes the one that was submitted first.
- **Limit and Market Order Matching**: Users can submit limit and market orders, and the system will automatically match them based on market conditions.
- **Real-Time Order Book**: The system updates the order book in real time, displaying the market depth with buy and sell prices and volumes.
- **Live Market Data**: Real-time market data, including order statuses and executed trades, are pushed via WebSocket, keeping users up to date with the latest market movements.
- **Historical Trade Records**: Users can view all completed trades and access detailed transaction histories.
- **High Concurrency Support**: The platform is built on a distributed architecture that can handle high volumes of concurrent orders, ensuring stability under heavy traffic.

## Technical Stack

- **Java/Spring Boot**: The core application framework, providing REST APIs and Kafka message handling.
- **Kafka**: Manages high-concurrency order messages, ensuring reliable processing in the order they were received.
- **Redis**: Utilizes Redis's ZSet structure for managing the order book, allowing O(log N) insertion and matching of limit orders.
- **MySQL**: Persists trade data, storing all orders and transaction records for consistency and durability.
- **WebSocket**: Pushes real-time market and order updates, allowing users to track order progress instantaneously.
- **AWS EC2 & ALB**: Ensures high availability and auto-scaling capabilities through AWS EC2 and Application Load Balancer (ALB).

## Test the Platform

You can log in and test the platform via the following link:

[dc3n.xyz](https://dc3n.xyz)

### Test Account:

- **Email**: terry@gmail.com
- **Password**: terry

After logging in, you can:

- Submit buy and sell orders (limit and market)
- View real-time order book and market depth
- Receive live updates on order statuses and trades
- Access historical trade records

## Contact

For any questions or suggestions, please contact the developer at:

- Email: terrydev.tw@gmail.com