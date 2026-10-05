// @ts-check
/** admin-table.js
 *  通用後台表格管理器 (AdminTable)
 */
const AdminTable = {

    renderPagination: function(containerId, currentPage, totalPages, smartPages, totalElements, onPageChange) {
        const container = document.getElementById(containerId);
        if (!container) return;

        if (!totalElements || totalElements <= 0) {
            container.innerHTML = '';
            return;
        }

        let html = '<div class="pagination-wrapper">';

        if (currentPage > 1) {
            html += `<button type="button" class="page-btn" data-page="${currentPage - 1}">
                    <i class="fas fa-chevron-left"></i> 上一頁
                 </button>`;
        } else {
            html += `<button type="button" class="page-btn disabled" disabled>
                    <i class="fas fa-chevron-left"></i> 上一頁
                 </button>`;
        }

        if (smartPages && smartPages.length > 0) {
            smartPages.forEach(item => {
                if (item.isEllipsis) {
                    html += `<span class="page-ellipsis">...</span>`;
                } else {
                    const isActive = item.pageNumber === currentPage ? 'active' : '';
                    html += `<button type="button" class="page-btn ${isActive}" data-page="${item.pageNumber}">
                            ${item.pageNumber}
                         </button>`;
                }
            });
        } else {
            html += `<span class="page-btn active">${currentPage}</span>`;
        }

        if (currentPage < totalPages) {
            html += `<button type="button" class="page-btn" data-page="${currentPage + 1}">
                    下一頁 <i class="fas fa-chevron-right"></i>
                 </button>`;
        } else {
            html += `<button type="button" class="page-btn disabled" disabled>
                    下一頁 <i class="fas fa-chevron-right"></i>
                 </button>`;
        }

        html += '</div>';
        container.innerHTML = html;

        container.querySelectorAll('.page-btn:not(.disabled)').forEach(btn => {
            btn.addEventListener('click', function() {
                const newPage = parseInt(this.getAttribute('data-page'));
                if (!isNaN(newPage) && onPageChange) {
                    onPageChange(newPage);
                }
            });
        });
    },

    loadData: async function(config) {
        const {
            apiUrl,
            params,
            tbodySelector,
            paginationId,
            renderRow,
            emptyText,
            onSuccess
        } = config;

        const tbody = document.querySelector(tbodySelector);
        if (!tbody) return console.error(`找不到 tbody: ${tbodySelector}`);

        const table = tbody.closest('table');
        const colSpan = table ? table.querySelectorAll('thead th').length : 10;

        tbody.innerHTML = `<tr><td colspan="${colSpan}" style="text-align:center; padding: 3rem; color: var(--gray);"><i class="fas fa-circle-notch fa-spin fa-2x" style="color: var(--gold);"></i><p style="margin-top:1rem;">加載數據中...</p></td></tr>`;

        try {
            const queryString = new URLSearchParams(params).toString();
            const response = await fetch(`${apiUrl}?${queryString}`);
            const data = await response.json();

            if (data.content !== undefined) {

                // 【嚴格執行】：必須先執行 onSuccess，確保全局變量在 renderRow 渲染前被賦值
                if (onSuccess) {
                    onSuccess(data);
                }

                if (data.content.length > 0) {
                    tbody.innerHTML = data.content.map(renderRow).join('');
                } else {
                    tbody.innerHTML = `<tr><td colspan="${colSpan}" style="text-align:center; padding: 3rem; color: var(--gray);"><i class="fas fa-inbox fa-2x" style="color: #e9ecef;"></i><p style="margin-top:1rem;">${emptyText || '暫無數據'}</p></td></tr>`;
                }

                this.renderPagination(
                    paginationId,
                    data.currentPage,
                    data.totalPages,
                    data.smartPages,
                    data.totalElements,
                    (newPage) => {
                        params.page = newPage;
                        this.loadData(config);
                    }
                );

                if (typeof bindDynamicEvents === 'function') bindDynamicEvents();

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