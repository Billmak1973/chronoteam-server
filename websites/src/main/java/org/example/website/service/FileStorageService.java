package org.example.website.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService {

    @Value("${upload.path:/tmp/chronoteam/uploads}")
    private String uploadPath;

    @Value("${upload.max-size:10485760}") // 10MB
    private long maxSize;

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(Paths.get(uploadPath));
        } catch (IOException e) {
            throw new RuntimeException("無法創建上傳目錄", e);
        }
    }

    /**
     * 單文件上傳
     */
    public String uploadFile(MultipartFile file, String subDir) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        // 驗證文件大小
        if (file.getSize() > maxSize) {
            throw new IOException("文件大小超過限制（最大10MB）");
        }

        // 驗證文件類型
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IOException("只允許上傳圖片文件");
        }

        // 生成唯一文件名
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = UUID.randomUUID().toString() + "_" + timestamp + extension;

        // 創建子目錄
        Path subPath = Paths.get(uploadPath, subDir);
        Files.createDirectories(subPath);

        // 保存文件
        Path filePath = subPath.resolve(filename);
        file.transferTo(filePath);

        // 返回相對路徑（用於數據庫存儲）
        return "/uploads/" + subDir + "/" + filename;
    }

    /**
     * 批量上傳圖片
     */
    public Map<String, String> uploadMultipleImages(Map<String, MultipartFile> files, String sellId) throws IOException {
        Map<String, String> imagePaths = new HashMap<>();

        for (Map.Entry<String, MultipartFile> entry : files.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                String path = uploadFile(entry.getValue(), "sell/" + sellId);
                imagePaths.put(entry.getKey(), path);
            }
        }

        return imagePaths;
    }

    /**
     * 刪除文件
     */
    public void deleteFile(String filePath) {
        if (filePath != null && !filePath.isEmpty()) {
            try {
                Path path = Paths.get(uploadPath, filePath.replace("/uploads/", ""));
                Files.deleteIfExists(path);
            } catch (IOException e) {
                // 日誌記錄，但不拋出異常
                System.err.println("刪除文件失敗: " + filePath);
            }
        }
    }
}