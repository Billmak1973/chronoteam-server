package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.SecurityQuestion;
import org.example.website.entity.User;
import org.example.website.repository.SecurityQuestionRepository;
import org.example.website.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class PasswordController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    //  新增：注入安全問題 Repository
    private final SecurityQuestionRepository securityQuestionRepository;

    public PasswordController(UserRepository UsrRepository,
                              PasswordEncoder passwordEncoder,
                              SecurityQuestionRepository securityQuestionRepository) {
       this.userRepository = UsrRepository;
        this.passwordEncoder = passwordEncoder;
        this.securityQuestionRepository = securityQuestionRepository;
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse> changePassword(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {

        try {
            String username = authentication.getName();
            String newPassword = (String) request.get("newPassword");
            String verificationMethod = (String) request.get("verificationMethod");

            // 獲取當前用戶實體
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("用戶不存在"));

            boolean isVerified = false;

            // 驗證方式 1：使用當前密碼
            if ("password".equals(verificationMethod)) {
                String currentPassword = (String) request.get("currentPassword");

                if (currentPassword != null && passwordEncoder.matches(currentPassword, user.getPassword())) {
                    isVerified = true;
                } else {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("當前密碼不正確"));
                }
            }
            // 驗證方式 2：回答安全問題
            else if ("question".equals(verificationMethod)) {

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> questionAnswers = (List<Map<String, Object>>) request.get("questionAnswers");

                if (questionAnswers == null || questionAnswers.isEmpty()) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("請提供安全問題的答案"));
                }

                boolean allCorrect = true;
                for (Map<String, Object> qa : questionAnswers) {
                    Long questionId = Long.valueOf(qa.get("questionId").toString());
                    String userAnswer = (String) qa.get("answer");

                    if (userAnswer == null) {
                        allCorrect = false;
                        break;
                    }

                    // 從數據庫查詢該安全問題
                    SecurityQuestion sq = securityQuestionRepository.findById(questionId)
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
                            .body(ApiResponse.error("安全問題答案不正確，請重試"));
                }
            }
            else {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("無效的驗證方式"));
            }

            // 驗證通過，開始更新密碼
            if (isVerified) {
                // 檢查新密碼是否和舊密碼一樣
                if (passwordEncoder.matches(newPassword, user.getPassword())) {
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("新密碼不能與舊密碼相同"));
                }

                // 加密新密碼並保存
                user.setPassword(passwordEncoder.encode(newPassword));
                userRepository.save(user);

                return ResponseEntity.ok(ApiResponse.ok("密碼修改成功！請使用新密碼重新登入"));
            }

            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("身份驗證失敗"));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("系統錯誤：" + e.getMessage()));
        }
    }
}