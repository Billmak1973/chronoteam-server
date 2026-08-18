package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.example.website.dto.ChangePasswordRequest;
import org.example.website.dto.Result;
import org.example.website.entity.SecurityQuestion;
import org.example.website.entity.User;
import org.example.website.repository.SecurityQuestionRepository;
import org.example.website.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
@Tag(name = "帳戶與安全", description = "用戶帳戶信息、密碼修改及安全設置相關接口")
public class PasswordController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityQuestionRepository securityQuestionRepository;

    public PasswordController(UserRepository userRepository,
                              PasswordEncoder passwordEncoder,
                              SecurityQuestionRepository securityQuestionRepository) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityQuestionRepository = securityQuestionRepository;
    }

    @Operation(
            summary = "修改登入密碼",
            description = "支持兩種驗證方式：\n" +
                    "1. **password**: 輸入當前密碼進行驗證。\n" +
                    "2. **question**: 回答預先設置的安全問題進行驗證。\n" +
                    "驗證通過後，系統將加密並更新您的新密碼。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "密碼修改成功"),
            @ApiResponse(responseCode = "400", description = "驗證失敗、新舊密碼相同、或請求參數格式錯誤"),
            @ApiResponse(responseCode = "401", description = "未登入或認證失效"),
            @ApiResponse(responseCode = "500", description = "系統內部錯誤")
    })
    @PostMapping("/change-password")
    public ResponseEntity<Result> changePassword(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "修改密碼的請求數據",
                    required = true
            )
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        try {
            String username = authentication.getName();
            String newPassword = request.getNewPassword();
            String verificationMethod = request.getVerificationMethod();

            // 獲取當前用戶實體
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("用戶不存在"));

            boolean isVerified = false;

            // 驗證方式 1：使用當前密碼
            if ("password".equals(verificationMethod)) {
                String currentPassword = request.getCurrentPassword();

                if (currentPassword != null && passwordEncoder.matches(currentPassword, user.getPassword())) {
                    isVerified = true;
                } else {
                    return ResponseEntity.badRequest()
                            .body(Result.error("當前密碼不正確"));
                }
            }
            // 驗證方式 2：回答安全問題
            else if ("question".equals(verificationMethod)) {
                if (request.getQuestionAnswers() == null || request.getQuestionAnswers().isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(Result.error("請提供安全問題的答案"));
                }

                boolean allCorrect = true;
                for (ChangePasswordRequest.QuestionAnswerDTO qa : request.getQuestionAnswers()) {
                    String userAnswer = qa.getAnswer();
                    if (userAnswer == null) {
                        allCorrect = false;
                        break;
                    }

                    // 從數據庫查詢該安全問題
                    SecurityQuestion sq = securityQuestionRepository.findById(qa.getQuestionId())
                            .orElseThrow(() -> new RuntimeException("安全問題不存在"));

                    // 驗證答案（不區分大小寫，並去除前後空格）
                    if (!sq.getAnswer().equalsIgnoreCase(userAnswer.trim())) {
                        allCorrect = false;
                        break;
                    }
                }

                if (allCorrect) {
                    isVerified = true;
                } else {
                    return ResponseEntity.badRequest()
                            .body(Result.error("安全問題答案不正確，請重試"));
                }
            } else {
                return ResponseEntity.badRequest()
                        .body(Result.error("無效的驗證方式，必須為 'password' 或 'question'"));
            }

            // 驗證通過，開始更新密碼
            if (isVerified) {
                // 檢查新密碼是否和舊密碼一樣
                if (passwordEncoder.matches(newPassword, user.getPassword())) {
                    return ResponseEntity.badRequest()
                            .body(Result.error("新密碼不能與舊密碼相同"));
                }

                // 加密新密碼並保存
                user.setPassword(passwordEncoder.encode(newPassword));
                userRepository.save(user);

                return ResponseEntity.ok(Result.ok("密碼修改成功！請使用新密碼重新登入"));
            }

            return ResponseEntity.badRequest()
                    .body(Result.error("身份驗證失敗"));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Result.error("系統錯誤：" + e.getMessage()));
        }
    }
}