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
        console.log("UserOrderWebSocket 收到消息：", message);
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
    const orderTableBody = document.querySelector('#currentOrdersTable tbody');
    orderTableBody.innerHTML = '';

    // 添加快照中的所有訂單
    orders.forEach(order => addOrUpdateOrderRow(order));
}


function handleOrderNotification(notification) {
    const order = notification.data;

    switch (notification.eventType) {
        case 'ORDER_UPDATED':
            addOrUpdateOrderRow(order);
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
        document.querySelector('#currentOrdersTable tbody').appendChild(orderRow);
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
            <button onclick="editOrder('${order.id}')" class="custom-button">編輯</button>
            <button onclick="deleteOrder('${order.id}')" class="custom-button">刪除</button>
        </td>        
    `;

    if (order.status === 'COMPLETED') {
        setTimeout(() => removeOrderRow(order.id), 3000);
    }
}

let editingOrderId = null;

function editOrder(orderId) {
    editingOrderId = orderId;
    const orderRow = document.getElementById(`order-${orderId}`);
    const price = orderRow.children[3].textContent;
    const quantity = orderRow.children[4].textContent;

    // 顯示彈出視窗
    const editBox = document.getElementById("editOrderBox");
    editBox.style.display = "block";

    // 預填訂單的價格和數量
    document.getElementById("editPrice").value = price;
    document.getElementById("editQuantity").value = quantity;
}

function closeEditBox() {
    // 隱藏彈出視窗
    const editBox = document.getElementById("editOrderBox");
    editBox.style.display = "none";
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

    // 移除 active 樣式
    document.getElementById('limitButton').classList.remove('active');
    document.getElementById('marketButton').classList.remove('active');

    // 添加 active 樣式
    if (orderType === 'LIMIT') {
        document.getElementById('limitButton').classList.add('active');
        // 啟用價格欄位
        document.getElementById('price').disabled = false;
    } else if (orderType === 'MARKET') {
        document.getElementById('marketButton').classList.add('active');
        // 禁用價格欄位
        document.getElementById('price').disabled = true;
        document.getElementById('price').value = ''; // 清空價格欄位
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

    orderbook = { buy: {}, sell: {} };
    updateOrderbookDisplay(orderbook);

    const token = localStorage.getItem('jwtToken');
    currentSymbol = symbol;
    // 將 interval 作為參數添加到 WebSocket URL 中
    orderbookSocket = new WebSocket(`/ws/orderbook?symbol=${symbol}&interval=${currentInterval}`);

    orderbookSocket.onopen = function() {
        console.log(`訂單簿 WebSocket 連接已建立，交易對：${symbol}，價格間隔：${currentInterval}`);
    };

    orderbookSocket.onmessage = function(event) {
        const orderbookUpdate = JSON.parse(event.data);

        // 如果是完整快照（包含 `buy` 或 `sell`）
        if (orderbookUpdate.buy || orderbookUpdate.sell) {
            if (orderbookUpdate.buy) {
                orderbook.buy = aggregateOrderbook(orderbookUpdate.buy, currentInterval, "BUY");
            }

            if (orderbookUpdate.sell) {
                orderbook.sell = aggregateOrderbook(orderbookUpdate.sell, currentInterval, "SELL");
            }

            // 更新訂單簿顯示
            console.log("訂單簿快照更新：", orderbook);
            updateOrderbookDisplay(orderbook);
        } else {
            // 增量更新
            console.log("訂單簿增量：", orderbookUpdate);
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
                    document.getElementById("editOrderBox").style.display = "none";
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

// 當交易對變更時重新連接訂單簿 WebSocket
document.getElementById('symbol').addEventListener('change', updateSymbol);

// 設置當價格間隔選擇變更時，重新建立 WebSocket 連接
document.getElementById('priceInterval').addEventListener('change', function() {
    connectOrderbookWebSocket(currentSymbol);
});

// Tab 切換功能
document.querySelectorAll('.tab-button').forEach(button => {
    button.addEventListener('click', () => {
        const tab = button.getAttribute('data-tab');

        // 移除所有按鈕的 active 類
        document.querySelectorAll('.tab-button').forEach(btn => btn.classList.remove('active'));
        // 給點擊的按鈕添加 active 類
        button.classList.add('active');

        // 隱藏所有內容
        document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));
        // 顯示對應的內容
        document.getElementById(tab).classList.add('active');

        // 顯示或隱藏篩選器
        const filters = document.getElementById('filters');
        if (tab === 'historicalOrders' || tab === 'historicalTrades') {
            filters.classList.add('active');
            renderFilters(tab);
        } else {
            filters.classList.remove('active');
            filters.innerHTML = ''; // 清空篩選器內容
        }

        // 如果是資產管理，模擬獲取數據
        if (tab === 'assetManagement') {
            simulateAssetManagementData();
        } else if (tab === 'historicalOrders') {
            fetchHistoricalTradesData();
        } else if (tab === 'historicalTrades') {
            simulateHistoricalTradesData();
        }
    });
});

function renderFilters(tab) {
    const filters = document.getElementById('filters');
    filters.innerHTML = ''; // 清空篩選器內容

    // 時間範圍
    const timeRangeLabel = document.createElement('label');
    timeRangeLabel.textContent = '時間範圍:';
    const timeRangeSelect = document.createElement('select');
    timeRangeSelect.id = 'timeRange';
    timeRangeSelect.innerHTML = `
        <option value="1">1天</option>
        <option value="3">3天</option>
        <option value="7">7天</option>
    `;
    filters.appendChild(timeRangeLabel);
    filters.appendChild(timeRangeSelect);

    if (tab === 'historicalOrders') {
        // 類型
        const typeLabel = document.createElement('label');
        typeLabel.textContent = '類型:';
        const typeSelect = document.createElement('select');
        typeSelect.id = 'orderType';
        typeSelect.innerHTML = `
            <option value="">全部</option>
            <option value="LIMIT">LIMIT</option>
            <option value="MARKET">MARKET</option>
        `;
        filters.appendChild(typeLabel);
        filters.appendChild(typeSelect);

        // 方向
        const sideLabel = document.createElement('label');
        sideLabel.textContent = '方向:';
        const sideSelect = document.createElement('select');
        sideSelect.id = 'orderSide';
        sideSelect.innerHTML = `
            <option value="">全部</option>
            <option value="BUY">買入</option>
            <option value="SELL">賣出</option>
        `;
        filters.appendChild(sideLabel);
        filters.appendChild(sideSelect);

        // 狀態
        const statusLabel = document.createElement('label');
        statusLabel.textContent = '狀態:';
        const statusSelect = document.createElement('select');
        statusSelect.id = 'orderStatus';
        statusSelect.innerHTML = `
            <option value="">全部</option>
            <option value="COMPLETED">已完成</option>
            <option value="CANCELLED">已取消</option>
            <!-- 添加更多狀態 -->
        `;
        filters.appendChild(statusLabel);
        filters.appendChild(statusSelect);
    } else if (tab === 'historicalTrades') {
        // 方向
        const sideLabel = document.createElement('label');
        sideLabel.textContent = '方向:';
        const sideSelect = document.createElement('select');
        sideSelect.id = 'tradeSide';
        sideSelect.innerHTML = `
            <option value="">全部</option>
            <option value="BUY">買入</option>
            <option value="SELL">賣出</option>
        `;
        filters.appendChild(sideLabel);
        filters.appendChild(sideSelect);
    }

    // 添加查詢按鈕
    const searchButton = document.createElement('button');
    searchButton.id = 'searchButton';
    searchButton.textContent = '查詢';
    filters.appendChild(searchButton);

    // 綁定查詢按鈕事件
    searchButton.addEventListener('click', fetchHistoricalTradesData);
}

// 獲取歷史委託數據
async function fetchHistoricalTradesData() {
    const timeRange = document.getElementById('timeRange').value;
    const orderType = document.getElementById('orderType').value; // 類型
    const side = document.getElementById('orderSide').value; // 方向
    const status = document.getElementById('orderStatus').value; // 狀態
    const token = localStorage.getItem('jwtToken');

    try {
        // 建立查詢參數，只加入非空值
        const queryParams = new URLSearchParams();
        queryParams.append('timeRange', timeRange);

        // 僅在有值的情況下加入查詢參數
        if (orderType) queryParams.append('orderType', orderType);
        if (side) queryParams.append('side', side);
        if (status) queryParams.append('status', status);

        // 發送請求到後端 API，包含篩選參數
        const response = await fetch(`/api/v1/orders/history?${queryParams.toString()}`, {
            method: 'GET',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            }
        });

        // 檢查 HTTP 狀態
        if (!response.ok) {
            console.error('Failed to fetch order history', response.status);
            return;
        }

        // 解析返回的數據
        const orderHistory = await response.json();
        console.log('Historical trades:', orderHistory); // 調試用

        // 更新表格
        const tbody = document.getElementById('historicalOrdersTable').getElementsByTagName('tbody')[0];

        if (!tbody) {
            console.error('Table body not found.');
            return;
        }

        tbody.innerHTML = '';

        // 迭代每個 orderHistory 條目，並將其渲染到表格中
        orderHistory.forEach(entry => {
            const order = entry.order;
            const trades = entry.trades; // 獲取對應的 trades

            // 渲染 order 行
            const row = document.createElement('tr');
            row.classList.add('order-row'); // 添加 `hover` 效果
            row.innerHTML = `
                <td>
                    <span class="arrow down" data-order-id="${order.id}">▼</span>
                    ${new Date(order.createdAt).toLocaleString()}
                </td>
                <td>${order.symbol}</td>
                <td>${order.orderType}</td>
                <td>${order.side}</td>
                <td>${parseFloat(order.price).toFixed(2)}</td>
                <td>${parseFloat(order.price).toFixed(2)}</td>
                <td>${parseFloat(order.filledQuantity).toFixed(2)}</td>
                <td>${parseFloat(order.quantity).toFixed(2)}</td>
                <td>${order.status}</td>
            `;
            tbody.appendChild(row);

            // 隱藏的詳細內容行
            const detailsRow = document.createElement('tr');
            detailsRow.classList.add('order-details');
            detailsRow.style.display = 'none'; // 默認隱藏
            detailsRow.innerHTML = `
                <td colspan="9">
                    <div class="details-container" id="details-${order.id}">
                        <table>
                            <thead>
                                <tr>
                                    <th>成交時間</th>
                                    <th>成交價格</th>
                                    <th>成交數量</th>
                                    <th>角色</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${trades.map(trade => `
                                    <tr>
                                        <td>${new Date(trade.tradeTime).toLocaleString()}</td>
                                        <td>${parseFloat(trade.price).toFixed(2)}</td>
                                        <td>${parseFloat(trade.quantity).toFixed(2)}</td>
                                        <td>${trade.role}</td>
                                    </tr>
                                `).join('')}
                            </tbody>
                        </table>
                    </div>
                </td>
            `;
            tbody.appendChild(detailsRow);
        });

        // 設置箭頭的事件
        tbody.querySelectorAll('.arrow').forEach(arrow => {
            arrow.addEventListener('click', (event) => {
                const orderId = event.target.getAttribute('data-order-id');
                const detailsRow = document.querySelector(`#details-${orderId}`).closest('tr');
                const isExpanded = detailsRow.style.display === 'table-row';

                // 切換顯示/隱藏詳細信息
                detailsRow.style.display = isExpanded ? 'none' : 'table-row';

                // 切換箭頭方向
                arrow.classList.toggle('up', !isExpanded);
                arrow.classList.toggle('down', isExpanded);
            });
        });

        console.log('Table updated successfully'); // 調試用

    } catch (error) {
        console.error('Error fetching historical trades:', error);
    }
}



