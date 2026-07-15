document.addEventListener('DOMContentLoaded', function() {
    // 1. 側邊欄菜單展開/收起 (點擊總功能)
    const menuGroups = document.querySelectorAll('.menu-group-title');
    menuGroups.forEach(title => {
        title.addEventListener('click', () => {
            const group = title.parentElement;
            group.classList.toggle('active'); // 切換 active 類觸發 CSS 動畫
        });
    });

    // 2. 點擊菜單項切換右側內容 (如果有 data-page 屬性)
    const menuLinks = document.querySelectorAll('.menu-items a[data-page]');
    menuLinks.forEach(link => {
        link.addEventListener('click', (e) => {
            // 如果是真實的 href 鏈接，允許跳轉
            const href = link.getAttribute('href');
            if (href && href !== '#') {
                return; // 讓瀏覽器正常跳轉
            }

            e.preventDefault(); // 阻止默認跳轉

            const pageId = link.getAttribute('data-page');
            const pageTitle = link.textContent.trim();
            const parentTitle = link.closest('.menu-group').querySelector('.menu-group-title span').textContent.trim();

            // 更新左側激活狀態
            menuLinks.forEach(l => l.classList.remove('active'));
            link.classList.add('active');

            // 更新頂部標題和麵包屑
            const pageTitleEl = document.getElementById('pageTitle');
            const pageBreadcrumbEl = document.getElementById('pageBreadcrumb');

            if (pageTitleEl) pageTitleEl.textContent = pageTitle;
            if (pageBreadcrumbEl) pageBreadcrumbEl.textContent = parentTitle + ' / ' + pageTitle;

            // 切換右側內容區
            const contentBody = document.getElementById('contentBody');
            if (contentBody) {
                contentBody.querySelectorAll('> div').forEach(div => {
                    div.style.display = 'none';
                });

                const targetPage = document.getElementById('page-' + pageId);
                if (targetPage) {
                    targetPage.style.display = 'block';
                }
            }
        });
    });

    // 3. 移動端側邊欄切換按鈕（如果需要）
    const toggleSidebarBtn = document.getElementById('toggleSidebar');
    if (toggleSidebarBtn) {
        toggleSidebarBtn.addEventListener('click', () => {
            const sidebar = document.querySelector('.admin-sidebar');
            sidebar.classList.toggle('open');
        });
    }
});