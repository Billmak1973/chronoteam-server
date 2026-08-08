package org.example.website.util;

import org.springframework.data.domain.Page;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PaginationUtils {

    public static class PageItem {
        private boolean isEllipsis;
        private Integer pageNumber;

        public PageItem(boolean isEllipsis, Integer pageNumber) {
            this.isEllipsis = isEllipsis;
            this.pageNumber = pageNumber;
        }
        public boolean isEllipsis() { return isEllipsis; }
        public Integer getPageNumber() { return pageNumber; }
    }

    /**
     * 核心方法：構建標準的分頁響應 Map
     * @param page 分頁對象
     * @param cleanData 清洗後的列表數據
     * @param extraData 額外數據。注意：這裡的 Map 的 Key 將作為 JSON 的根級別字段名，Value 將作為該字段的值。
     *                  例如：傳入 Map.of("appealStatusMap", myMap)，結果 JSON 會有 "appealStatusMap": {...}
     *                  如果你想平鋪數據，請確保 Value 是基本類型且 Key 是 String。
     */
    public static Map<String, Object> buildPageResponse(Page<?> page, List<?> cleanData, Map<String, Object> extraData) {
        Map<String, Object> response = new HashMap<>();

        response.put("content", cleanData != null ? cleanData : page.getContent());
        response.put("currentPage", page.getNumber());
        response.put("totalPages", page.getTotalPages());
        response.put("totalElements", page.getTotalElements());
        response.put("smartPages", generateSmartPagination(page.getNumber(), page.getTotalPages()));

        // 2. 合併額外數據
        // 這裡直接使用 putAll 是安全的，因為參數限制為 Map<String, Object>
        // 這意味著 Controller 必須傳入 Map<String, Object>，其中 Key 是字段名 (如 "appealStatusMap")
        if (extraData != null && !extraData.isEmpty()) {
            response.putAll(extraData);
        }

        return response;
    }

    // 重載方法
    public static Map<String, Object> buildPageResponse(Page<?> page, List<?> cleanData) {
        return buildPageResponse(page, cleanData, null);
    }

    public static List<PageItem> generateSmartPagination(int currentPage, int totalPages) {
        // ... (保持原有邏輯不變) ...
        List<PageItem> pages = new ArrayList<>();
        if (totalPages <= 0) {
            pages.add(new PageItem(false, 1));
            return pages;
        }
        if (totalPages <= 7) {
            for (int i = 1; i <= totalPages; i++) pages.add(new PageItem(false, i));
            return pages;
        }

        pages.add(new PageItem(false, 1));
        int current1Based = currentPage + 1;

        if (current1Based <= 3) {
            for (int i = 2; i <= 4; i++) pages.add(new PageItem(false, i));
            pages.add(new PageItem(true, null));
        } else if (current1Based >= totalPages - 2) {
            pages.add(new PageItem(true, null));
            for (int i = totalPages - 3; i <= totalPages - 1; i++) pages.add(new PageItem(false, i));
        } else {
            pages.add(new PageItem(true, null));
            pages.add(new PageItem(false, current1Based - 1));
            pages.add(new PageItem(false, current1Based));
            pages.add(new PageItem(false, current1Based + 1));
            pages.add(new PageItem(true, null));
        }
        pages.add(new PageItem(false, totalPages));
        return pages;
    }
}