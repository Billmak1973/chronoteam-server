package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.dto.LoginRequest;
import org.example.website.dto.RegisterRequest;
import org.example.website.entity.Customer;
import org.example.website.repository.CustomerRepository;
import org.example.website.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final CustomerService customerService;
    private final AuthenticationManager authenticationManager;
    private final CustomerRepository customerRepository;
    //  構造函數同時注入兩個依賴
    public ApiController(CustomerService customerService,
                         AuthenticationManager authenticationManager,
                         CustomerRepository customerRepository) {
        this.customerService = customerService;
        this.authenticationManager = authenticationManager;
        this.customerRepository = customerRepository;
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

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@RequestBody @Valid LoginRequest request) {
        try {
            // 1. 先檢查用戶名是否存在 (防止 Spring Security 直接拋出統一錯誤)
            if (!customerRepository.existsById(request.getUsername())) {
                // 返回特定標識，方便前端區分
                return ResponseEntity.badRequest().body(ApiResponse.error("USER_NOT_FOUND:該用戶名不存在"));
            }

            // 2. 用戶名存在，繼續驗證密碼
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // 3. 驗證成功：將認證信息存入 SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return ResponseEntity.ok(ApiResponse.ok("登入成功"));

        } catch (BadCredentialsException e) {
            // 密碼錯誤
            return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_PASSWORD:輸入密碼不正確"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("系統錯誤，請稍後重試"));
        }
    }
}