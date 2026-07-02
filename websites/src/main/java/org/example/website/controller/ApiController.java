package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.dto.LoginRequest;
import org.example.website.dto.RegisterRequest;
import org.example.website.entity.User;
import org.example.website.repository.UserRepository;
import org.example.website.service.UserService;
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

    private final UserService userService; //
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository; //

    // 構造函數注入依賴
    public ApiController(UserService userService,
                         AuthenticationManager authenticationManager,
                         UserRepository userRepository) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
    }

    // 註冊接口
    @PostMapping("/register")
    public ResponseEntity<ApiResponse> register(@RequestBody @Valid RegisterRequest request) {
        try {
            // 調用新的 UserService，返回 User 實體
            User user = userService.register(request);
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
            // User 的主鍵是 Long id，username 是業務鍵。
            // 必須使用 existsByUsername，不能再使用 existsById！
            if (!userRepository.existsByUsername(request.getUsername())) {
                // 返回特定標識，方便前端區分
                return ResponseEntity.badRequest().body(ApiResponse.error("USER_NOT_FOUND:該用戶名不存在"));
            }

            // 2. 用戶名存在，繼續驗證密碼 (Spring Security 會自動調用 CustomUserDetailsService)
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