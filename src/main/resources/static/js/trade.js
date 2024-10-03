let userOrderSocket;
let selectedSide = '';
let selectedOrderType = '';

function connectUserOrderWebSocket() {
    const token = localStorage.getItem('jwtToken');

    userOrderSocket = new WebSocket(`/ws?token=${token}`);

    userOrderSocket.onopen = function() {
        console.log("UserOrderWebSocket 連接已建立");
    };

    userOrderSocket.onmessage = function(event) {
        const message = JSON.parse(event.data);
        console.log(message);
        if (message.type === 'snapshot') {
            handleOrderSnapshot(message.data);
        } else {
            handleOrderNotification(message);
        }
    };

    userOrderSocket.onclose = function(event) {
        if (event.wasClean) {
            console.log(`UserOrderWebSocket 連接已關閉，代碼=${event.code} 原因=${event.reason}`);
        } else {
            console.log('UserOrderWebSocket 連接已中斷');
        }
    };

    userOrderSocket.onerror = function(error) {
        console.log(`UserOrderWebSocket 錯誤: ${error.message}`);
    };
}

function handleOrderSnapshot(orders) {
    // 清空現有訂單表格
    const orderTableBody = document.querySelector('#orderTable tbody');
    orderTableBody.innerHTML = '';

    // 添加快照中的所有訂單
    orders.forEach(order => addOrUpdateOrderRow(order));
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

let recentTradesSocket;

function connectRecentTradesWebSocket(symbol) {
    if (recentTradesSocket) {
        recentTradesSocket.close();
        const tradesList = document.getElementById('recentTradesList');
        tradesList.innerHTML = '';
    }

    const token = localStorage.getItem('jwtToken');
    currentSymbol = symbol;
    recentTradesSocket = new WebSocket(`/ws/recent-trades/${symbol}?token=${token}`);

    recentTradesSocket.onopen = function() {
        console.log(`最新成交 WebSocket 連接已建立，交易對：${symbol}`);
    };

    let isFirstMessage = true; // 標誌變數

    recentTradesSocket.onmessage = function(event) {
        const tradesList = document.getElementById('recentTradesList');
        const tradesData = JSON.parse(event.data);
        console.log("最新成交：", tradesData);
        if (isFirstMessage) {
            // 確認資料是否為陣列且不為空
            if (Array.isArray(tradesData) && tradesData.length > 0) {
                // 清空默認的空白行
                tradesList.innerHTML = '';

                // 處理所有的交易資料
                tradesData.forEach(trade => {
                    addRecentTrade(trade);
                });
            } isFirstMessage = false;
        } else {
            // 後續收到的是單筆交易資料
            if (Array.isArray(tradesData)) {
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

    // 使用 24 小時制格式化時間
    const tradeTime = new Date(trade.tradeTime).toLocaleTimeString('zh-TW', { hour12: false });

    // 根據 `trade.direction` 來設定顏色
    let priceColor = trade.direction === 'buy' ? '#008000' : '#ff0000'; // `buy` 為綠色，`sell` 為紅色

    // 固定價格和數量的小數位為兩位
    const formattedPrice = parseFloat(trade.price).toFixed(2);
    const formattedQuantity = parseFloat(trade.quantity).toFixed(2);

    row.innerHTML = `
        <td style="color: ${priceColor};">${formattedPrice}</td>
        <td>${formattedQuantity}</td>
        <td>${tradeTime}</td>
    `;

    tradesList.insertBefore(row, tradesList.firstChild);

    // 限制顯示的成交數量，例如只顯示最新的 5 筆
    if (tradesList.children.length > 5) {
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


let orderbook = {
    buy: {},  // 儲存初始買單
    sell: {}  // 儲存初始賣單
};

let currentInterval = 1; // 默認為 1

// 連接訂單簿 WebSocket
let orderbookSocket;
let currentSymbol;

function connectOrderbookWebSocket(symbol) {
    // 獲取選擇的價格間隔
    currentInterval = parseFloat(document.getElementById('priceInterval').value);

    if (orderbookSocket) {
        orderbookSocket.close();
    }

    const token = localStorage.getItem('jwtToken');
    currentSymbol = symbol;
    // 將 interval 作為參數添加到 WebSocket URL 中
    orderbookSocket = new WebSocket(`/ws/orderbook?symbol=${symbol}&interval=${currentInterval}`);

    orderbookSocket.onopen = function() {
        console.log(`訂單簿 WebSocket 連接已建立，交易對：${symbol}，價格間隔：${currentInterval}`);
    };

    orderbookSocket.onmessage = function(event) {
        const orderbookUpdate = JSON.parse(event.data);

        // 根據消息內容區分快照或增量
        if (orderbookUpdate.buy && orderbookUpdate.sell) {
            // 快照更新
            orderbook.buy = aggregateOrderbook(orderbookUpdate.buy, currentInterval);
            orderbook.sell = aggregateOrderbook(orderbookUpdate.sell, currentInterval);
            console.log("訂單簿快照：", orderbook);
            updateOrderbookDisplay(orderbook);
        } else {
            console.log("訂單簿增量：", orderbookUpdate);
            // 增量更新
            updateOrderbookDelta(orderbookUpdate);
        }
    };

    orderbookSocket.onclose = function() {
        console.log(`訂單簿 WebSocket 連接已關閉，交易對：${symbol}`);
    };

    orderbookSocket.onerror = function(error) {
        console.log(`訂單簿 WebSocket 錯誤: ${error.message}`);
    };
}

function aggregateOrderbook(orderbookSide, interval, side) {
    const aggregated = {};

    for (const [priceStr, quantity] of Object.entries(orderbookSide)) {
        const price = parseFloat(priceStr);

        // 將價格調整到合適的精度來避免浮點數問題
        let aggregatedPrice = side === "BUY"
            ? Math.floor(price / interval + 1e-10) * interval // 加入微小數字避免浮點數誤差
            : Math.ceil(price / interval - 1e-10) * interval; // 減去微小數字避免浮點數誤差

        // 如果 interval 是 0.1，保留一位小數；否則保持整數
        aggregatedPrice = interval === 0.1 ? parseFloat(aggregatedPrice.toFixed(1)) : aggregatedPrice;

        if (!aggregated[aggregatedPrice]) {
            aggregated[aggregatedPrice] = 0;
        }
        aggregated[aggregatedPrice] += quantity;
    }

    return aggregated;
}

function updateOrderbookDelta(deltaUpdate) {
    const { side, price, unfilledQuantity } = deltaUpdate;

    // 將價格和未成交數量轉換為浮點數
    const parsedPrice = parseFloat(price);
    const parsedQuantity = parseFloat(unfilledQuantity);

    // 將價格調整到合適的精度來避免浮點數問題
    let aggregatedPrice = side === "BUY"
        ? Math.floor(parsedPrice / currentInterval + 1e-10) * currentInterval // 加入微小數字避免浮點數誤差
        : Math.ceil(parsedPrice / currentInterval - 1e-10) * currentInterval; // 減去微小數字避免浮點數誤差

    // 如果 interval 是 0.1，保留一位小數；否則保持整數
    aggregatedPrice = currentInterval === 0.1 ? parseFloat(aggregatedPrice.toFixed(1)) : aggregatedPrice;

    console.log(`原始價格: ${parsedPrice}, 聚合後價格: ${aggregatedPrice}, 未成交數量: ${parsedQuantity}`);

    // 根據買賣方向更新對應的訂單簿
    if (side === "BUY") {
        updateSide(orderbook.buy, aggregatedPrice, parsedQuantity);
    } else if (side === "SELL") {
        updateSide(orderbook.sell, aggregatedPrice, parsedQuantity);
    }

    // 在增量更新時，動態檢查並更新訂單簿顯示範圍
    updateOrderbookDisplay(orderbook);
}


function updateSide(orderbookSide, aggregatedPrice, parsedQuantity) {
    // 定義精度閾值
    const precisionThreshold = 1e-8;

    // 如果該價格不存在，則新增
    if (!orderbookSide[aggregatedPrice]) {
        orderbookSide[aggregatedPrice] = 0;
    }
    // 更新該價格的數量
    orderbookSide[aggregatedPrice] += parsedQuantity;

    // 刪除數量為零、小於零，或精度小於閾值的價格
    if (orderbookSide[aggregatedPrice] <= 0 || Math.abs(orderbookSide[aggregatedPrice]) <= precisionThreshold) {
        delete orderbookSide[aggregatedPrice];
    }
}

function updateOrderbookDisplay(orderbookUpdate) {
    const { buy, sell } = orderbookUpdate;

    // 將 buy 和 sell 轉換為 [price, quantity] 格式，並按價格排序
    const bids = Object.entries(buy)
        .map(([price, quantity]) => [parseFloat(price), quantity])
        .sort((a, b) => b[0] - a[0]); // 買單從高到低排列

    const asks = Object.entries(sell)
        .map(([price, quantity]) => [parseFloat(price), quantity])
        .sort((a, b) => b[0] - a[0]); // 賣單從高到低排列

    // 顯示五檔買單 (綠色)
    const bidsList = document.getElementById('bidsList');
    bidsList.innerHTML = '';
    const filledBids = bids.length < 5 ? [...bids, ...Array(5 - bids.length).fill(['-', '-'])] : bids; // 填充至底部
    filledBids.slice(0, 5).forEach(([price, totalQuantity]) => {
        // 如果 interval 是 0.1，保留一位小數；否則保持整數
        const formattedPrice = price !== '-' && currentInterval === 0.1 ? price.toFixed(1) : price;
        const formattedQuantity = totalQuantity !== '-' ? totalQuantity.toFixed(2) : '-';
        const row = `<tr class="bid-row"><td>${formattedPrice}</td><td>${formattedQuantity}</td></tr>`;
        bidsList.innerHTML += row;
    });

    // 顯示五檔賣單 (紅色)
    const asksList = document.getElementById('asksList');
    asksList.innerHTML = '';
    const filledAsks = asks.length < 5 ? [...Array(5 - asks.length).fill(['-', '-']), ...asks] : asks; // 填充至頂部
    filledAsks.slice(-5).forEach(([price, totalQuantity]) => {
        // 如果 interval 是 0.1，保留一位小數；否則保持整數
        const formattedPrice = price !== '-' && currentInterval === 0.1 ? price.toFixed(1) : price;
        const formattedQuantity = totalQuantity !== '-' ? totalQuantity.toFixed(2) : '-';
        const row = `<tr class="ask-row"><td>${formattedPrice}</td><td>${formattedQuantity}</td></tr>`;
        asksList.innerHTML += row;
    });
}

// 在頁面加載時連接 WebSocket
window.onload = function() {
    connectUserOrderWebSocket();
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

// 設置當價格間隔選擇變更時，重新建立 WebSocket 連接
document.getElementById('priceInterval').addEventListener('change', function() {
    connectOrderbookWebSocket(currentSymbol);
});