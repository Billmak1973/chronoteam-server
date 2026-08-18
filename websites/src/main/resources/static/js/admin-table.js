// @ts-check
/** admin-table.js
 *  通用後台表格管理器 (AdminTable)
 * 消滅所有重複的 loadXXXData 和 renderPagination 函數
 *
 * 【修復】：恢復遍歷 smartPages 邏輯，正確顯示所有頁碼 (1, 2, 3...)
 */
const AdminTable = {

    /**
     * 1. 渲染分頁 (通用 - 修正版：適配 1-based 頁碼)
     * @param {string} containerId - 分頁容器 ID
     * @param {number} currentPage - 當前頁 (1-based, 例如 1, 2, 3...)
     * @param {number} totalPages - 總頁數
     * @param {Array} smartPages - 智能分頁數組 (包含 {pageNumber: 1, isEllipsis: false}, ...)
     * @param {number} totalElements - 總記錄數
     * @param {Function} onPageChange - 翻頁回調 (注意：回調傳遞的依然是 1-based 頁碼)
     */
    renderPagination: function(containerId, currentPage, totalPages, smartPages, totalElements, onPageChange) {
        const container = document.getElementById(containerId);
        if (!container) return;

        // 如果沒有數據，清空容器
        if (!totalElements || totalElements <= 0) {
            container.innerHTML = '';
            return;
        }

        let html = '<div class="pagination-wrapper">';

        // 1. 上一頁按鈕 (修正：1-based 邏輯，第1頁時 currentPage=1，不應顯示可點擊的上一頁)
        if (currentPage > 1) {
            html += `<button type="button" class="page-btn" data-page="${currentPage - 1}">
                    <i class="fas fa-chevron-left"></i> 上一頁
                 </button>`;
        } else {
            // 第1頁時，顯示灰色的上一頁
            html += `<button type="button" class="page-btn disabled" disabled>
                    <i class="fas fa-chevron-left"></i> 上一頁
                 </button>`;
        }

        // 2. 遍歷 smartPages 數組渲染頁碼
        if (smartPages && smartPages.length > 0) {
            smartPages.forEach(item => {
                if (item.isEllipsis) {
                    html += `<span class="page-ellipsis">...</span>`;
                } else {
                    // 修正：後端 pageNumber 是 1-based，前端 currentPage 也是 1-based，直接比較！
                    const isActive = item.pageNumber === currentPage ? 'active' : '';

                    // 修正：data-page 直接存 1-based 的頁碼，不需要減 1
                    html += `<button type="button" class="page-btn ${isActive}" data-page="${item.pageNumber}">
                            ${item.pageNumber}
                         </button>`;
                }
            });
        } else {
            // 兜底邏輯
            html += `<span class="page-btn active">${currentPage}</span>`;
        }

        // 3. 下一頁按鈕 (修正：1-based 邏輯)
        if (currentPage < totalPages) {
            html += `<button type="button" class="page-btn" data-page="${currentPage + 1}">
                    下一頁 <i class="fas fa-chevron-right"></i>
                 </button>`;
        } else {
            // 最後一頁時，顯示灰色的下一頁
            html += `<button type="button" class="page-btn disabled" disabled>
                    下一頁 <i class="fas fa-chevron-right"></i>
                 </button>`;
        }

        html += '</div>';
        container.innerHTML = html;

        // 事件委託：綁定翻頁點擊事件
        container.querySelectorAll('.page-btn:not(.disabled)').forEach(btn => {
            btn.addEventListener('click', function() {
                const newPage = parseInt(this.getAttribute('data-page'));
                if (!isNaN(newPage) && onPageChange) {
                    // 傳遞 1-based 頁碼給回調
                    onPageChange(newPage);
                }
            });
        });
    },

    /**
     * 2. 核心：加載數據並渲染 (通用)
     * @param {Object} config - 配置對象
     */
    loadData: async function(config) {
        const {
            apiUrl,         // API 路徑
            params,         // 請求參數對象
            tbodySelector,  // tbody 的選擇器
            paginationId,   // 分頁容器的 ID
            renderRow,      // 行渲染回調函數
            emptyText,      // 空數據提示語
            onSuccess       // 成功後的回調
        } = config;

        const tbody = document.querySelector(tbodySelector);
        if (!tbody) return console.error(`找不到 tbody: ${tbodySelector}`);

        const table = tbody.closest('table');
        const colSpan = table ? table.querySelectorAll('thead th').length : 10;

        // 1. 顯示 Loading
        tbody.innerHTML = `<tr><td colspan="${colSpan}" style="text-align:center; padding: 3rem; color: var(--gray);"><i class="fas fa-circle-notch fa-spin fa-2x" style="color: var(--gold);"></i><p style="margin-top:1rem;">加載數據中...</p></td></tr>`;

        try {
            const queryString = new URLSearchParams(params).toString();
            const response = await fetch(`${apiUrl}?${queryString}`);
            const data = await response.json();

            if (data.content !== undefined) {
                if (data.content.length > 0) {
                    // 2. 渲染表格行
                    tbody.innerHTML = data.content.map(renderRow).join('');
                } else {
                    tbody.innerHTML = `<tr><td colspan="${colSpan}" style="text-align:center; padding: 3rem; color: var(--gray);"><i class="fas fa-inbox fa-2x" style="color: #e9ecef;"></i><p style="margin-top:1rem;">${emptyText || '暫無數據'}</p></td></tr>`;
                }

                // 3. 渲染分頁
                this.renderPagination(
                    paginationId,
                    data.currentPage,
                    data.totalPages,
                    data.smartPages, // 【關鍵】確保後端返回了這個數組
                    data.totalElements,
                    (newPage) => {
                        params.page = newPage;
                        this.loadData(config);
                    }
                );

                if (typeof bindDynamicEvents === 'function') bindDynamicEvents();
                if (onSuccess) onSuccess(data);
            } else {
                throw new Error(data.message || '數據格式錯誤');
            }
        } catch (error) {
            console.error('加載數據失敗:', error);
            tbody.innerHTML = `<tr><td colspan="${colSpan}" style="text-align:center; padding: 2rem; color: #ef4444;"><i class="fas fa-exclamation-triangle fa-2x"></i><p style="margin-top:1rem;">加載失敗，請檢查網絡或後端接口</p></td></tr>`;

            const paginationContainer = document.getElementById(paginationId);
            if (paginationContainer) paginationContainer.innerHTML = '';

            if (typeof showNotification === 'function') showNotification('❌ 網絡錯誤或接口異常', true);
        }
    }
};