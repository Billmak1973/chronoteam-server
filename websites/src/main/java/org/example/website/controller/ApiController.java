package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.website.dto.LoginRequest;
import org.example.website.dto.RegisterRequest;
import org.example.website.dto.Result;
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

@RestController
@RequestMapping("/api")
@Tag(name = "用戶認證", description = "用戶註冊、登入等身份驗證相關接口") // 👈 新增：分類標籤
public class ApiController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;

    // 構造函數注入依賴
    public ApiController(UserService userService,
                         AuthenticationManager authenticationManager,
                         UserRepository userRepository) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
    }

    /**
     * 註冊接口
     */
    @Operation(
            summary = "用戶註冊",
            description = "創建新的用戶帳號。系統會自動校驗用戶名、郵箱和手機號的唯一性。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "註冊成功"),
            @ApiResponse(responseCode = "400", description = "請求參數錯誤，或用戶名/郵箱/手機號已被註冊"),
            @ApiResponse(responseCode = "500", description = "系統內部錯誤")
    })
    @PostMapping("/register")
    public ResponseEntity<Result> register(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "註冊資訊", required = true)
            @RequestBody @Valid RegisterRequest request) {
        try {
            // 調用 UserService，返回 User 實體
            User user = userService.register(request);
            return ResponseEntity.ok(Result.ok("註冊成功"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest()
                    .body(Result.error(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Result.error("系統錯誤，請稍後重試"));
        }
    }

    /**
     * 登入接口
     */
    @Operation(
            summary = "用戶登入",
            description = "驗證用戶名和密碼。驗證成功後，會將認證資訊存入 SecurityContext (建立 Session)。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "登入成功"),
            @ApiResponse(responseCode = "400", description = "用戶名不存在 (USER_NOT_FOUND) 或 密碼錯誤 (INVALID_PASSWORD)"),
            @ApiResponse(responseCode = "500", description = "系統內部錯誤")
    })
    @PostMapping("/login")
    public ResponseEntity<Result> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "登入憑證", required = true)
            @RequestBody @Valid LoginRequest request) {
        try {
            // 1. 預先檢查用戶名是否存在，以便返回更精確的錯誤提示
            if (!userRepository.existsByUsername(request.getUsername())) {
                return ResponseEntity.badRequest().body(Result.error("USER_NOT_FOUND:該用戶名不存在"));
            }

            // 2. 用戶名存在，繼續驗證密碼 (Spring Security 會自動調用 CustomUserDetailsService)
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getUsername(),
                            request.getPassword()
                    )
            );

            // 3. 驗證成功：將認證資訊存入 SecurityContext
            SecurityContextHolder.getContext().setAuthentication(authentication);
            return ResponseEntity.ok(Result.ok("登入成功"));

        } catch (BadCredentialsException e) {
            // 密碼錯誤
            return ResponseEntity.badRequest().body(Result.error("INVALID_PASSWORD:輸入密碼不正確"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Result.error("系統錯誤，請稍後重試"));
        }
    }
}