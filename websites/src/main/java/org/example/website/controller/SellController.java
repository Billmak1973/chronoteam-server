package org.example.website.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.example.website.dto.Result;
import org.example.website.dto.SellApplicationDTO;
import org.example.website.entity.SellApplication;
import org.example.website.entity.User;
import org.example.website.repository.SellApplicationRepository;
import org.example.website.repository.UserRepository;
import org.example.website.service.FileStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/sell")
@Tag(name = "出售申請管理", description = "用戶發佈出售申請、查看申請記錄及取消/刪除申請相關接口")
public class SellController {

    private final SellApplicationRepository sellApplicationRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public SellController(SellApplicationRepository sellApplicationRepository,
                          FileStorageService fileStorageService,
                          ObjectMapper objectMapper,
                          UserRepository userRepository) {
        this.sellApplicationRepository = sellApplicationRepository;
        this.userRepository = userRepository;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    /**
     * GET: 顯示發佈出售表單頁面
     * 使用 @Hidden 隱藏此接口，因為 Swagger 專注於 REST API，不需要展示頁面渲染路由
     */
    @Hidden
    @GetMapping
    public String showSellForm(Model model, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return "redirect:/"; // 未登入跳轉首頁
        }
        return "sell"; // 返回 sell.html
    }

    /**
     * POST: 提交出售申請（處理圖片上傳）
     */
    @Operation(
            summary = "提交出售申請",
            description = "用戶填寫手錶基礎資訊、成色狀況並上傳多張圖片，提交出售申請。系統將自動處理圖片上傳並將路徑轉為 JSON 存儲。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "申請提交成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "表單驗證失敗或文件上傳失敗"),
            @ApiResponse(responseCode = "401", description = "未登入"),
            @ApiResponse(responseCode = "500", description = "系統內部錯誤")
    })
    @PostMapping("/submit")
    @ResponseBody
    public ResponseEntity<Result> submitSellApplication(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "出售申請表單數據及圖片文件 (multipart/form-data)",
                    required = true
            )
            @ModelAttribute @Valid SellApplicationDTO dto,
            Authentication authentication) {

        try {
            // 1. 獲取當前用戶 (核心修改：使用 UserRepository 獲取 User 實體)
            String username = authentication.getName();
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("用戶不存在"));

            // 2. 創建出售申請實體
            SellApplication application = new SellApplication();
            application.setUser(user); // 核心修改：設置 User 關聯，不再是 setCustomer
            application.setBrand(dto.getBrand());
            application.setSeries(dto.getSeries());
            application.setModel(dto.getModel());
            application.setPurchaseYear(dto.getPurchaseYear());
            application.setCondition(dto.getCondition());
            application.setFunctionStatus(dto.getFunctionStatus());
            application.setNotes(dto.getNotes());

            // 3. 處理附件列表（轉為 JSON）
            if (dto.getAccessories() != null) {
                application.setAccessories(objectMapper.writeValueAsString(dto.getAccessories()));
            }

            // 4. 處理交易模式
            application.setTransactionMode(SellApplication.TransactionMode.valueOf(dto.getTransactionMode()));

            // 5. 上傳圖片
            Map<String, MultipartFile> imageFiles = new HashMap<>();
            if (dto.getImgDial() != null && !dto.getImgDial().isEmpty())
                imageFiles.put("dial", dto.getImgDial());
            if (dto.getImgCaseback() != null && !dto.getImgCaseback().isEmpty())
                imageFiles.put("caseback", dto.getImgCaseback());
            if (dto.getImgCard() != null && !dto.getImgCard().isEmpty())
                imageFiles.put("card", dto.getImgCard());
            if (dto.getImgFlaws() != null && !dto.getImgFlaws().isEmpty())
                imageFiles.put("flaws", dto.getImgFlaws());
            if (dto.getImgExtra() != null && !dto.getImgExtra().isEmpty())
                imageFiles.put("extra", dto.getImgExtra());

            // 生成臨時 ID（用於文件夾命名）
            String tempId = System.currentTimeMillis() + "_" + username;
            Map<String, String> imagePaths = fileStorageService.uploadMultipleImages(imageFiles, tempId);

            // 將圖片路徑轉為 JSON 存儲
            application.setImagePaths(objectMapper.writeValueAsString(imagePaths));

            // 6. 保存到數據庫
            SellApplication saved = sellApplicationRepository.save(application);

            // 7. 返回成功響應 (核心修改：主鍵 getter 從 getId() 改為 getApplicationId())
            Map<String, Object> data = new HashMap<>();
            data.put("sellId", saved.getApplicationId());
            data.put("message", "申請提交成功！我們的鑑定團隊將在 24 小時內聯繫您。");

            return ResponseEntity.ok(Result.okWithData("申請提交成功", data));

        } catch (IOException e) {
            return ResponseEntity.badRequest()
                    .body(Result.error("文件上傳失敗：" + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(Result.error("系統錯誤，請稍後重試"));
        }
    }

    /**
     * GET: 我的出售申請列表（只顯示 BUYOUT 平台直接買斷）
     * 使用 @Hidden 隱藏此接口，保持 Swagger 文檔純淨
     */
    @Hidden
    @GetMapping("/my-applications")
    public String myApplications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model,
            Authentication authentication) {

        String username = authentication.getName();

        // 核心修改：使用 UserRepository 獲取 User 實體，並放入 Model 供側邊欄渲染
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));
        model.addAttribute("user", user);

        // 只查詢 BUYOUT（平台直接買斷）模式的申請
        Page<SellApplication> applications = sellApplicationRepository
                .findByUser_UsernameAndTransactionModeOrderByCreatedAtDesc(
                        username,
                        SellApplication.TransactionMode.BUYOUT,
                        PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"))
                );

        model.addAttribute("applications", applications);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", applications.getTotalPages());

        return "my-sell-applications";
    }

    /**
     * API: 獲取出售申請詳情（AJAX 調用）
     */
    @Operation(
            summary = "獲取出售申請詳情",
            description = "根據申請 ID 獲取出售申請的詳細資訊，包含圖片路徑與狀態。僅限申請人本人查看。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "獲取成功", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "403", description = "無權訪問該申請"),
            @ApiResponse(responseCode = "404", description = "申請不存在")
    })
    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<?> getApplicationDetail(
            @Parameter(description = "出售申請的唯一 ID", example = "1", required = true)
            @PathVariable Long id,
            Authentication authentication) {

        try {
            SellApplication application = sellApplicationRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("申請不存在"));

            // 權限檢查：只能查看自己的申請
            if (!application.getUser().getUsername().equals(authentication.getName())) {
                return ResponseEntity.status(403).body(Result.error("無權訪問"));
            }

            return ResponseEntity.ok(Result.okWithData("成功", application));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Result.error(e.getMessage()));
        }
    }

    /**
     * POST: 取消出售申請
     */
    @Operation(
            summary = "取消出售申請",
            description = "將狀態為 PENDING (待處理) 的出售申請更改為 CANCELLED (已取消)。僅限申請人本人操作，不物理刪除數據。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "申請已取消", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "400", description = "申請已在處理中，無法取消"),
            @ApiResponse(responseCode = "403", description = "無權操作"),
            @ApiResponse(responseCode = "404", description = "申請不存在")
    })
    @PostMapping("/cancel/{id}")
    @ResponseBody
    public ResponseEntity<Result> cancelApplication(
            @Parameter(description = "要取消的出售申請 ID", example = "1", required = true)
            @PathVariable Long id,
            Authentication authentication) {
        try {
            SellApplication application = sellApplicationRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("申請不存在"));

            // 權限檢查：只能取消自己的申請
            if (!application.getUser().getUsername().equals(authentication.getName())) {
                return ResponseEntity.status(403).body(Result.error("無權操作"));
            }

            // 只有 PENDING 狀態可以取消
            if (application.getStatus() != SellApplication.ApplicationStatus.PENDING) {
                return ResponseEntity.badRequest().body(Result.error("該申請已在處理中，無法取消"));
            }

            // 將狀態改為 CANCELLED (不直接刪除數據，保留記錄)
            application.setStatus(SellApplication.ApplicationStatus.CANCELLED);
            sellApplicationRepository.save(application);

            return ResponseEntity.ok(Result.ok("申請已取消"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Result.error("系統錯誤：" + e.getMessage()));
        }
    }

    /**
     * POST: 徹底刪除出售申請記錄（物理刪除，非取消）
     */
    @Operation(
            summary = "徹底刪除出售申請記錄",
            description = "物理刪除出售申請的數據庫記錄。僅限申請人本人操作，此操作不可逆，請謹慎使用。"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "申請記錄已徹底刪除", content = @Content(schema = @Schema(implementation = Result.class))),
            @ApiResponse(responseCode = "403", description = "無權操作"),
            @ApiResponse(responseCode = "404", description = "申請不存在")
    })
    @PostMapping("/delete/{id}")
    @ResponseBody
    public ResponseEntity<Result> deleteApplication(
            @Parameter(description = "要刪除的出售申請 ID", example = "1", required = true)
            @PathVariable Long id,
            Authentication authentication) {
        try {
            SellApplication application = sellApplicationRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("申請不存在"));

            // 權限檢查：只能刪除自己的申請
            if (!application.getUser().getUsername().equals(authentication.getName())) {
                return ResponseEntity.status(403).body(Result.error("無權操作"));
            }

            // 徹底刪除數據庫記錄
            sellApplicationRepository.deleteById(id);

            return ResponseEntity.ok(Result.ok("申請記錄已徹底刪除"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Result.error("系統錯誤：" + e.getMessage()));
        }
    }
}