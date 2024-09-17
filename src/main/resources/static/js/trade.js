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
        console.log(notification);
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
        case 'ORDER_COMPLETED':
            removeOrderRow(order.id);
            break;
    }
}

function addOrUpdateOrderRow(order) {
    let orderRow = document.getElementById(`order-${order.id}`);

    if (!orderRow) {
        orderRow = document.createElement('tr');
        orderRow.id = `order-${order.id}`;
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
        <td>
            <button onclick="editOrder('${order.id}')">編輯</button>
            <button onclick="deleteOrder('${order.id}')">刪除</button>
        </td>
    `;

    if (order.status === 'COMPLETED') {
        setTimeout(() => removeOrderRow(order.id), 5000);
    }
}

let editingOrderId = null;

function editOrder(orderId) {
    editingOrderId = orderId;
    const orderRow = document.getElementById(`order-${orderId}`);
    const price = orderRow.children[3].textContent;
    const quantity = orderRow.children[4].textContent;

    // 顯示彈出視窗
    const modal = document.getElementById("editOrderModal");
    modal.style.display = "block";

    // 預填訂單的價格和數量
    document.getElementById("editPrice").value = price;
    document.getElementById("editQuantity").value = quantity;
}


function showConfirm(message) {
    return new Promise((resolve) => {
        const confirmBox = document.getElementById('confirmBox');
        confirmBox.style.display = 'block';

        document.getElementById('confirmYes').onclick = () => {
            confirmBox.style.display = 'none';
            resolve(true); // 使用者確認
        };

        document.getElementById('confirmNo').onclick = () => {
            confirmBox.style.display = 'none';
            resolve(false); // 使用者取消
        };
    });
}

async function deleteOrder(orderId) {
    const token = localStorage.getItem('jwtToken');
    const confirmed = await showConfirm("確定要刪除這筆訂單嗎？");

    if (confirmed) {
        fetch(`/orders/cancel/${orderId}`, {
            method: 'DELETE',
            headers: {
                'Authorization': 'Bearer ' + token
            }
        })
            .then(response => response.json().then(data => {
                if (response.ok) {
                    showSuccessPopup('訂單已取消');
                    removeOrderRow(orderId);
                } else {
                    throw new Error(data.error || '訂單刪除失敗');
                }
            }))
            .catch((error) => {
                showErrorPopup('刪除失敗: ' + error.message);
            });
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
                showSuccessPopup('下單成功: ' + data.message);
            } else {
                throw new Error(data.error || '下單失敗');
            }
        }))
        .catch((error) => {
            showErrorPopup('下單失敗: ' + error.message);
        });
}

function showSuccessPopup(message) {
    const successPopup = document.getElementById('successPopup');
    const successPopupMessage = document.getElementById('successPopupMessage');

    if (successPopupMessage) {
        successPopupMessage.textContent = message;
        successPopup.style.display = 'block';

        setTimeout(() => {
            successPopup.style.display = 'none';
        }, 3000);
    }
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
        console.log(orderbookUpdate);
        updateOrderbookDisplay(orderbookUpdate);
    };

    orderbookSocket.onclose = function() {
        console.log(`訂單簿 WebSocket 連接已關閉，交易對：${symbol}`);
    };

    orderbookSocket.onerror = function(error) {
        console.log(`訂單簿 WebSocket 錯誤: ${error.message}`);
    };
}

let recentTradesSocket;

function connectRecentTradesWebSocket(symbol) {
    if (recentTradesSocket) {
        recentTradesSocket.close();
    }

    const token = localStorage.getItem('jwtToken');
    currentSymbol = symbol;
    recentTradesSocket = new WebSocket(`ws://localhost:8081/ws/recent-trades/${symbol}?token=${token}`);

    recentTradesSocket.onopen = function() {
        console.log(`最新成交 WebSocket 連接已建立，交易對：${symbol}`);
    };

    let isFirstMessage = true; // 標誌變數

    recentTradesSocket.onmessage = function(event) {
        const tradesList = document.getElementById('recentTradesList');
        const tradesData = JSON.parse(event.data);

        if (isFirstMessage) {
            // 清空默認的空白行
            tradesList.innerHTML = '';

            // 確認資料是否為陣列（多筆交易資料）
            if (Array.isArray(tradesData)) {
                // 處理所有的交易資料
                tradesData.forEach(trade => {
                    addRecentTrade(trade);
                });
            }
            isFirstMessage = false; // 第一次處理完畢
        } else {
            // 後續收到的是單筆交易資料
            if (Array.isArray(tradesData)) {
                // 如果錯誤發送了陣列，處理所有交易
                tradesData.forEach(trade => {
                    addRecentTrade(trade);
                });
            } else {
                // 處理單筆交易資料
                addRecentTrade(tradesData);
            }
        }
    };

    recentTradesSocket.onclose = function() {
        console.log(`最新成交 WebSocket 連接已關閉，交易對：${symbol}`);
    };

    recentTradesSocket.onerror = function(error) {
        console.log(`最新成交 WebSocket 錯誤: ${error.message}`);
    };
}

function addRecentTrade(trade) {
    const tradesList = document.getElementById('recentTradesList');
    const row = document.createElement('tr');

    row.innerHTML = `
        <td>${trade.price}</td>
        <td>${trade.quantity}</td>
        <td>${new Date(trade.tradeTime).toLocaleTimeString()}</td>
    `;
    tradesList.insertBefore(row, tradesList.firstChild);

    // 限制顯示的成交數量，例如只顯示最新的 5 筆
    if (tradesList.children.length > 3) {
        tradesList.removeChild(tradesList.lastChild);
    }
}



function updateSymbol() {
    const symbol = document.getElementById('symbol').value;
    if (symbol !== currentSymbol) {
        connectOrderbookWebSocket(symbol);
        connectRecentTradesWebSocket(symbol);
        currentSymbol = symbol;
    }
}

function groupOrdersByPriceRange(orders, rangeSize) {
    const groupedOrders = {};

    orders.forEach(([price, quantity]) => {
        const roundedPrice = Math.floor(price / rangeSize) * rangeSize;
        if (!groupedOrders[roundedPrice]) {
            groupedOrders[roundedPrice] = 0;
        }
        groupedOrders[roundedPrice] = parseFloat((groupedOrders[roundedPrice] + parseFloat(quantity)).toFixed(2));
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
        const row = `<tr class="bid-row"><td>${price}</td><td>${totalQuantity.toFixed(2)}</td></tr>`;
        bidsList.innerHTML += row;
    });

    // 分組並顯示五檔賣單 (紅色)
    const asksList = document.getElementById('asksList');
    asksList.innerHTML = '';
    const groupedAsks = groupOrdersByPriceRange(asks, 1); // 每1塊分組
    groupedAsks.slice(0, 5).reverse().forEach(([price, totalQuantity]) => { // 不再reverse
        const row = `<tr class="ask-row"><td>${price}</td><td>${totalQuantity.toFixed(2)}</td></tr>`;
        asksList.innerHTML += row;
    });
}

// 在頁面加載時連接 WebSocket
window.onload = function() {
    connectWebSocket();
    const initialSymbol = document.getElementById('symbol').value;
    connectOrderbookWebSocket(initialSymbol);
    connectRecentTradesWebSocket(initialSymbol);
};

// 當用戶點擊確認按鈕時提交修改訂單請求
document.getElementById("confirmEditButton").addEventListener("click", function () {
    const newPrice = document.getElementById("editPrice").value;
    const newQuantity = document.getElementById("editQuantity").value;

    if (newPrice && newQuantity && editingOrderId) {
        const token = localStorage.getItem('jwtToken');
        fetch(`/orders/modify/${editingOrderId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': 'Bearer ' + token
            },
            body: JSON.stringify({
                price: parseFloat(newPrice),
                quantity: parseFloat(newQuantity)
            }),
        })
            .then(response => response.json().then(data => {
                if (response.ok) {
                    showSuccessPopup('訂單修改成功');
                    // 更新前端表格
                    addOrUpdateOrderRow(data.data);
                    // 隱藏彈出視窗
                    document.getElementById("editOrderModal").style.display = "none";
                } else {
                    throw new Error(data.error || '訂單修改失敗');
                }
            }))
            .catch((error) => {
                showErrorPopup('修改失敗: ' + error.message);
            });
    }
});

// 當用戶點擊取消按鈕時關閉彈出視窗
document.getElementById("cancelEditButton").addEventListener("click", function () {
    document.getElementById("editOrderModal").style.display = "none";
});

// 點擊彈窗外部區域時隱藏彈窗
window.onclick = function(event) {
    const modal = document.getElementById("editOrderModal");
    if (event.target === modal) {
        modal.style.display = "none";
    }
};

// 點擊關閉按鈕隱藏彈窗
document.querySelector(".close").addEventListener("click", function() {
    document.getElementById("editOrderModal").style.display = "none";
});


// 當交易對變更時重新連接訂單簿 WebSocket
document.getElementById('symbol').addEventListener('change', updateSymbol);
