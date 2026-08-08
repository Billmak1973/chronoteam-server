/** admin-table.js
 * 🚀 通用後台表格管理器 (AdminTable)
 * 消滅所有重複的 loadXXXData 和 renderPagination 函數
 */
const AdminTable = {
    /**
     * 1. 渲染分頁 (通用)
     */
    renderPagination: function(containerId, currentPage, totalPages, smartPages, onPageChange) {
        const container = document.getElementById(containerId);
        if (!container) return;

        if (!smartPages || smartPages.length === 0 || totalPages === 0) {
            container.innerHTML = '';
            return;
        }

        let html = '<div class="pagination-wrapper">';
        // 上一頁
        if (currentPage > 0) {
            html += `<button class="page-btn" data-page="${currentPage - 1}"><i class="fas fa-chevron-left"></i> 上一頁</button>`;
        } else {
            html += `<button class="page-btn disabled" disabled><i class="fas fa-chevron-left"></i> 上一頁</button>`;
        }

        // 頁碼遍歷
        smartPages.forEach(item => {
            if (item.isEllipsis) {
                html += `<span class="page-ellipsis">...</span>`;
            } else {
                const pageIndex = item.pageNumber - 1; // 後端 1-based，前端 0-based
                const isActive = pageIndex === currentPage ? 'active' : '';
                html += `<button class="page-btn ${isActive}" data-page="${pageIndex}">${item.pageNumber}</button>`;
            }
        });

        // 下一頁
        if (currentPage < totalPages - 1) {
            html += `<button class="page-btn" data-page="${currentPage + 1}">下一頁 <i class="fas fa-chevron-right"></i></button>`;
        } else {
            html += `<button class="page-btn disabled" disabled>下一頁 <i class="fas fa-chevron-right"></i></button>`;
        }
        html += '</div>';
        container.innerHTML = html;

        // 事件委託：綁定翻頁點擊事件
        container.querySelectorAll('.page-btn:not(.disabled)').forEach(btn => {
            btn.addEventListener('click', function() {
                const newPage = parseInt(this.getAttribute('data-page'));
                if (onPageChange) onPageChange(newPage);
            });
        });
    },

    /**
     * 2. 核心：加載數據並渲染 (通用)
     * @param {Object} config - 配置對象
     */
    loadData: async function(config) {
        const {
            apiUrl,         // API 路徑 (如 '/admin/api/customers/list')
            params,         // 請求參數對象 (如 {page: 0, size: 25, keyword: 'test'})
            tbodySelector,  // tbody 的選擇器 (如 '#customersTableBody')
            paginationId,   // 分頁容器的 ID (如 'customers-pagination')
            renderRow,      // 行渲染回調函數 (接收單條數據，返回 HTML 字符串)
            emptyText,      // 空數據提示語
            onSuccess       // 成功後的回調 (可選)
        } = config;

        const tbody = document.querySelector(tbodySelector);
        if (!tbody) return console.error(`找不到 tbody: ${tbodySelector}`);

        // 計算 colSpan 用於 Loading 和 Empty 狀態
        const table = tbody.closest('table');
        const colSpan = table ? table.querySelectorAll('thead th').length : 10;

        // 1. 顯示 Loading
        tbody.innerHTML = `<tr><td colspan="${colSpan}" style="text-align:center; padding: 3rem; color: var(--gray);"><i class="fas fa-circle-notch fa-spin fa-2x" style="color: var(--gold);"></i><p style="margin-top:1rem;">加載數據中...</p></td></tr>`;

        try {
            const queryString = new URLSearchParams(params).toString();
            const response = await fetch(`${apiUrl}?${queryString}`);
            const data = await response.json();

            if (data.content !== undefined) { // 後端標準返回格式
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
                    data.smartPages,
                    (newPage) => {
                        params.page = newPage; // 更新參數中的頁碼
                        this.loadData(config); // 遞歸調用，刷新數據
                    }
                );

                // 4. 觸發全局動態事件綁定 (如果頁面有定義 bindDynamicEvents)
                if (typeof bindDynamicEvents === 'function') bindDynamicEvents();
                if (onSuccess) onSuccess(data);
            } else {
                throw new Error(data.message || '數據格式錯誤');
            }
        } catch (error) {
            console.error('加載數據失敗:', error);
            tbody.innerHTML = `<tr><td colspan="${colSpan}" style="text-align:center; padding: 2rem; color: #ef4444;"><i class="fas fa-exclamation-triangle fa-2x"></i><p style="margin-top:1rem;">加載失敗，請檢查網絡或後端接口</p></td></tr>`;
            if (typeof showNotification === 'function') showNotification('❌ 網絡錯誤或接口異常', true);
        }
    }
};