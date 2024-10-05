document.addEventListener('DOMContentLoaded', () => {
    // 交易對選擇器
    const symbolSelector = document.querySelector('.symbol-selector');
    const currentSymbol = document.getElementById('current-symbol');
    const dropdownContent = document.querySelector('.dropdown-content');

    // 模擬交易對列表
    const symbols = ['BTC/USDT', 'ETH/USDT', 'BNB/USDT', 'ADA/USDT', 'XRP/USDT'];

    // 填充交易對下拉列表
    symbols.forEach(symbol => {
        const a = document.createElement('a');
        a.href = '#';
        a.textContent = symbol;
        a.addEventListener('click', (e) => {
            e.preventDefault();
            currentSymbol.textContent = symbol;
            updateSymbolInfo(symbol);
            dropdownContent.style.display = 'none';
        });
        dropdownContent.appendChild(a);
    });

    // 顯示/隱藏下拉列表
    symbolSelector.addEventListener('click', () => {
        dropdownContent.style.display = dropdownContent.style.display === 'block' ? 'none' : 'block';
    });

    // 關閉下拉列表（當點擊其他地方時）
    window.addEventListener('click', (e) => {
        if (!symbolSelector.contains(e.target)) {
            dropdownContent.style.display = 'none';
        }
    });

    // 更新交易對資訊（模擬數據）
    function updateSymbolInfo(symbol) {
        document.getElementById('current-price').textContent = (Math.random() * 10000 + 50000).toFixed(2);
        const change = (Math.random() * 1000 - 500).toFixed(2);
        const changePercent = (change / 500 * 100).toFixed(2);
        const changeElement = document.getElementById('price-change');
        changeElement.textContent = `${change} (${changePercent}%)`;
        changeElement.className = parseFloat(change) >= 0 ? 'positive' : 'negative';

        document.getElementById('24h-high').textContent = (Math.random() * 1000 + 60000).toFixed(2);
        document.getElementById('24h-low').textContent = (Math.random() * 1000 + 59000).toFixed(2);
        document.getElementById('24h-volume').textContent = (Math.random() * 10000 + 5000).toFixed(3);
        document.getElementById('24h-amount').textContent = (Math.random() * 1000000000 + 5000000000).toFixed(2);

        updateOrderBook();
        updateRecentTrades();
    }

    // 更新訂單簿（模擬數據）
    function updateOrderBook() {
        const asks = document.getElementById('asks');
        const bids = document.getElementById('bids');
        const currentPrice = parseFloat(document.getElementById('current-price').textContent);

        asks.innerHTML = '';
        bids.innerHTML = '';

        for (let i = 0; i < 5; i++) {
            const askPrice = (currentPrice + Math.random() * 100).toFixed(2);
            const bidPrice = (currentPrice - Math.random() * 100).toFixed(2);
            const volume = (Math.random() * 10).toFixed(4);

            asks.innerHTML += `<tr><td>${askPrice}</td><td>${volume}</td></tr>`;
            bids.innerHTML += `<tr><td>${bidPrice}</td><td>${volume}</td></tr>`;
        }
    }

    // 更新最新成交（模擬數據）
    function updateRecentTrades() {
        const recentTradesTable = document.getElementById('recent-trades-table').getElementsByTagName('tbody')[0];
        const currentPrice = parseFloat(document.getElementById('current-price').textContent);

        recentTradesTable.innerHTML = '';

        for (let i = 0; i < 5; i++) {
            const price = (currentPrice + (Math.random() - 0.5) * 10).toFixed(2);
            const amount = (Math.random() * 1).toFixed(4);
            const time = new Date().toLocaleTimeString();

            recentTradesTable.innerHTML += `<tr><td>${price}</td><td>${amount}</td><td>${time}</td></tr>`;
        }
    }

    // 初始更新交易對資訊
    updateSymbolInfo('BTC/USDT');

    // 切換買入/賣出按鈕
    const buyButton = document.querySelector('.order-type[data-type="buy"]');
    const sellButton = document.querySelector('.order-type[data-type="sell"]');

    buyButton.addEventListener('click', () => {
        buyButton.classList.add('active');
        sellButton.classList.remove('active');
    });

    sellButton.addEventListener('click', () => {
        sellButton.classList.add('active');
        buyButton.classList.remove('active');
    });

    // 切換限價/市價按鈕
    const limitButton = document.querySelector('.order-type[data-type="limit"]');
    const marketButton = document.querySelector('.order-type[data-type="market"]');

    limitButton.addEventListener('click', () => {
        limitButton.classList.add('active');
        marketButton.classList.remove('active');
        document.getElementById('price').disabled = false;
    });

    marketButton.addEventListener('click', () => {
        marketButton.classList.add('active');
        limitButton.classList.remove('active');
        document.getElementById('price').disabled = true;
    });

    // 下單表單提交
    const orderForm = document.getElementById('place-order-form');
    orderForm.addEventListener('submit', (e) => {
        e.preventDefault();
        const price = document.getElementById('price').value;
        const quantity = document.getElementById('quantity').value;
        const orderType = document.querySelector('.order-type[data-type="buy"].active') ? 'buy' : 'sell';
        const priceType = document.querySelector('.order-type[data-type="limit"].active') ? 'limit' : 'market';

        console.log(`下單: ${orderType} ${priceType} ${quantity} @ ${price}`);
        // 這裡可以添加實際的下單邏輯
    });

    // 時間框架選擇
    const timeFrames = document.querySelectorAll('.time-frame');
    timeFrames.forEach(frame => {
        frame.addEventListener('click', () => {
            timeFrames.forEach(f => f.classList.remove('active'));
            frame.classList.add('active');
            // 這裡可以添加切換K線圖時間框架的邏輯
            console.log(`切換到 ${frame.dataset.time} 時間框架`);
        });
    });

    // 模擬實時更新
    setInterval(() => {
        updateSymbolInfo(currentSymbol.textContent);
    }, 5000);  // 每5秒更新一次
});

