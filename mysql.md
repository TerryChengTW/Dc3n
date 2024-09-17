# Cryptocurrency Exchange Database Structure

## 1. market_data Table

<pre><code>create table market_data
(
    symbol     varchar(10)    not null,
    time_frame varchar(5)     not null,
    timestamp  datetime(6)    not null,
    close      decimal(18, 8) not null,
    high       decimal(18, 8) not null,
    low        decimal(18, 8) not null,
    open       decimal(18, 8) not null,
    volume     decimal(18, 8) not null,
    primary key (symbol, time_frame, timestamp)
);
</code></pre>

- **symbol**: The identifier for the trading pair (e.g., BTC/USDT).
- **time_frame**: The time frame for the data (e.g., '1m', '5m', '1d').
- **timestamp**: The timestamp for the data point.
- **open, high, low, close, volume**: Opening, highest, lowest, closing prices, and trading volume.

## 2. orders Table

<pre><code>create table orders
(
    id                varchar(20)                                                    not null primary key,
    created_at        datetime(6)                                                    not null,
    filled_quantity   decimal(18, 8)                                                 not null,
    order_type        enum ('LIMIT', 'MARKET', 'STOP_LOSS', 'TAKE_PROFIT')           not null,
    price             decimal(18, 8)                                                 null,
    quantity          decimal(18, 8)                                                 not null,
    side              enum ('BUY', 'SELL')                                           not null,
    status            enum ('CANCELLED', 'COMPLETED', 'PARTIALLY_FILLED', 'PENDING') not null,
    stop_price        decimal(18, 8)                                                 null,
    symbol            varchar(10)                                                    not null,
    take_profit_price decimal(18, 8)                                                 null,
    updated_at        datetime(6)                                                    not null,
    user_id           varchar(20)                                                    not null
);
</code></pre>

- **id**: The unique identifier for the order.
- **created_at, updated_at**: The timestamps for when the order was created and last updated.
- **filled_quantity**: The quantity of the order that has been filled.
- **order_type**: The type of order (e.g., limit order, market order).
- **price, quantity**: The price and quantity for the order.
- **side**: Indicates whether the order is a buy or sell.
- **status**: The current status of the order (e.g., cancelled, completed).
- **stop_price, take_profit_price**: The stop loss and take profit prices.
- **symbol**: The trading pair for the order.
- **user_id**: The unique identifier for the user placing the order.

## 3. trades Table

<pre><code>create table trades
(
    id            varchar(20)    not null primary key,
    price         decimal(18, 8) not null,
    quantity      decimal(18, 8) not null,
    trade_time    datetime(6)    not null,
    buy_order_id  varchar(20)    not null,
    sell_order_id varchar(20)    not null,
    constraint FKha2ij91oxit1wewyag8ol2ut6 foreign key (sell_order_id) references orders (id),
    constraint FKl71ijfuqfnp64ug4ehbwp0kqf foreign key (buy_order_id) references orders (id)
);
</code></pre>

- **id**: The unique identifier for the trade.
- **price, quantity**: The price and quantity for the trade.
- **trade_time**: The timestamp when the trade occurred.
- **buy_order_id, sell_order_id**: The IDs of the orders involved in the trade, with foreign key constraints.

## 4. user_balances Table

<pre><code>create table user_balances
(
    currency          varchar(10)    not null,
    user_id           varchar(255)   not null,
    available_balance decimal(18, 8) not null,
    frozen_balance    decimal(18, 8) not null,
    primary key (currency, user_id)
);
</code></pre>

- **currency**: The type of currency (e.g., BTC, USDT).
- **user_id**: The unique identifier for the user.
- **available_balance**: The available balance for the user.
- **frozen_balance**: The frozen balance that cannot be used.

## 5. users Table

<pre><code>create table users
(
    id            varchar(20)  not null primary key,
    created_at    datetime(6)  not null,
    email         varchar(100) not null,
    password_hash varchar(255) not null,
    status        varchar(10)  not null,
    updated_at    datetime(6)  not null,
    username      varchar(50)  not null,
    constraint UK6dotkott2kjsp8vw4d0m25fb7 unique (email),
    constraint UKr43af9ap4edm43mmtq01oddj6 unique (username)
);
</code></pre>

- **id**: The unique identifier for the user.
- **created_at, updated_at**: The timestamps for when the user was created and last updated.
- **email**: The user's email address, which must be unique.
- **password_hash**: The hashed password for the user.
- **status**: The current status of the user (e.g., active, inactive).
- **username**: The username for the user, which must be unique.

---

This document provides a detailed description of the database structure, helping to understand the purpose and relationships of each table and its fields.
