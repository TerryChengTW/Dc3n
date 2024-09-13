# Database Schema

This document outlines the schema for the database used in our trading system. It includes definitions for users, orders, trades, user balances, and market data.

```mysql
Table users {
    id varchar(20) [pk]                // Unique user ID, generated using Snowflake algorithm
    created_at datetime(6)             // User account creation time
    email varchar(100) [unique]        // User email, unique
    password_hash varchar(255)         // Hash of user password
    status varchar(10)                 // User account status, e.g., 'active' or 'inactive'
    updated_at datetime(6)             // Last update time of user account
    username varchar(50) [unique]      // Username, unique
}

Table orders {
    id varchar(20) [pk]                // Unique order ID, generated using Snowflake algorithm
    user_id varchar(20)                // Foreign key, references users.id
    symbol varchar(10)                 // Trading pair, e.g., 'BTC/USDT'
    price decimal(18, 8)               // Order price
    quantity decimal(18, 8)            // Order quantity
    order_type enum('LIMIT', 'MARKET', 'TAKE_PROFIT', 'STOP_LOSS') // Order type
    status enum('PENDING', 'COMPLETED', 'CANCELLED') // Order status
    stop_price decimal(18, 8)          // Stop loss price (only used for STOP_LOSS type)
    take_profit_price decimal(18, 8)   // Take profit price (only used for TAKE_PROFIT type)
    created_at datetime(6)             // Order creation time
    updated_at datetime(6)             // Last update time of order

    // Indexes
    Index idx_user_id(user_id)         // User ID index for querying orders of a specific user
    Index idx_symbol(symbol)           // Trading pair index for querying orders of a specific trading pair
}

Table trades {
    id varchar(20) [pk]                // Unique trade ID, generated using Snowflake algorithm
    buy_order_id varchar(20)           // Foreign key, references orders.id, buying order
    sell_order_id varchar(20)          // Foreign key, references orders.id, selling order
    price decimal(18, 8)               // Trade price
    quantity decimal(18, 8)            // Trade quantity
    trade_time datetime(6)             // Trade time

    // Indexes
    Index idx_buy_order_id(buy_order_id)  // Buying order ID index
    Index idx_sell_order_id(sell_order_id) // Selling order ID index
    Index idx_trade_time(trade_time)      // Trade time index
}

Table user_balances {
    currency varchar(10)               // Currency type, e.g., 'BTC', 'USDT'
    user_id varchar(20)                // Foreign key, references users.id
    available_balance decimal(18, 8)  // Available balance
    frozen_balance decimal(18, 8)     // Frozen balance
    primary key (currency, user_id)   // Primary key, composed of currency type and user ID
}

Table market_data {
    symbol varchar(10)                 // Trading pair, e.g., 'BTC/USDT'
    time_frame varchar(5)              // Time frame, e.g., '1m', '5m', '1d'
    timestamp datetime(6)              // Data timestamp
    close decimal(18, 8)               // Closing price
    high decimal(18, 8)                // Highest price
    low decimal(18, 8)                 // Lowest price
    open decimal(18, 8)                // Opening price
    volume decimal(18, 8)              // Trading volume
    primary key (symbol, time_frame, timestamp) // Primary key, composed of trading pair, time frame, and timestamp
}

// Foreign key relationships
Ref: orders.user_id > users.id         // Order's user ID references user ID
Ref: trades.buy_order_id > orders.id   // Trade's buying order ID references order ID
Ref: trades.sell_order_id > orders.id  // Trade's selling order ID references order ID
Ref: user_balances.user_id > users.id  // User balance's user ID references user ID
