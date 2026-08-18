package org.example.website.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
import org.example.website.entity.SecurityQuestion;
import org.example.website.entity.User;
import org.example.website.repository.SecurityQuestionRepository;
import org.example.website.repository.UserRepository;
import org.example.website.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/security")
@Tag(name = "帳戶安全設置", description = "用戶安全問答管理、安全等級評估等相關接口")
public class SecurityController {

    private final SecurityQuestionRepository sqRepository;
    private final UserService userService;
    private final UserRepository userRepository;

    public SecurityController(SecurityQuestionRepository sqRepository, UserService userService, UserRepository userRepository) {
        this.sqRepository = sqRepository;
        this.userService = userService;
        this.userRepository = userRepository;
    }

    /**
     * 1. 獲取當前用戶的安全問題列表 (絕對不能把答案返回給前端)
     */
    @Operation(
            summary = "獲取當前用戶的安全問題列表",
            description = "獲取當前登入用戶設置的所有安全問題。為保護隱私，返回的列表中答案字段已被後端強制脫敏處理（替換為 '******'）。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功，返回脫敏後的問題列表", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "401", description = "未登入或認證失效")
    })
    @GetMapping("/questions")
    public ResponseEntity<?> getMyQuestions(Authentication authentication) {
        String username = authentication.getName();
        List<SecurityQuestion> list = sqRepository.findByUser_UsernameOrderByCreatedAtDesc(username);

        // 脫敏處理：將答案替換為 ***，防止洩露
        list.forEach(q -> q.setAnswer("******"));

        return ResponseEntity.ok(Result.okWithData("獲取成功", list));
    }

    /**
     * 2. 保存/新增安全問題
     */
    @Operation(
            summary = "新增安全問題",
            description = "為當前用戶新增一條安全問答記錄。答案在後端會統一轉為小寫存儲，以支持後續驗證時不區分大小寫。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "安全問答設置成功", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "請求參數錯誤或設置失敗"),
            @ApiResponse(responseCode = "401", description = "未登入或認證失效")
    })
    @PostMapping("/question")
    public ResponseEntity<?> saveQuestion(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "請求體需包含 'question' (問題文本) 和 'answer' (答案文本) 兩個字段",
                    required = true
            )
            @RequestBody Map<String, String> body,
            Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("用戶不存在"));

            SecurityQuestion sq = new SecurityQuestion();
            sq.setUser(user); // 修改：設置 User 關聯
            sq.setQuestion(body.get("question"));
            // 將答案統一轉為小寫存儲，方便後續驗證時不區分大小寫
            sq.setAnswer(body.get("answer").trim().toLowerCase());

            sqRepository.save(sq);
            return ResponseEntity.ok(Result.ok("安全問答設置成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error("設置失敗: " + e.getMessage()));
        }
    }

    /**
     * 3. 新增：驗證原答案並更新答案
     */
    @Operation(
            summary = "修改安全問題答案",
            description = "驗證用戶提供的原答案是否正確，若正確則更新為新答案。新答案同樣會統一轉為小寫存儲。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "答案修改成功", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "原答案不正確或修改失敗"),
            @ApiResponse(responseCode = "401", description = "未登入或認證失效"),
            @ApiResponse(responseCode = "403", description = "無權修改此安全問題（非本人創建）")
    })
    @PutMapping("/question/{id}/update-answer")
    public ResponseEntity<?> updateAnswer(
            @Parameter(description = "要修改的安全問題記錄 ID", example = "1", required = true)
            @PathVariable Long id,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "請求體需包含 'oldAnswer' (原答案) 和 'newAnswer' (新答案) 兩個字段",
                    required = true
            )
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
            if (!sq.getUser().getUsername().equals(username)) {
                return ResponseEntity.status(403).body(Result.error("無權修改此安全問題"));
            }

            // 3. 驗證原答案是否正確 (不區分大小寫)
            if (!sq.getAnswer().equalsIgnoreCase(oldAnswer.trim())) {
                return ResponseEntity.badRequest()
                        .body(Result.error("原答案不正確，請重試"));
            }

            // 4. 原答案正確，更新為新答案 (同樣統一轉為小寫存儲)
            sq.setAnswer(newAnswer.trim().toLowerCase());
            sqRepository.save(sq);

            return ResponseEntity.ok(Result.ok("答案修改成功"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error("修改失敗: " + e.getMessage()));
        }
    }

    /**
     * 4. 獲取帳戶安全等級
     */
    @Operation(
            summary = "獲取帳戶安全等級",
            description = "根據用戶的郵箱綁定、手機綁定、密碼設置及安全問答設置情況，計算並返回 0-100 的安全等級分數及對應的文字描述（弱、中、強、極高）。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功，返回 level (分數) 和 levelText (描述)", content = @io.swagger.v3.oas.annotations.media.Content(schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "401", description = "未登入或認證失效")
    })
    @GetMapping("/security-level")
    public ResponseEntity<?> getSecurityLevel(Authentication authentication) {
        String username = authentication.getName();
        User user = userService.findByUsername(username);
        List<SecurityQuestion> questions = sqRepository.findByUser_UsernameOrderByCreatedAtDesc(username);

        // 计算安全等级（0-100）
        int securityLevel = 0;

        // 邮箱绑定（25分）
        if (user.getEmail() != null && !user.getEmail().isEmpty()) {
            securityLevel += 25;
        }

        // 手机绑定（25分）
        if (user.getPhone() != null && !user.getPhone().isEmpty()) {
            securityLevel += 25;
        }

        // 密码设置（25分）
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            securityLevel += 25;
        }

        // 安全问答（25分，至少设置1个问题）
        if (questions != null && !questions.isEmpty()) {
            securityLevel += 25;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("level", securityLevel);
        data.put("levelText", getSecurityLevelText(securityLevel));

        return ResponseEntity.ok(Result.okWithData("成功", data));
    }

    /**
     * 輔助方法：將分數轉換為文字描述 (私有方法，無需 Swagger 註解)
     */
    private String getSecurityLevelText(int level) {
        if (level >= 100) return "極高";
        if (level >= 75) return "強";
        if (level >= 50) return "中";
        return "弱";
    }
}