// 模擬獲取歷史成交數據
function simulateHistoricalTradesData() {
    const timeRange = document.getElementById('timeRange').value;
    const side = document.getElementById('tradeSide').value;

    const simulatedData = [
        {
            orderNumber: '123456',
            time: '2023-09-01 12:00:00',
            symbol: 'BTCUSDT',
            side: 'BUY',
            avgPrice: '50000.00',
            quantity: '0.5',
            role: 'Maker',
            amount: '25000.00'
        },
        // 添加更多模擬數據
    ];

    const filteredData = simulatedData.filter(trade => {
        return true;
    });

    const tbody = document.getElementById('historicalTradesTable').getElementsByTagName('tbody')[0];
    tbody.innerHTML = '';

    filteredData.forEach(trade => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${trade.orderNumber}</td>
            <td>${trade.time}</td>
            <td>${trade.symbol}</td>
            <td>${trade.side}</td>
            <td>${trade.avgPrice}</td>
            <td>${trade.quantity}</td>
            <td>${trade.role}</td>
            <td>${trade.amount}</td>
        `;
        tbody.appendChild(row);
    });
}

// 模擬獲取資產管理數據
function simulateAssetManagementData() {
    const simulatedData = [
        {
            currency: 'BTC',
            total: '1.5',
            available: '1.0',
            locked: '0.5'
        },
        {
            currency: 'USDT',
            total: '5000.00',
            available: '4500.00',
            locked: '500.00'
        },
        // 添加更多模擬數據
    ];

    const tbody = document.getElementById('assetManagementTable').getElementsByTagName('tbody')[0];
    tbody.innerHTML = '';

    simulatedData.forEach(asset => {
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${asset.currency}</td>
            <td>${asset.total}</td>
            <td>${asset.available}</td>
            <td>${asset.locked}</td>
        `;
        tbody.appendChild(row);
    });
}