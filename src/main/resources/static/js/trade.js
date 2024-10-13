let userOrderSocket;

function connectUserOrderWebSocket() {
    const token = localStorage.getItem('jwtToken');

    if (!token) {
        console.log("未找到 jwtToken，不建立 WebSocket 連線");
        return;
    }

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
    orderTableBody.innerHTML = ''; // 清空原有內容

    // 初始化索引
    let index = 0;

    // 每次渲染的批量數量（根據情況調整）
    const batchSize = 20;

    function renderBatch() {
        // 使用 DocumentFragment 批量更新 DOM
        const fragment = document.createDocumentFragment();

        // 渲染一批訂單
        for (let i = 0; i < batchSize && index < orders.length; i++, index++) {
            const order = orders[index];
            const row = createOrderRow(order);
            fragment.appendChild(row);
        }

        // 插入到表格中
        orderTableBody.appendChild(fragment);

        // 如果還有未渲染的訂單，繼續下一批
        if (index < orders.length) {
            requestAnimationFrame(renderBatch); // 使用 requestAnimationFrame 非阻塞地渲染下一批
        }
    }

    // 開始渲染
    requestAnimationFrame(renderBatch);
}

function createOrderRow(order) {
    const row = document.createElement('tr');
    row.id = `order-${order.id}`;
    if (order.side === 'BUY') {
        row.classList.add('bid-row');
    } else if (order.side === 'SELL') {
        row.classList.add('ask-row');
    }

    row.innerHTML = `
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
    return row;
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

let selectedSide = null;
let selectedOrderType = null;

// 新增一個函數來檢查按鈕狀態
function updateSubmitButtonState() {
    const price = document.getElementById('price').value;
    const quantity = document.getElementById('quantity').value;
    const submitButton = document.getElementById('submitButton');

    // 依據 orderType 檢查條件：如果是 LIMIT，price 和 quantity 必填；如果是 MARKET，quantity 必填
    if (selectedSide && selectedOrderType && quantity &&
        (selectedOrderType === 'MARKET' || (selectedOrderType === 'LIMIT' && price))) {
        submitButton.disabled = false; // 啟用按鈕
    } else {
        submitButton.disabled = true; // 禁用按鈕
    }
}

// 更新的 selectSide 函數
function selectSide(side) {
    selectedSide = side;

    document.getElementById('buyButton').classList.remove('active');
    document.getElementById('sellButton').classList.remove('active');
    if (side === 'BUY') {
        document.getElementById('buyButton').classList.add('active');
    } else {
        document.getElementById('sellButton').classList.add('active');
    }

    // 更新按鈕狀態
    updateSubmitButtonState();
}

// 更新的 selectOrderType 函數
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

    // 更新按鈕狀態
    updateSubmitButtonState();
}

// 每當 price 或 quantity 改變時更新按鈕狀態
document.getElementById('price').addEventListener('input', updateSubmitButtonState);
document.getElementById('quantity').addEventListener('input', updateSubmitButtonState);


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

    currentSymbol = symbol;
    recentTradesSocket = new WebSocket(`/ws/recent-trades/${symbol}`);

    recentTradesSocket.onopen = function() {
        console.log(`最新成交 WebSocket 連接已建立，交易對：${symbol}`);
    };

    let isFirstMessage = true; // 標誌變數

    recentTradesSocket.onmessage = function(event) {
        const tradesList = document.getElementById('recentTradesList');
        const tradesData = JSON.parse(event.data);
        console.log("最新成交：", tradesData);

        if (isFirstMessage) {
            // 清空默認的空白行
            tradesList.innerHTML = '';

            // 如果收到的是空陣列，填充 5 個佔位符
            if (Array.isArray(tradesData) && tradesData.length === 0) {
                for (let i = 0; i < 5; i++) {
                    const placeholderRow = document.createElement('tr');
                    placeholderRow.innerHTML = `
                    <td>-</td>
                    <td>-</td>
                    <td>-</td>
                `;
                    tradesList.appendChild(placeholderRow);
                }
            } else if (Array.isArray(tradesData)) {
                // 處理所有的交易資料
                tradesData.forEach(trade => {
                    addRecentTrade(trade);
                });
            }

            isFirstMessage = false;
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
        loadKlineData(symbol, selectedTimeFrame);
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

function checkAuthAndRender() {
    const jwtToken = localStorage.getItem('jwtToken');

    // 設置 Tab 切換
    setupTabs();

    // 如果沒有 jwtToken，渲染所有表格內容為登入按鈕
    if (!jwtToken) {
        // 清空所有內容（如果登出）
        document.querySelectorAll('.tab-content').forEach(content => content.innerHTML = '');
        renderLoginButtonInAllTabs();
    } else {
        // 如果有 jwtToken，渲染對應的內容
        document.querySelectorAll('.tab-button').forEach(button => {
            // 自動點擊當前選中的 tab，觸發渲染
            if (button.classList.contains('active')) {
                button.click();
            }
        });
    }
}


function renderLoginButtonInAllTabs() {
    // 獲取所有 tab-content 元素
    const tabContents = document.querySelectorAll('.tab-content');

    // 將每個 tab-content 的內容替換為登入按鈕
    tabContents.forEach(content => {
        content.innerHTML = ''; // 清空內容

        const loginButtonContainer = document.createElement('div');
        loginButtonContainer.style.textAlign = 'center'; // 置中按鈕

        const loginButton = document.createElement('button');
        loginButton.className = 'login-button';
        loginButton.textContent = '立即登入';
        loginButton.onclick = () => {
            window.location.href = '/login'; // 跳轉到登入頁面
        };

        loginButtonContainer.appendChild(loginButton);
        content.appendChild(loginButtonContainer);
    });
}

function setupTabs() {
    // Tab 切換功能
    document.querySelectorAll('.tab-button').forEach(button => {
        button.addEventListener('click', async () => {
            const tab = button.getAttribute('data-tab');

            // 移除所有按鈕的 active 類
            document.querySelectorAll('.tab-button').forEach(btn => btn.classList.remove('active'));
            // 給點擊的按鈕添加 active 類
            button.classList.add('active');

            // 隱藏所有內容
            document.querySelectorAll('.tab-content').forEach(content => content.classList.remove('active'));
            // 顯示對應的內容
            document.getElementById(tab).classList.add('active');

            // 渲染篩選條件
            renderFilters(tab);

            // 根據 tab 進行首次查詢
            if (tab === 'assetManagement') {
                simulateAssetManagementData();
            } else if (tab === 'historicalOrders') {
                await fetchHistoricalDelegatesData(); // 使用 await 進行異步操作
            } else if (tab === 'historicalTrades') {
                await fetchHistoricalTradesData(); // 使用 await 進行異步操作
            }
        });
    });
}

// 初始檢查並渲染
checkAuthAndRender();

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

    // 類型（預設隱藏）
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
    sideSelect.id = tab === 'historicalOrders' ? 'orderSide' : 'tradeSide';
    sideSelect.innerHTML = `
        <option value="">全部</option>
        <option value="BUY">買入</option>
        <option value="SELL">賣出</option>
    `;
    filters.appendChild(sideLabel);
    filters.appendChild(sideSelect);

    // 狀態（預設隱藏）
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

    // 添加查詢按鈕
    const searchButton = document.createElement('button');
    searchButton.id = 'searchButton';
    searchButton.textContent = '查詢';
    filters.appendChild(searchButton);

    // 根據 tab 來控制篩選項目的顯示或隱藏
    if (tab === 'historicalOrders') {
        typeLabel.style.display = 'block';
        typeSelect.style.display = 'block';
        statusLabel.style.display = 'block';
        statusSelect.style.display = 'block';
        searchButton.addEventListener('click', fetchHistoricalDelegatesData);
    } else if (tab === 'historicalTrades') {
        typeLabel.style.display = 'none';
        typeSelect.style.display = 'none';
        statusLabel.style.display = 'none';
        statusSelect.style.display = 'none';
        searchButton.addEventListener('click', fetchHistoricalTradesData);
    } else {
        // 如果有其他 tab，需要確保所有篩選條件隱藏
        typeLabel.style.display = 'none';
        typeSelect.style.display = 'none';
        statusLabel.style.display = 'none';
        statusSelect.style.display = 'none';
        searchButton.style.display = 'none';
        timeRangeLabel.style.display = 'none';
        sideLabel.style.display = 'none';
        timeRangeSelect.style.display = 'none';
        sideSelect.style.display = 'none';
    }
}

// 顯示指定區域的加載動畫
function showLoadingSpinner(spinnerId) {
    document.getElementById(spinnerId).style.display = 'block';
}

// 隱藏指定區域的加載動畫
function hideLoadingSpinner(spinnerId) {
    document.getElementById(spinnerId).style.display = 'none';
}

// 獲取歷史委託數據
async function fetchHistoricalDelegatesData() {
    showLoadingSpinner('orders-loading-spinner');
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
            const trades = entry.trades;

            // 渲染 order 行
            const row = document.createElement('tr');
            row.classList.add('order-row'); // 添加 `hover` 效果
            // 計算平均價格
            const totalQuantity = trades.reduce((sum, trade) => sum + parseFloat(trade.quantity), 0);
            const totalWeightedPrice = trades.reduce((sum, trade) => sum + (parseFloat(trade.price) * parseFloat(trade.quantity)), 0);
            const averagePrice = totalQuantity ? (totalWeightedPrice / totalQuantity).toFixed(2) : '-';

            // 設置狀態顏色
            const statusColor = order.status === 'COMPLETED' ? 'green' : order.status === 'CANCELLED' ? 'gray' : '';

            // 設置方向顏色
            const sideColor = order.side === 'BUY' ? '#008000' : order.side === 'SELL' ? '#d10000' : '';

            // 修改 order 行，使用計算出的平均價格
            row.innerHTML = `
                <td>
                    <span class="arrow down" data-order-id="${order.id}">▼</span>
                    ${new Date(order.createdAt).toLocaleString()}
                </td>
                <td>${order.symbol}</td>
                <td>${order.orderType}</td>
                <td style="color: ${sideColor};">${order.side}</td>
                <td>${averagePrice}</td>
                <td>${order.price === null ? '-' : parseFloat(order.price).toFixed(2)}</td>
                <td>${parseFloat(order.filledQuantity).toFixed(2)}</td>
                <td>${parseFloat(order.quantity).toFixed(2)}</td>
                <td style="color: ${statusColor};">${order.status}</td>
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
    } finally {
        hideLoadingSpinner('orders-loading-spinner'); // 確保無論成功或失敗都隱藏動畫
    }
}


