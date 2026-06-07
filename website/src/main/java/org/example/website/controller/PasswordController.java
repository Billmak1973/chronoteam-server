package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.Customer;
import org.example.website.entity.SecurityQuestion;
import org.example.website.repository.CustomerRepository;
import org.example.website.repository.SecurityQuestionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/account")
public class PasswordController {

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    // 🟢 新增：注入安全問題 Repository
    private final SecurityQuestionRepository securityQuestionRepository;

    public PasswordController(CustomerRepository customerRepository,
                              PasswordEncoder passwordEncoder,
                              SecurityQuestionRepository securityQuestionRepository) {
        this.customerRepository = customerRepository;
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

            // 🔍 添加日志
            System.out.println("🔐 修改密码请求 - 用户: " + username + ", 验证方式: " + verificationMethod);

            // 获取当前用户
            Customer customer = customerRepository.findById(username)
                    .orElseThrow(() -> new RuntimeException("用戶不存在"));

            boolean isVerified = false;

            // 🟢 验证方式 1：使用当前密码
            if ("password".equals(verificationMethod)) {
                String currentPassword = (String) request.get("currentPassword");
                System.out.println("🔑 使用密码验证");

                if (currentPassword != null && passwordEncoder.matches(currentPassword, customer.getPassword())) {
                    isVerified = true;
                    System.out.println("✅ 密码验证成功");
                } else {
                    System.out.println("❌ 密码验证失败");
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("當前密碼不正確"));
                }
            }
            // 🟢 验证方式 2：回答安全问题
            else if ("question".equals(verificationMethod)) {
                System.out.println("🛡️ 使用安全问题验证");

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> questionAnswers = (List<Map<String, Object>>) request.get("questionAnswers");

                System.out.println("📋 安全问题答案数量: " + (questionAnswers != null ? questionAnswers.size() : 0));

                if (questionAnswers == null || questionAnswers.isEmpty()) {
                    System.out.println("❌ 未提供安全问题答案");
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("請提供安全問題的答案"));
                }

                boolean allCorrect = true;
                for (Map<String, Object> qa : questionAnswers) {
                    // 前端传来的 questionId 可能是 Integer 或 Long，统一转为 Long 防止报错
                    Long questionId = Long.valueOf(qa.get("questionId").toString());
                    String userAnswer = (String) qa.get("answer");

                    System.out.println("🔍 验证问题ID: " + questionId + ", 用户答案: " + userAnswer);

                    if (userAnswer == null) {
                        allCorrect = false;
                        System.out.println("❌ 答案为空");
                        break;
                    }

                    // 从数据库查询该安全问题
                    SecurityQuestion sq = securityQuestionRepository.findById(questionId)
                            .orElseThrow(() -> {
                                System.out.println("❌ 安全问题不存在: " + questionId);
                                return new RuntimeException("安全問題不存在");
                            });

                    System.out.println("💾 数据库中的答案: " + sq.getAnswer());

                    // 验证答案（不区分大小写，并去除前后空格）
                    if (!sq.getAnswer().equalsIgnoreCase(userAnswer.trim())) {
                        allCorrect = false;
                        System.out.println("❌ 答案不匹配");
                        break; // 只要有一个答案错误，就验证失败
                    } else {
                        System.out.println("✅ 答案匹配成功");
                    }
                }

                if (allCorrect) {
                    isVerified = true;
                    System.out.println("✅ 安全问题验证成功");
                } else {
                    System.out.println("❌ 安全问题验证失败");
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("安全問題答案不正確，請重試"));
                }
            }
            else {
                System.out.println("❌ 无效的验证方式: " + verificationMethod);
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("無效的驗證方式"));
            }

            // 🟢 验证通过，开始更新密码
            if (isVerified) {
                // 可选：检查新密码是否和旧密码一样
                if (passwordEncoder.matches(newPassword, customer.getPassword())) {
                    System.out.println("❌ 新密码与旧密码相同");
                    return ResponseEntity.badRequest()
                            .body(ApiResponse.error("新密碼不能與舊密碼相同"));
                }

                // 加密新密码并保存
                System.out.println("💾 保存新密码...");
                customer.setPassword(passwordEncoder.encode(newPassword));
                customerRepository.save(customer);
                System.out.println("✅ 密码修改成功");

                return ResponseEntity.ok(ApiResponse.ok("密碼修改成功！請使用新密碼重新登入"));
            }

            System.out.println("❌ 身份验证失败");
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("身份驗證失敗"));

        } catch (Exception e) {
            System.err.println("❌ 系统错误: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("系統錯誤：" + e.getMessage()));
        }
    }
}