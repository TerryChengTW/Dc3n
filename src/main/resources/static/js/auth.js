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
            const payloadBase64 = token.split('.')[1];
            const decodedPayload = JSON.parse(atob(payloadBase64));
            const username = decodedPayload.username;

            userStatusDiv.innerHTML = `
                <span style="margin-right: 20px;">歡迎，${username}</span>
                <button onclick="logout()" class="auth-button">登出</button>
            `;
        } catch (error) {
            console.error('無效的 JWT token:', error);
            renderLoginRegisterButtons(userStatusDiv);
        }
    } else {
        renderLoginRegisterButtons(userStatusDiv);
    }
}

function renderLoginRegisterButtons(container) {
    container.innerHTML = `
        <a href="/login" class="auth-link">登入</a> / 
        <a href="/register" class="auth-link">註冊</a>
    `;
}

// 登出功能
function logout() {
    localStorage.removeItem('jwtToken');
    renderUserStatus();
}

// 主題切換功能
function toggleTheme() {
    document.body.classList.toggle('light-mode');
    const isLightMode = document.body.classList.contains('light-mode');
    localStorage.setItem('theme', isLightMode ? 'light' : 'dark');
    updateThemeButtonText();
}

function updateThemeButtonText() {
    const themeToggle = document.getElementById('themeToggle');
    const isLightMode = document.body.classList.contains('light-mode');
    themeToggle.textContent = isLightMode ? '切換深色模式' : '切換淺色模式';
}

// 在頁面加載時執行
document.addEventListener("DOMContentLoaded", function() {
    renderUserStatus();

    // 設置主題
    const savedTheme = localStorage.getItem('theme');
    if (savedTheme === 'light') {
        document.body.classList.add('light-mode');
    }
    updateThemeButtonText();

    // 綁定主題切換按鈕事件
    const themeToggle = document.getElementById('themeToggle');
    themeToggle.addEventListener('click', toggleTheme);
});