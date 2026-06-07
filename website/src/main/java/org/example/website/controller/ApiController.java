package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.dto.LoginRequest;  //  新增 DTO
import org.example.website.dto.RegisterRequest;
import org.example.website.entity.Customer;
import org.example.website.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;  //  新增
import org.springframework.security.authentication.BadCredentialsException;  //  新增
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;  //  新增
import org.springframework.security.core.Authentication;  //  新增
import org.springframework.security.core.context.SecurityContextHolder;  //  新增
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;  // 改用 jakarta

@RestController
@RequestMapping("/api")
public class ApiController {

    private final CustomerService customerService;
    private final AuthenticationManager authenticationManager;  //  新增：用於驗證登入

    //  構造函數同時注入兩個依賴
    public ApiController(CustomerService customerService,
                         AuthenticationManager authenticationManager) {
        this.customerService = customerService;
        this.authenticationManager = authenticationManager;
    }

    //  原有註冊接口（保持不變）
    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(@RequestBody @Valid RegisterRequest request) {
        try {
            Customer customer = customerService.register(request);
            return ResponseEntity.ok(ApiResponse.ok("註冊成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("系統錯誤，請稍後重試"));
        }
    }

    //  新增：登入接口
    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@RequestBody @Valid LoginRequest request) {
        try {
            // 1. 使用 AuthenticationManager 驗證用戶名密碼
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // 2. 驗證成功：將認證信息存入 SecurityContext（自動綁定到 Session）
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 3. 返回成功響應
            return ResponseEntity.ok(ApiResponse.ok("登入成功"));

        } catch (BadCredentialsException e) {
            // 用戶名或密碼錯誤
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("用戶名或密碼錯誤"));
        } catch (Exception e) {
            // 其他系統錯誤
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("系統錯誤，請稍後重試"));
        }
    }
}