let socket;
let selectedSide = '';
let selectedOrderType = '';

function connectWebSocket() {
    const token = localStorage.getItem('jwtToken');
    socket = new WebSocket(`ws://localhost:8081/ws?token=${token}`);

    socket.onopen = function(e) {
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
    const orderTableBody = document.querySelector('#orderTable tbody');

    switch (notification.eventType) {
        case 'ORDER_CREATED':
            addOrUpdateOrderRow(order);
            break;
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

window.onload = function() {
    connectWebSocket();
};