// 在 app.js 中添加以下代碼

// 訂單歷史選項切換
const orderOptions = document.querySelectorAll('.order-option');
orderOptions.forEach(option => {
    option.addEventListener('click', () => {
        orderOptions.forEach(opt => opt.classList.remove('active'));
        option.classList.add('active');
        updateOrderHistoryTable(option.dataset.option);
    });
});

function updateOrderHistoryTable(option) {
    const table = document.getElementById('order-history-table');
    const thead = table.querySelector('thead tr');
    const tbody = table.querySelector('tbody');

    // 清空現有內容
    thead.innerHTML = '';
    tbody.innerHTML = '';

    // 根據選項設置表頭和加載數據
    switch(option) {
        case 'current-orders':
            thead.innerHTML = `
                <th>訂單ID</th>
                <th>交易對</th>
                <th>類型</th>
                <th>價格</th>
                <th>數量</th>
                <th>已成交</th>
                <th>狀態</th>
            `;
            // 這裡添加加載當前訂單的邏輯
            break;
        case 'order-history':
            thead.innerHTML = `
                <th>時間</th>
                <th>交易對</th>
                <th>類型</th>
                <th>價格</th>
                <th>數量</th>
                <th>成交額</th>
                <th>狀態</th>
            `;
            // 這裡添加加載歷史訂單的邏輯
            break;
        case 'trade-history':
            thead.innerHTML = `
                <th>時間</th>
                <th>交易對</th>
                <th>方向</th>
                <th>價格</th>
                <th>數量</th>
                <th>成交額</th>
                <th>手續費</th>
            `;
            // 這裡添加加載成交歷史的邏輯
            break;
        case 'asset-management':
            thead.innerHTML = `
                <th>資產</th>
                <th>總額</th>
                <th>可用</th>
                <th>凍結</th>
                <th>估值(USDT)</th>
            `;
            // 這裡添加加載資產管理數據的邏輯
            break;
    }

    // 模擬數據加載
    for (let i = 0; i < 5; i++) {
        const row = tbody.insertRow();
        for (let j = 0; j < thead.cells.length; j++) {
            const cell = row.insertCell();
            cell.textContent = `數據 ${i+1}-${j+1}`;
        }
    }
}

// 初始化訂單歷史表格
updateOrderHistoryTable('current-orders');