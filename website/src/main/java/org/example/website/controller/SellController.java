package org.example.website.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.website.dto.ApiResponse;
import org.example.website.dto.SellApplicationDTO;
import org.example.website.entity.Customer;
import org.example.website.entity.SellApplication;
import org.example.website.repository.CustomerRepository;
import org.example.website.repository.SellApplicationRepository;
import org.example.website.service.FileStorageService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/sell")
public class SellController {

    private final SellApplicationRepository sellApplicationRepository;
    private final CustomerRepository customerRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper;

    public SellController(SellApplicationRepository sellApplicationRepository,
                          CustomerRepository customerRepository,
                          FileStorageService fileStorageService,
                          ObjectMapper objectMapper) {
        this.sellApplicationRepository = sellApplicationRepository;
        this.customerRepository = customerRepository;
        this.fileStorageService = fileStorageService;
        this.objectMapper = objectMapper;
    }

    /**
     * GET: 顯示發佈出售表單頁面
     */
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
    @PostMapping("/submit")
    @ResponseBody
    public ResponseEntity<ApiResponse> submitSellApplication(
            @ModelAttribute @Valid SellApplicationDTO dto,
            Authentication authentication) {

        try {
            // 1. 獲取當前用戶
            String username = authentication.getName();
            Customer customer = customerRepository.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("用戶不存在"));

            // 2. 創建出售申請實體
            SellApplication application = new SellApplication();
            application.setCustomer(customer);
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

            // 7. 返回成功響應
            Map<String, Object> data = new HashMap<>();
            data.put("sellId", saved.getId());
            data.put("message", "申請提交成功！我們的鑑定團隊將在 24 小時內聯繫您。");

            return ResponseEntity.ok(ApiResponse.okWithData("申請提交成功", data));

        } catch (IOException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("文件上傳失敗：" + e.getMessage()));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("系統錯誤，請稍後重試"));
        }
    }

    /**
     * GET: 我的出售申請列表（只顯示 BUYOUT 平台直接買斷）
     */
    @GetMapping("/my-applications")
    public String myApplications(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model,
            Authentication authentication) {

        String username = authentication.getName();

        //  【新增】查詢當前用戶信息並放入 Model，供側邊欄 (sidebar.html) 渲染使用
        Customer customer = customerRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("用戶不存在"));
        model.addAttribute("customer", customer);

        // 🟢 修改：只查詢 BUYOUT（平台直接買斷）模式的申請
        Page<SellApplication> applications = sellApplicationRepository
                .findByCustomer_UsernameAndTransactionModeOrderByCreatedAtDesc(
                        username,
                        SellApplication.TransactionMode.BUYOUT,  // 🟢 只查 BUYOUT
                        PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "createdAt"))
                );

        model.addAttribute("applications", applications);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", applications.getTotalPages());

        return "my-sell-applications"; // 對應 my-sell-applications.html
    }

    /**
     * API: 獲取出售申請詳情（AJAX 調用）
     */
    @GetMapping("/api/{id}")
    @ResponseBody
    public ResponseEntity<?> getApplicationDetail(
            @PathVariable Long id,
            Authentication authentication) {

        try {
            SellApplication application = sellApplicationRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("申請不存在"));

            // 權限檢查：只能查看自己的申請
            if (!application.getCustomer().getUsername().equals(authentication.getName())) {
                return ResponseEntity.status(403).body(ApiResponse.error("無權訪問"));
            }

            return ResponseEntity.ok(ApiResponse.okWithData("成功", application));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    // 在 SellController.java 中添加
    @PostMapping("/cancel/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse> cancelApplication(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            SellApplication application = sellApplicationRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("申請不存在"));

            // 權限檢查：只能取消自己的申請
            if (!application.getCustomer().getUsername().equals(authentication.getName())) {
                return ResponseEntity.status(403).body(ApiResponse.error("無權操作"));
            }

            // 只有 PENDING 狀態可以取消
            if (application.getStatus() != SellApplication.ApplicationStatus.PENDING) {
                return ResponseEntity.badRequest().body(ApiResponse.error("該申請已在處理中，無法取消"));
            }

            // 將狀態改為 CANCELLED (不直接刪除數據，保留記錄)
            application.setStatus(SellApplication.ApplicationStatus.CANCELLED);
            sellApplicationRepository.save(application);

            return ResponseEntity.ok(ApiResponse.ok("申請已取消"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("系統錯誤：" + e.getMessage()));
        }
    }

    /**
     * POST: 徹底刪除出售申請記錄（物理刪除，非取消）
     */
    @PostMapping("/delete/{id}")
    @ResponseBody
    public ResponseEntity<ApiResponse> deleteApplication(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            SellApplication application = sellApplicationRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("申請不存在"));

            // 權限檢查：只能刪除自己的申請
            if (!application.getCustomer().getUsername().equals(authentication.getName())) {
                return ResponseEntity.status(403).body(ApiResponse.error("無權操作"));
            }

            //  徹底刪除數據庫記錄
            sellApplicationRepository.deleteById(id);

            return ResponseEntity.ok(ApiResponse.ok("申請記錄已徹底刪除"));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(ApiResponse.error("系統錯誤：" + e.getMessage()));
        }
    }
}