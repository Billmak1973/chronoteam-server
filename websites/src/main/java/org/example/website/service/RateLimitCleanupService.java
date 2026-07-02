package org.example.website.service;

import lombok.RequiredArgsConstructor;
import org.example.website.entity.RateLimitLog;
import org.example.website.entity.RateLimitLogHistory;
import org.example.website.repository.RateLimitLogHistoryRepository;
import org.example.website.repository.RateLimitLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RateLimitCleanupService {

    private final RateLimitLogRepository rateLimitRepository;
    private final RateLimitLogHistoryRepository historyRepository;

    // 使用 ScheduledExecutorService 安排延遲任務
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    /**
     * 安排一個延遲任務，在封禁結束時轉移記錄
     *
     * @param recordId 記錄ID
     * @param bannedUntil 封禁結束時間
     */
    public void scheduleUnbanTask(Long recordId, LocalDateTime bannedUntil) {
        LocalDateTime now = LocalDateTime.now();
        long delayMillis = Duration.between(now, bannedUntil).toMillis();

        if (delayMillis <= 0) {
            // 如果已經過期，立即執行
            System.out.println("封禁已過期，立即執行轉移，記錄ID: " + recordId);
            moveRecordToHistory(recordId);
            return;
        }

        // 安排延遲任務
        scheduler.schedule(() -> {
            try {
                System.out.println("開始執行封禁記錄轉移，記錄ID: " + recordId);
                moveRecordToHistory(recordId);
            } catch (Exception e) {
                System.err.println("轉移封禁記錄失敗，記錄ID: " + recordId +
                        "，錯誤: " + e.getMessage());
            }
        }, delayMillis, TimeUnit.MILLISECONDS);

        long delaySeconds = delayMillis / 1000;
        System.out.println("✓ 已安排封禁記錄轉移任務，記錄ID: " + recordId +
                "，將在 " + bannedUntil + " 執行（延遲 " + delaySeconds + " 秒）");
    }

    /**
     * 將指定記錄轉移到歷史表
     *
     * @param recordId 記錄ID
     */
    @Transactional
    public void moveRecordToHistory(Long recordId) {
        Optional<RateLimitLog> recordOpt = rateLimitRepository.findById(recordId);

        if (recordOpt.isEmpty()) {
            System.out.println("記錄 ID " + recordId + " 已不存在，可能已被轉移");
            return;
        }

        RateLimitLog record = recordOpt.get();

        // 創建歷史記錄
        RateLimitLogHistory history = new RateLimitLogHistory();

        // 直接關聯 User 對象，不再是 setUsername
        history.setUser(record.getUser());

        history.setActionTime(record.getActionTime());
        history.setTimes(record.getTimes());
        history.setUpdatedAt(record.getUpdatedAt());
        history.setBannedUntil(record.getBannedUntil());
        history.setUnbannedAt(LocalDateTime.now());  // 記錄實際解封時間

        // 保存到歷史表
        historyRepository.save(history);

        // 從原表刪除
        rateLimitRepository.delete(record);

        // 日誌輸出時，透過 User 對象獲取 username
        System.out.println("✓ 用戶 " + record.getUser().getUsername() +
                " 的封禁記錄已轉移到歷史表（封禁結束時間: " + record.getBannedUntil() + "）");
    }
}