async function fetchHistoricalTradesData() {
    showLoadingSpinner('trades-loading-spinner');
    const timeRange = document.getElementById('timeRange').value;
    const side = document.getElementById('tradeSide').value;

    let tradeHistoryUrl = `/api/trade-history?`;

    // 根據篩選條件構建查詢參數
    if (timeRange) {
        tradeHistoryUrl += `timeRange=${timeRange}&`;
    }
    if (side) {
        tradeHistoryUrl += `direction=${side}`;
    }

    try {
        // 你需要在這裡加入你自己的 JWT token
        const jwtToken = localStorage.getItem('jwtToken');

        // 發送請求
        const response = await fetch(tradeHistoryUrl, {
            method: 'GET',
            headers: {
                'Authorization': `Bearer ${jwtToken}`,
                'Content-Type': 'application/json',
            },
        });

        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }

        const tradeData = await response.json();

        // 渲染數據
        renderTradeHistory(tradeData);
    } catch (error) {
        console.error('Error fetching trade history:', error);
    } finally {
        hideLoadingSpinner('trades-loading-spinner'); // 確保無論成功或失敗都隱藏動畫
    }
}

function renderTradeHistory(tradeData) {
    const tbody = document.getElementById('historicalTradesTable').getElementsByTagName('tbody')[0];
    tbody.innerHTML = '';

    tradeData.forEach(trade => {
        const row = document.createElement('tr');
        const directionColor = trade.direction === 'BUY' ? '#28a745' : '#dc3545';

        row.innerHTML = `
            <td>${trade.tradeId}</td>
            <td>${new Date(trade.tradeTime).toLocaleString()}</td>
            <td>${trade.symbol}</td>
            <td style="color: ${directionColor};">${trade.direction}</td>
            <td>${trade.avgPrice}</td>
            <td>${trade.quantity}</td>
            <td>${trade.role}</td>
            <td>${trade.totalAmount}</td>
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


document.addEventListener('DOMContentLoaded', function() {
    const token = localStorage.getItem('jwtToken');
    console.log('JWT Token:', token); // 除錯用
    if (token) {
        console.log('Token found, showing submit button.');
        document.getElementById('submitButton').style.display = 'block';
        document.getElementById('loginButton').style.display = 'none';
    } else {
        console.log('No token found, showing login button.');
        document.getElementById('submitButton').style.display = 'none';
        document.getElementById('loginButton').style.display = 'block';
    }
});


// 轉到登入頁面的函式
function redirectToLogin() {
    // 這裡處理轉到登入頁面的邏輯
    window.location.href = '/login';
}

// 創建圖表
const chart = LightweightCharts.createChart(document.getElementById('chart'), {
    layout: {
        backgroundColor: '#FFFFFF',
        textColor: '#000',
    },
    grid: {
        vertLines: {
            color: '#e1e1e1',
        },
        horzLines: {
            color: '#e1e1e1',
        },
    },
    crosshair: {
        mode: LightweightCharts.CrosshairMode.Normal,
    },
    rightPriceScale: {
        borderColor: '#AAA',
    },
    timeScale: {
        borderColor: '#AAA',
        timeVisible: true,
        secondsVisible: false,
    },
});

const candleSeries = chart.addCandlestickSeries();
let visibleCandles = 20; // 初始顯示50根K線
let originalData = []; // 用來儲存K線數據
let lastCandle = null; // 用來儲存最新的未結束K棒

// 設置 WebSocket 連接
let kLineSocket = null;
let selectedSymbol = 'BTCUSDT'; // 默認幣種
let selectedTimeFrame = '1m'; // 默認時間框架

function calculateCurrentIntervalTime(time, timeFrame) {
    let intervalInSeconds;
    switch (timeFrame) {
        case '1m':
            intervalInSeconds = 60;
            break;
        case '5m':
            intervalInSeconds = 300; // 5分鐘
            break;
        case '1h':
            intervalInSeconds = 3600; // 1小時
            break;
        default:
            intervalInSeconds = 60; // 默認為1分鐘
            break;
    }
    return Math.floor(time / intervalInSeconds) * intervalInSeconds;
}

// 創建 WebSocket 連接
function loadKlineData(symbol, timeFrame) {
    // 如果已有 WebSocket 連接，先關閉它
    if (kLineSocket) {
        kLineSocket.close();
        console.log('kline WebSocket 已斷開');
        lastCandle = null;
    }

    // 建立新的 WebSocket 連接，帶上選擇的幣種和時間框架
    const wsUrl = `/ws/kline?symbol=${symbol}&timeFrame=${timeFrame}`;
    kLineSocket = new WebSocket(wsUrl);

    kLineSocket.onmessage = function(event) {
        const message = JSON.parse(event.data);
        console.log("kline 收到消息: ", message);

        // 處理歷史K線數據
        if (Array.isArray(message) && message.length > 0 && message[0].type === 'historical') {
            originalData = message.map(d => ({
                time: d.time, // 假設傳來的是秒級時間戳
                open: d.open,
                high: d.high,
                low: d.low,
                close: d.close
            })).sort((a, b) => a.time - b.time);

            console.log("kline 歷史數據：", originalData);
            candleSeries.setData(originalData); // 初始化時直接設定K線數據
            updateChart();
        }

        // 處理當前K棒的既有數據
        else if (message.type === 'current_kline' && message.symbol === symbol) {
            const currentInterval = calculateCurrentIntervalTime(message.time, timeFrame);

            if (!lastCandle || lastCandle.time !== currentInterval) {
                // 創建新K棒
                lastCandle = {
                    time: currentInterval,
                    open: message.open,
                    high: message.high,
                    low: message.low,
                    close: message.close
                };
            } else {
                lastCandle.high = Math.max(lastCandle.high, message.high);
                lastCandle.low = Math.min(lastCandle.low, message.low);
                lastCandle.close = message.close;
            }

            // 更新圖表
            candleSeries.update(lastCandle);
        }

        // 處理實時成交數據
        else if (message.type === 'trade' && message.symbol === symbol) {
            const tradeInterval = calculateCurrentIntervalTime(message.time, timeFrame);

            if (lastCandle && lastCandle.time === tradeInterval) {
                // 更新當前K棒
                lastCandle.close = message.price;
                lastCandle.high = Math.max(lastCandle.high, message.price);
                lastCandle.low = Math.min(lastCandle.low, message.price);

                // 更新圖表
                candleSeries.update(lastCandle);
            } else if (lastCandle) {
                console.log('新時間段，將當前K棒視為完成');

                // 創建新K棒，將上一根K棒的收盤價作為新K棒的開盤價
                lastCandle = {
                    time: tradeInterval,
                    open: lastCandle.close,  // 使用上一根K棒的收盤價作為新K棒的開盤價
                    high: message.price,
                    low: message.price,
                    close: message.price
                };

                // 更新新K棒到圖表
                candleSeries.update(lastCandle);
            }
        }
    };

    kLineSocket.onerror = function(error) {
        console.error('WebSocket 錯誤: ', error);
    };

    kLineSocket.onclose = function() {
        console.log('WebSocket 已關閉');
    };
}

// 初始化載入數據
loadKlineData(selectedSymbol, selectedTimeFrame);

// 用戶選擇不同幣種或時間框架時調用此函數
function updateTimeFrame(timeFrame) {
    selectedTimeFrame = timeFrame;
    selectedSymbol = document.getElementById('symbol').value;
    loadKlineData(selectedSymbol, selectedTimeFrame);
}

let minCandles = 20;   // 最少顯示10根K線
let maxCandles = 100;  // 最多顯示100根K線

// 更新圖表數據並控制可見範圍內的K線數量
function updateChart() {
    const dataToShow = originalData.slice(-visibleCandles);
    candleSeries.setData(dataToShow);

    // 調整時間範圍以適應數據
    const visibleLogicalRange = chart.timeScale().getVisibleLogicalRange();
    const barsInfo = candleSeries.barsInLogicalRange(visibleLogicalRange);

    // 計算當前顯示的K線數量
    const visibleBars = barsInfo?.barsBefore + barsInfo?.barsAfter + 1;

    // 控制顯示的K線數量在最小和最大範圍內
    if (visibleBars < minCandles) {
        // 如果顯示的K線數量小於最小值，擴大顯示範圍
        visibleCandles = minCandles;
        chart.timeScale().fitContent();  // 自動適應數據範圍
    } else if (visibleBars > maxCandles) {
        // 如果顯示的K線數量大於最大值，縮小顯示範圍
        const logicalRange = chart.timeScale().getVisibleLogicalRange();
        const newRange = {
            from: logicalRange.from + (visibleBars - maxCandles),
            to: logicalRange.to,
        };
        chart.timeScale().setVisibleLogicalRange(newRange);  // 設置新的顯示範圍
    }
}



let isLoading = false;  // 用來控制加載狀態

// 監聽圖表的時間範圍變化來加載更多的歷史數據
let initialTriggerCount = 0;  // 用來追踪可見範圍變化的觸發次數

chart.timeScale().subscribeVisibleTimeRangeChange(function() {
    // 初始載入完成後才監聽用戶的操作
    if (initialTriggerCount < 2) {
        initialTriggerCount++;  // 每次觸發都增加計數
        console.log('初始觸發次數: ', initialTriggerCount);
        return;  // 前兩次不執行加載數據
    }

    // 檢查圖表的可見範圍
    const visibleLogicalRange = chart.timeScale().getVisibleLogicalRange();
    const barsInfo = candleSeries.barsInLogicalRange(visibleLogicalRange);

    // 當顯示的數據到達圖表左邊界並且無更多數據可顯示時，才加載更多數據
    // console.log('barsInfo:', barsInfo);
    if (!isLoading && barsInfo.barsBefore<0) {
        console.log('準備加載更多數據...');
        isLoading = true;  // 標記正在加載
        loadMoreExistingData();  // 加載數據
    }
});


// 從已有數據中加載更多的歷史數據
function loadMoreExistingData() {
    // 獲取當前可見範圍的開始和結束時間
    const visibleRange = chart.timeScale().getVisibleRange();

    // 加載數據邏輯
    visibleCandles = Math.min(visibleCandles + 400, originalData.length);
    loadChart();

    // 在加載數據後，將顯示範圍設置回之前的範圍
    chart.timeScale().setVisibleRange(visibleRange);

    // 檢查本地數據是否已經全部加載完畢
    if (visibleCandles >= originalData.length) {
        const earliestTimestamp = originalData[0]?.time || 0;
        console.log('最早的時間戳:', earliestTimestamp);
        console.log('本地數據已經顯示完，開始加載更多數據...');
        // 如果本地數據加載完，向後端請求更多數據
        fetch(`/api/kline/${selectedSymbol}/${earliestTimestamp}?timeframe=${selectedTimeFrame}`)
            .then(response => {
                if (!response.ok) {
                    throw new Error('Network response was not ok');
                }
                return response.json();
            })
            .then(newData => {
                console.log('接收到後端數據:', newData);

                if (newData.length > 0) {
                    // 合併新數據與現有的 originalData
                    originalData = newData.concat(originalData);
                    console.log('合併後的 originalData:', originalData);
                    originalData.sort((a, b) => a.time - b.time);
                    visibleCandles = Math.min(visibleCandles + 100, originalData.length);
                    loadChart();  // 更新圖表
                    console.log('圖表已更新，當前 visibleCandles:', visibleCandles);
                } else {
                    console.log('後端沒有返回新的數據');
                }
                isLoading = false;  // 加載完成後重置加載標誌
            })
            .catch(error => {
                console.error("加載數據失敗", error);
                isLoading = false;
            });
    } else {
        // 如果還有本地數據可以顯示
        setTimeout(() => {
            isLoading = false;  // 加載完成後重置加載標誌
            console.log('本地數據加載完成');
        }, 500);  // 模擬耗時
    }
}


// 更新圖表數據
function loadChart() {
    const dataToShow = originalData.slice(-visibleCandles);  // 顯示最近的 visibleCandles 根K棒
    if (lastCandle) {
        // 如果存在未完成的當前K棒，將它添加到數據中
        dataToShow.push(lastCandle);
    }
    candleSeries.setData(dataToShow);  // 更新圖表
}


chart.subscribeCrosshairMove(function(param) {
    if (!param || !param.time || !param.seriesData.get(candleSeries)) {
        // 如果沒有滑鼠指向的 K 線數據，顯示為 '-'
        document.getElementById('info-time').textContent = '-';
        document.getElementById('info-open').textContent = '-';
        document.getElementById('info-high').textContent = '-';
        document.getElementById('info-low').textContent = '-';
        document.getElementById('info-close').textContent = '-';
        return;
    }

    const klineData = param.seriesData.get(candleSeries);

    // 更新 K 線數據
    document.getElementById('info-time').textContent = new Date(param.time * 1000).toLocaleString();  // 假設是 Unix 時間戳，轉換為本地時間
    document.getElementById('info-open').textContent = klineData.open.toFixed(2);
    document.getElementById('info-high').textContent = klineData.high.toFixed(2);
    document.getElementById('info-low').textContent = klineData.low.toFixed(2);
    document.getElementById('info-close').textContent = klineData.close.toFixed(2);
});

// 監聽窗口大小變化
window.addEventListener('resize', () => {
    const chartContainer = document.getElementById('chart');
    chart.resize(chartContainer.clientWidth, chartContainer.clientHeight);
});