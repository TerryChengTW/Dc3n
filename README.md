# Dc3n - Cryptocurrency Exchange Platform (Main Service)

**Dc3n** is a professional cryptocurrency exchange platform designed to handle high-frequency trading. This repository represents the **main service**, which includes order submission, user interaction, and real-time updates. It is integrated with a separate **order matching engine**, ensuring accurate and efficient trade execution.

The **order matching engine** used by Dc3n is hosted at:  
[Dc3n Order Matching Engine Repository](https://github.com/TerryChengTW/dc3n-order-matching-engine)

## Key Features

- **Single-Order Matching Engine**: The system integrates with a single-order matching engine, ensuring that each order is matched with only one counter-order at a time. This provides efficient and immediate order execution without batch processing.
- **Price-Time Priority**: The platform ensures fairness by enforcing the price-time priority rule. Orders are matched based on the best price, and if multiple orders have the same price, the system prioritizes the earliest submitted order.
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

[Dc3n.xyz](https://dc3n.xyz)

### Test Account:

- **Username**: terry
- **Password**: terry

After logging in, you can:

- Submit buy and sell orders (limit and market)
- View real-time order book and market depth
- Receive live updates on order statuses and trades
- Access historical trade records

## Order Matching Engine

The core of Dc3n’s trading functionality is powered by a separate order matching engine, which you can find here:  
[Dc3n Order Matching Engine Repository](https://github.com/TerryChengTW/Dc3n-order-matching-engine)

This engine handles all trade matching processes based on price-time priority, ensuring fair and accurate trade execution.

## Contact

For any questions or suggestions, please contact the developer at:

- Email: terrydev.tw@gmail.com