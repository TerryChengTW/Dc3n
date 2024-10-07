// 檢查 JWT token 是否存在並且未過期
function isTokenExpired(token) {
    try {
        const payloadBase64 = token.split('.')[1];
        const decodedPayload = JSON.parse(atob(payloadBase64));  // 解碼 Payload
        const expirationTime = decodedPayload.exp * 1000;  // exp 是 Unix 時間戳，以秒為單位
        const currentTime = Date.now();
        return expirationTime < currentTime;  // 如果過期，返回 true
    } catch (error) {
        console.error('無效的 JWT token:', error);
        return true;  // 如果 token 無效，視為過期
    }
}

function checkToken() {
    const token = localStorage.getItem('jwtToken');
    if (token && !isTokenExpired(token)) {
        return token;  // 返回有效的 token
    }
    return null;  // 沒有 token 或已過期
}

function renderUserStatus() {
    const userStatusDiv = document.getElementById('userStatus');
    const token = checkToken();

    if (token) {
        try {
            // 顯示用戶名和登出按鈕
            const payloadBase64 = token.split('.')[1];
            const decodedPayload = JSON.parse(atob(payloadBase64));
            const username = decodedPayload.username;

            userStatusDiv.innerHTML = `
                <span style="margin-right: 20px;">歡迎，${username}</span>
                <button onclick="logout()" style="background-color: #000000; color: #00dfb6; border: none; padding: 5px 10px; cursor: pointer; border-radius: 5px; box-shadow: 0px 4px 6px rgba(0, 0, 0, 0.1); transition: background-color 0.3s ease;">登出</button>
            `;
        } catch (error) {
            console.error('無效的 JWT token:', error);
            userStatusDiv.innerHTML = `
                <a href="/login" style="color: #000000; margin-right: 10px;">登入</a> / 
                <a href="/register" style="color: #000000;">註冊</a>
            `;
        }
    } else {
        // 顯示登入/註冊按鈕
        userStatusDiv.innerHTML = `
            <a href="/login" style="color: #000000; margin-right: 10px;">登入</a> / 
            <a href="/register" style="color: #000000;">註冊</a>
        `;
    }
}

// 登出功能
function logout() {
    localStorage.removeItem('jwtToken');  // 清除 JWT token
    renderUserStatus();  // 更新頁面內容，顯示登入/註冊
    updateAuthButtons();  // 更新 trade.html 頁面的按鈕顯示
    checkAuthAndRender();  // 更新所有選項卡狀態和內容
}

// 在頁面加載時渲染用戶狀態
document.addEventListener("DOMContentLoaded", renderUserStatus);

function updateAuthButtons() {
    const token = localStorage.getItem('jwtToken');
    if (token) {
        // 如果有 jwtToken，顯示下單按鈕
        document.getElementById('submitButton').style.display = 'block';
        document.getElementById('loginButton').style.display = 'none';
    } else {
        // 如果沒有 jwtToken，顯示登入/註冊按鈕
        document.getElementById('submitButton').style.display = 'none';
        document.getElementById('loginButton').style.display = 'block';

        // 移除篩選器
        const filters = document.getElementById('filters');
        if (filters) filters.innerHTML = ''; // 清空篩選器內容
    }
}

