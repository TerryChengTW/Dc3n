let socket;
let selectedSide = '';
let selectedOrderType = '';

function connectWebSocket() {
    const token = localStorage.getItem('jwtToken');
    socket = new WebSocket(`ws://localhost:8081/ws?token=${token}`);

    socket.onopen = function() {
        console.log("WebSocket 連接已建立");
    };

    socket.onmessage = function(event) {
        const notification = JSON.parse(event.data);
        handleOrderNotification(notification);
    };

    socket.onclose = function(event) {
        if (event.wasClean) {
            console.log(`WebSocket 連接已關閉，代碼=${event.code} 原因=${event.reason}`);
        } else {
            console.log('WebSocket 連接已中斷');
        }
    };

    socket.onerror = function(error) {
        console.log(`WebSocket 錯誤: ${error.message}`);
    };
}

function handleOrderNotification(notification) {
    const order = notification.data;

    switch (notification.eventType) {
        case 'ORDER_CREATED':
        case 'ORDER_UPDATED':
            addOrUpdateOrderRow(order);
            break;
        case 'ORDER_DELETED':
            removeOrderRow(order.id);
            break;
    }
}

function addOrUpdateOrderRow(order) {
    let orderRow = document.getElementById(`order-${order.id}`);

    if (!orderRow) {
        orderRow = document.createElement('tr');
        orderRow.id = `order-${order.id}`;
        // 根據訂單的side動態添加買單或賣單的類別
        if (order.side === 'BUY') {
            orderRow.classList.add('bid-row');
        } else if (order.side === 'SELL') {
            orderRow.classList.add('ask-row');
        }
        document.querySelector('#orderTable tbody').appendChild(orderRow);
    }

    orderRow.innerHTML = `
        <td>${order.id}</td>
        <td>${order.userId}</td>
        <td>${order.symbol}</td>
        <td>${order.price}</td>
        <td>${order.quantity}</td>
        <td>${order.filledQuantity}</td>
        <td>${order.side}</td>
        <td>${order.orderType}</td>
        <td>${order.status}</td>
    `;

    if (order.status === 'COMPLETED') {
        setTimeout(() => removeOrderRow(order.id), 5000);
    }
}

function removeOrderRow(orderId) {
    const orderRow = document.getElementById(`order-${orderId}`);
    if (orderRow) {
        orderRow.remove();
    }
}

function selectSide(side) {
    selectedSide = side;

    document.getElementById('buyButton').classList.remove('active');
    document.getElementById('sellButton').classList.remove('active');
    if (side === 'BUY') {
        document.getElementById('buyButton').classList.add('active');
    } else {
        document.getElementById('sellButton').classList.add('active');
    }
}

function selectOrderType(orderType) {
    selectedOrderType = orderType;

    document.getElementById('limitButton').classList.remove('active');
    document.getElementById('marketButton').classList.remove('active');
    if (orderType === 'LIMIT') {
        document.getElementById('limitButton').classList.add('active');
    } else {
        document.getElementById('marketButton').classList.add('active');
    }
}

function submitOrder() {
    const data = {
        symbol: document.getElementById("symbol").value,
        price: parseFloat(document.getElementById("price").value),
        quantity: parseFloat(document.getElementById("quantity").value),
        side: selectedSide,
        orderType: selectedOrderType
    };

    if (!data.side || !data.orderType) {
        showErrorPopup("請選擇方向和訂單類型");
        return;
    }

    const token = localStorage.getItem('jwtToken');
    fetch('/orders/submit', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + token
        },
        body: JSON.stringify(data),
    })
        .then(response => response.json().then(data => {
            if (response.ok) {
                alert('下單成功: ' + data.message);
            } else {
                throw new Error(data.error || '下單失敗');
            }
        }))
        .catch((error) => {
            showErrorPopup('下單失敗: ' + error.message);
        });
}

function showErrorPopup(message) {
    const errorPopup = document.getElementById('errorPopup');
    const errorPopupMessage = document.getElementById('errorPopupMessage');

    if (errorPopupMessage) {
        errorPopupMessage.textContent = message;
        errorPopup.style.display = 'block';

        setTimeout(() => {
            errorPopup.style.display = 'none';
        }, 3000);
    }
}

// 連接訂單簿 WebSocket
let orderbookSocket;
let currentSymbol;

function connectOrderbookWebSocket(symbol) {
    if (orderbookSocket) {
        orderbookSocket.close();
    }

    const token = localStorage.getItem('jwtToken');
    currentSymbol = symbol;
    orderbookSocket = new WebSocket(`ws://localhost:8081/ws/orderbook?token=${token}&symbol=${symbol}`);

    orderbookSocket.onopen = function() {
        console.log(`訂單簿 WebSocket 連接已建立，交易對：${symbol}`);
    };

    orderbookSocket.onmessage = function(event) {
        const orderbookUpdate = JSON.parse(event.data);
        updateOrderbookDisplay(orderbookUpdate);
    };

    orderbookSocket.onclose = function() {
        console.log(`訂單簿 WebSocket 連接已關閉，交易對：${symbol}`);
    };

    orderbookSocket.onerror = function(error) {
        console.log(`訂單簿 WebSocket 錯誤: ${error.message}`);
    };
}

function updateSymbol() {
    const symbol = document.getElementById('symbol').value;
    if (symbol !== currentSymbol) {
        connectOrderbookWebSocket(symbol);
    }
}

function groupOrdersByPriceRange(orders, rangeSize) {
    const groupedOrders = {};

    orders.forEach(([price, quantity]) => {
        const roundedPrice = Math.floor(price / rangeSize) * rangeSize;
        if (!groupedOrders[roundedPrice]) {
            groupedOrders[roundedPrice] = 0;
        }
        groupedOrders[roundedPrice] += quantity;
    });

    return Object.entries(groupedOrders).sort((a, b) => a[0] - b[0]);
}

function updateOrderbookDisplay(orderbookUpdate) {
    const { bids, asks } = orderbookUpdate;

    // 分組並顯示五檔買單 (綠色)
    const bidsList = document.getElementById('bidsList');
    bidsList.innerHTML = '';
    const groupedBids = groupOrdersByPriceRange(bids, 1); // 每1塊分組
    groupedBids.slice(-5).reverse().forEach(([price, totalQuantity]) => {
        const row = `<tr class="bid-row"><td>${price}</td><td>${totalQuantity}</td></tr>`;
        bidsList.innerHTML += row;
    });

    // 分組並顯示五檔賣單 (紅色)
    const asksList = document.getElementById('asksList');
    asksList.innerHTML = '';
    const groupedAsks = groupOrdersByPriceRange(asks, 1); // 每1塊分組
    groupedAsks.slice(-5).reverse().forEach(([price, totalQuantity]) => {
        const row = `<tr class="ask-row"><td>${price}</td><td>${totalQuantity}</td></tr>`;
        asksList.innerHTML += row;
    });
}

// 在頁面加載時連接 WebSocket
window.onload = function() {
    connectWebSocket();
    const initialSymbol = document.getElementById('symbol').value;
    connectOrderbookWebSocket(initialSymbol);
};

// 當交易對變更時重新連接訂單簿 WebSocket
document.getElementById('symbol').addEventListener('change', updateSymbol);
