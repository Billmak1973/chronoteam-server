package org.example.website.controller;

import org.example.website.dto.ApiResponse;
import org.example.website.entity.Customer;
import org.example.website.entity.SecurityQuestion;
import org.example.website.repository.SecurityQuestionRepository;
import org.example.website.service.CustomerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/security")
public class SecurityController {

    private final SecurityQuestionRepository sqRepository;
    private final CustomerService customerService;
    public SecurityController(SecurityQuestionRepository sqRepository, CustomerService customerService) {
        this.sqRepository = sqRepository;
        this.customerService = customerService;
    }

    // 1. 獲取當前用戶的安全問題列表 ( 絕對不能把答案返回給前端)
    @GetMapping("/questions")
    public ResponseEntity<?> getMyQuestions(Authentication authentication) {
        String username = authentication.getName();
        List<SecurityQuestion> list = sqRepository.findByCustomer_UsernameOrderByCreatedAtDesc(username);

        // 脫敏處理：將答案替換為 ***，防止洩露
        list.forEach(q -> q.setAnswer("******"));

        return ResponseEntity.ok(ApiResponse.okWithData("獲取成功", list));
    }

    // 2. 保存/新增安全問題
    @PostMapping("/question")
    public ResponseEntity<?> saveQuestion(@RequestBody Map<String, String> body, Authentication authentication) {
        try {
            String username = authentication.getName();
            Customer customer = customerService.findByUsername(username);

            SecurityQuestion sq = new SecurityQuestion();
            sq.setCustomer(customer);
            sq.setQuestion(body.get("question"));
            // 將答案統一轉為小寫存儲，方便後續驗證時不區分大小寫
            sq.setAnswer(body.get("answer").trim().toLowerCase());

            sqRepository.save(sq);
            return ResponseEntity.ok(ApiResponse.ok("安全問答設置成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("設置失敗: " + e.getMessage()));
        }
    }

    // 3.  新增：驗證原答案並更新答案
    @PutMapping("/question/{id}/update-answer")
    public ResponseEntity<?> updateAnswer(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            String oldAnswer = body.get("oldAnswer");
            String newAnswer = body.get("newAnswer");

            // 1. 查找該安全問題
            SecurityQuestion sq = sqRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("安全問題不存在"));

            // 2. 權限驗證：確保該問題屬於當前登入的用戶
            if (!sq.getCustomer().getUsername().equals(username)) {
                return ResponseEntity.status(403).body(ApiResponse.error("無權修改此安全問題"));
            }

            // 3. 驗證原答案是否正確 (不區分大小寫)
            if (!sq.getAnswer().equalsIgnoreCase(oldAnswer.trim())) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("原答案不正確，請重試"));
            }

            // 4. 原答案正確，更新為新答案 (同樣統一轉為小寫存儲)
            sq.setAnswer(newAnswer.trim().toLowerCase());
            sqRepository.save(sq);

            return ResponseEntity.ok(ApiResponse.ok("答案修改成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("修改失敗: " + e.getMessage()));
        }
    }

    @GetMapping("/security-level")
    public ResponseEntity<?> getSecurityLevel(Authentication authentication) {
        String username = authentication.getName();
        Customer customer = customerService.findByUsername(username);
        List<SecurityQuestion> questions = sqRepository.findByCustomer_UsernameOrderByCreatedAtDesc(username);

        // 计算安全等级（0-100）
        int securityLevel = 0;

        // 邮箱绑定（25分）
        if (customer.getEmail() != null && !customer.getEmail().isEmpty()) {
            securityLevel += 25;
        }

        // 手机绑定（25分）
        if (customer.getPhone() != null && !customer.getPhone().isEmpty()) {
            securityLevel += 25;
        }

        // 密码设置（25分）
        if (customer.getPassword() != null && !customer.getPassword().isEmpty()) {
            securityLevel += 25;
        }

        // 安全问答（25分，至少设置1个问题）
        if (questions != null && !questions.isEmpty()) {
            securityLevel += 25;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("level", securityLevel);
        data.put("levelText", getSecurityLevelText(securityLevel));

        return ResponseEntity.ok(ApiResponse.okWithData("成功", data));
    }

    private String getSecurityLevelText(int level) {
        if (level >= 100) return "極高";
        if (level >= 75) return "強";
        if (level >= 50) return "中";
        return "弱";
    }
}