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

// 渲染用戶狀態
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
                <p>歡迎，${username}</p>
                <button onclick="logout()">登出</button>
            `;
        } catch (error) {
            console.error('無效的 JWT token:', error);
            userStatusDiv.innerHTML = `
                <a href="/login">登入</a> / <a href="/register">註冊</a>
            `;
        }
    } else {
        // 顯示登入/註冊按鈕
        userStatusDiv.innerHTML = `
            <a href="/login">登入</a> / <a href="/register">註冊</a>
        `;
    }
}

// 登出功能
function logout() {
    localStorage.removeItem('jwtToken');  // 清除 JWT token
    renderUserStatus();  // 更新頁面內容，顯示登入/註冊
}

// 在頁面加載時渲染用戶狀態
document.addEventListener("DOMContentLoaded", renderUserStatus);
