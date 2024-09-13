package com.exchange.controller;

import com.exchange.dto.UserRegistrationRequest;
import com.exchange.model.User;
import com.exchange.service.UserService;
import com.exchange.utils.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;
    private final SnowflakeIdGenerator idGenerator;
    private final PasswordEncoder passwordEncoder;

    @Autowired
    public UserController(UserService userService, SnowflakeIdGenerator idGenerator, PasswordEncoder passwordEncoder) {
        this.userService = userService;
        this.idGenerator = idGenerator;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody UserRegistrationRequest request) {
        System.out.println("Received request to register user");
        // 檢查用戶名是否已存在
        System.out.println("request.getUsername() = " + request.getUsername());
        if (userService.getUserByUsername(request.getUsername()) != null) {
            return ResponseEntity.badRequest().body("用戶名已存在");
        }

        // 檢查郵箱是否已存在
        if (userService.getUserByEmail(request.getEmail()) != null) {
            return ResponseEntity.badRequest().body("郵箱已存在");
        }

        // 創建新用戶
        User user = new User();
        user.setId(String.valueOf(idGenerator.nextId()));
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus("active");
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        // 保存用戶
        userService.saveUser(user);

        return ResponseEntity.ok("用戶註冊成功");
    }
}
