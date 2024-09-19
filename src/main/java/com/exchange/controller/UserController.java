package com.exchange.controller;

import com.exchange.dto.UserLoginRequest;
import com.exchange.dto.UserRegistrationRequest;
import com.exchange.model.User;
import com.exchange.service.UserService;
import com.exchange.utils.JwtUtil;
import com.exchange.utils.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final SnowflakeIdGenerator idGenerator;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Autowired
    public UserController(UserService userService, SnowflakeIdGenerator idGenerator, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userService = userService;
        this.idGenerator = idGenerator;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // 註冊 API
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody UserRegistrationRequest request) {
        if (userService.getUserByUsername(request.getUsername()) != null) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "用戶名已存在");
            return ResponseEntity.badRequest().body(response);  // 返回 JSON
        }

        if (userService.getUserByEmail(request.getEmail()) != null) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "郵箱已存在");
            return ResponseEntity.badRequest().body(response);  // 返回 JSON
        }

        User user = new User();
        user.setId(String.valueOf(idGenerator.nextId()));
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus("active");
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        userService.saveUser(user);

        Map<String, String> response = new HashMap<>();
        response.put("message", "用戶註冊成功");

        return ResponseEntity.ok(response);  // 返回 JSON
    }

    // 登入 API
    @PostMapping("/login")
    public ResponseEntity<?> loginUser(@RequestBody UserLoginRequest request) {
        User user = userService.getUserByUsername(request.getUsername());
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "用戶名或密碼錯誤");
            return ResponseEntity.badRequest().body(response);  // 返回 JSON
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        Map<String, String> response = new HashMap<>();
        response.put("token", token);

        return ResponseEntity.ok(response);  // 返回 JSON
    }
}
