import React, { useState, useEffect, useCallback, useRef } from 'react';

const ReplySection = ({
    reviewId,
    initialReplyCount,
    currentUsername,
    isAdmin,
    productId,
    rootUsername,
    onReplyCountChange,
    showReplyForm,
    setShowReplyForm,
    blockedUsers,        // 從父組件接收禁言列表 (實現全局同步)
    onBlockUser          // 從父組件接收禁言處理函數
}) => {
    // 輔助函數：解決 React 組件中無法直接調用 common.js 全局函數的問題
    const notify = (message, isError = false) => {
        if (typeof window.showNotification === 'function') {
            window.showNotification(message, isError);
        } else {
            alert(message);
        }
    };

    const [isOpen, setIsOpen] = useState(false);
    const [replies, setReplies] = useState([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [loading, setLoading] = useState(false);
    const [sort, setSort] = useState('newest');

    // 內部表單狀態
    const [replyText, setReplyText] = useState('');
    const [replyToUser, setReplyToUser] = useState('');
    const [replyParentId, setReplyParentId] = useState(null);

    // 禁言模態框狀態
    const [showBlockModal, setShowBlockModal] = useState(false);
    const [targetBlockUser, setTargetBlockUser] = useState('');
    const [blockValue, setBlockValue] = useState(1);
    const [blockUnit, setBlockUnit] = useState('day');

    // ==========================================
    // 刪除回覆模態框相關狀態
    // ==========================================
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [deleteReason, setDeleteReason] = useState('inappropriate');
    const [customReason, setCustomReason] = useState('');
    const [deletingReplyId, setDeletingReplyId] = useState(null);

    // ==========================================
    // 【新增】用於記錄管理員當前會話中已封禁的用戶
    // ==========================================
    const adminBannedUsersRef = useRef(new Set());

    // ==========================================
    // 🛡️ 兜底邏輯：確保關閉管理員權限彈窗的全局函數存在
    // ==========================================
    useEffect(() => {
        if (typeof window.closeAdminPermissionModal !== 'function') {
            window.closeAdminPermissionModal = () => {
                const modal = document.getElementById('adminPermissionModal');
                if (modal) {
                    modal.style.display = 'none';
                    document.body.style.overflow = ''; // 恢復背景滾動
                }
            };
        }
    }, []);

    // 不再需要本地 blockedUsers 狀態，改用 props

    // 【核心修復 1】當 showReplyForm 變成 true 時，根據是否有回覆決定是否展開，並修復 replyToUser 被覆蓋的問題
    useEffect(() => {
        if (showReplyForm) {
            if (initialReplyCount > 0) {
                setIsOpen(true);
            } else {
                setIsOpen(false);
            }
            // 關鍵修復：只有當 replyParentId 為 null 時（回覆根評論），才設置為 rootUsername
            // 如果是樓中樓回覆（replyParentId 有值），保持之前點擊回復按鈕時設置的 replyToUser
            if (replyParentId === null && rootUsername) {
                setReplyToUser(rootUsername);
            }
        }
    }, [showReplyForm, rootUsername, initialReplyCount, replyParentId]);

    const fetchReplies = useCallback(async (targetPage = 0, targetSort = sort) => {
        setLoading(true);
        try {
            const res = await fetch(`/api/review/${reviewId}/replies?page=${targetPage}&size=20&sort=${targetSort}`, { credentials: 'same-origin' });
            const result = await res.json();
            if (result.success) {
                setReplies(result.data.replies);
                setTotalPages(result.data.totalPages);
                setPage(targetPage);
                setSort(targetSort);
            }
        } catch (err) { console.error(err); }
        finally { setLoading(false); }
    }, [reviewId, sort]);

    const handleSubmitReply = async () => {
        if (!replyText.trim()) return alert('請輸入內容');
        try {
            const res = await fetch('/api/review/submit', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify({
                    productId: productId,
                    content: replyText,
                    // 【核心修復 2】：強制扁平化，無論回復根評論還是樓中樓，parentId 永遠是根評論 ID
                    parentId: reviewId,
                    replyToUser: replyToUser || null
                })
            });
            const result = await res.json();
            if (result.success) {
                setReplyText('');
                setReplyToUser('');
                setReplyParentId(null);
                setShowReplyForm(false);
                if (onReplyCountChange) onReplyCountChange(reviewId, 1);
                if (result.data && page === 0 && isOpen) {
                    setReplies(prev => [result.data, ...prev]);
                } else if (isOpen) {
                    await fetchReplies(0, 'newest');
                }
            } else {
                //最高優先級 - 攔截「永久拉黑」錯誤，彈出紅色警告框
                if (result.message === "BLACKLISTED") {
                    if (window.showBlacklistedModal) {
                        window.showBlacklistedModal(); // 調用 HTML 中定義的紅色彈窗
                    }
                    return; // 攔截，不執行後續操作
                }
                //  新增：檢查是否為全局禁言
                if (result.message === "GLOBAL_BAN" && result.data && result.data.banned) {
                    if (window.showGlobalBanModal) {
                        window.showGlobalBanModal(currentUsername, result.data.expiresAt);
                    }
                    return; // 攔截，不執行後續操作
                }
                // 普通錯誤提示
                alert(result.message || '提交失敗');
            }
        } catch (error) { console.error('提交回覆失敗:', error); }
    };

    // ==========================================
    // 修改：刪除回覆邏輯 (區分管理員與普通用戶)
    // ==========================================
    const handleDeleteReply = async (replyId) => {
        if (isAdmin) {
            // 管理員：打開模態框選擇原因
            setDeletingReplyId(replyId);
            setShowDeleteModal(true);
            setDeleteReason('inappropriate');
            setCustomReason('');
        } else {
            // 普通用戶：使用自定義確認彈窗
            if (window.openDeleteModal) {
                window.openDeleteModal('確定刪除這條回覆嗎？', async () => {
                    await executeDeleteReply(replyId);
                });
            } else {
                // 降級處理：如果沒有全局函數，使用原生 confirm
                if (confirm('確定刪除這條回覆嗎？')) {
                    await executeDeleteReply(replyId);
                }
            }
        }
    };

    // 新增：執行刪除的輔助函數
    const executeDeleteReply = async (replyId, reason = null) => {
        try {
            const deleteBody = {};
            if (isAdmin && reason) {
                deleteBody.deleteReason = reason;
            }

            const res = await fetch(`/api/review/${replyId}`, {
                method: 'DELETE',
                credentials: 'same-origin',
                headers: { 'Content-Type': 'application/json' },
                body: Object.keys(deleteBody).length > 0 ? JSON.stringify(deleteBody) : undefined
            });

            const result = await res.json();
            if (result.success) {
                setReplies(prev => prev.filter(r => r.id !== replyId));
                if (onReplyCountChange) onReplyCountChange(reviewId, -1);
                if (window.showNotification) {
                    window.showNotification('✅ 回覆已刪除');
                }
            } else {
                if (window.showNotification) {
                    window.showNotification('❌ ' + (result.message || '刪除失敗'), true);
                } else {
                    alert(result.message || '刪除失敗');
                }
            }
        } catch (error) {
            console.error('刪除失敗:', error);
            if (window.showNotification) {
                window.showNotification('❌ 網絡錯誤', true);
            } else {
                alert('網絡錯誤');
            }
        }
    };

    // 管理員確認刪除（模態框中的按鈕調用）
    const confirmDeleteReply = () => {
        let finalReason = '';
        if (deleteReason === 'custom') {
            if (!customReason.trim()) {
                if (window.showNotification) {
                    window.showNotification('❌ 請輸入自定義原因', true);
                } else {
                    alert('請輸入自定義原因');
                }
                return;
            }
            finalReason = customReason;
        } else {
            // 根據選項映射為更委婉的文字
            switch (deleteReason) {
                case 'inappropriate':
                    finalReason = '該內容不符合社區規範，因此被移除。';
                    break;
                case 'ads':
                    finalReason = '檢測到廣告或推廣信息，因此被移除。';
                    break;
                case 'irrelevant':
                    finalReason = '該評論與商品無關，因此被移除。';
                    break;
                default:
                    finalReason = '該內容不符合社區規範，因此被移除。';
            }
        }

        setShowDeleteModal(false);
        executeDeleteReply(deletingReplyId, finalReason);
        setDeletingReplyId(null);
    };

    const handleReaction = async (replyId, isDislike = false) => {
        // 樓中樓點贊/踩的未登入攔截
        if (!currentUsername || currentUsername === 'anonymousUser') {
            if (typeof window.showLoginRequiredModal === 'function') {
                window.showLoginRequiredModal();
            }
            return;
        }
        const api = isDislike ? `/api/review/${replyId}/dislike` : `/api/review/${replyId}/like`;
        try {
            const res = await fetch(api, { method: 'POST', credentials: 'same-origin' });
            const result = await res.json();

            if (result.success) {
                setReplies(prev => prev.map(r =>
                    r.id === replyId ? {
                        ...r,
                        likeCount: result.likeCount,
                        dislikeCount: result.dislikeCount,
                        // 核心修復：同步點讚/踩的布爾狀態
                        isLikedByMe: result.liked,
                        isDislikedByMe: result.disliked
                    } : r
                ));
            } else {
                // ================= 失敗情況下的攔截邏輯 =================

                // 1. 最高優先級：攔截「永久拉黑」錯誤，彈出紅色警告框
                if (result.message === "BLACKLISTED" || result.blacklisted) {
                    if (window.showBlacklistedModal) {
                        window.showBlacklistedModal(); // 調用 HTML 中定義的紅色彈窗
                    }
                    return; // 攔截，不執行後續的普通錯誤提示
                }

                // 2. 普通錯誤提示 (例如：網絡錯誤、評論不存在等)
                console.error('點贊/踩失敗:', result.message);
            }
        } catch (err) {
            console.error('網絡請求錯誤:', err);
        }
    };

    // 【架構優化】：使用父組件傳遞的 onBlockUser 函數，實現全局狀態同步
    const confirmBlock = async (durationMinutes) => {
        if (isAdmin && durationMinutes) {
            let totalMinutes = 0;
            if (blockUnit === 'day') {
                totalMinutes = durationMinutes * 24 * 60;  // 1天 = 1440分钟
            } else if (blockUnit === 'week') {
                totalMinutes = durationMinutes * 7 * 24 * 60;  //1周 = 7天
            } else if (blockUnit === 'month') {
                totalMinutes = durationMinutes * 30 * 24 * 60;  //1月 ≈ 30天
            }
            const params = new URLSearchParams();
            params.append('durationMinutes', totalMinutes);
            params.append('reason', '管理員禁言');
            params.append('reviewId', reply.id);
            params.append('reviewContent', reply.content);

            try {
                const response = await fetch(
                    `/api/user/${targetBlockUser}/toggle-block?${params}`,
                    { method: 'POST', credentials: 'same-origin' }
                );
                const result = await response.json();

                            if (result.alreadyBanned) {
                                // 弹出警告弹窗（复用 adminPermissionModal）
                                const modal = document.getElementById('adminPermissionModal');
                                if (modal) {
                                    modal.style.display = 'flex';
                                    document.body.style.overflow = 'hidden';
                                }
                                return; // 阻止后续操作
                            }

                if (response.ok) {
                    notify(`✅ 已全局禁言用戶 ${blockValue} ${blockUnit === 'day' ? '天' : blockUnit === 'week' ? '週' : '月'}`);
                    // 調用父組件的 onBlockUser 更新共享狀態
                    await onBlockUser(targetBlockUser, true);
                } else {
                    notify('❌ ' + result.message, true);
                }
            } catch (error) {
                notify('❌ 網絡錯誤', true);
            }
        }
        setShowBlockModal(false);
    };

    // 【修改】確認封禁 (選時長)
    const handleConfirmBlock = () => {
        const val = parseInt(blockValue);
        if (!val || val <= 0) {
            notify('❌ 請輸入有效的數字', true);
            return;
        }
        confirmBlock(val);
    };

    // ==========================================
    // 核心修改：解除禁言函數 (攔截管理員)
    // ==========================================
    const handleUnblock = async (targetUsername) => {
        // 1. 核心攔截：如果是管理員，彈出權限提示並阻止操作
        if (isAdmin) {
            const modal = document.getElementById('adminPermissionModal');
            if (modal) {
                modal.style.display = 'flex';
                document.body.style.overflow = 'hidden';
            }
            return; // 攔截，不執行後續的解除禁言邏輯
        }

        // 2. 普通用戶的解除禁言邏輯
        if (typeof window.openUnblockModal === 'function') {
            // 使用自定義彈窗
            window.openUnblockModal(targetUsername, async (username) => {
                const result = await onBlockUser(username, false);
                if (result.success) {
                    notify('✅ 已解除禁言');
                } else {
                    notify('❌ ' + result.message, true);
                }
            });
        } else {
            // 降級處理：使用原生 confirm
            if (!window.confirm('確定要解除禁言嗎？')) return;
            const result = await onBlockUser(targetUsername, false);
            if (result.success) {
                notify('✅ 已解除禁言');
            } else {
                notify('❌ ' + result.message, true);
            }
        }
        // ❌ 刪除這裡的重複代碼！
    };

    // 【修改】禁言按鈕點擊處理
    const handleBlockClick = (targetUsername) => {
        setTargetBlockUser(targetUsername); // 僅用於管理員模態框顯示
        if (isAdmin) {
                setShowBlockModal(true);

        } else {
            // 普通用戶直接調用父組件的禁言函數
            onBlockUser(targetUsername, true).then(result => {
                if (result.success) {
                    notify('✅ 已禁言該用戶，雙方將無法互相回復');
                } else {
                    notify('❌ ' + result.message, true);
                }
            });
        }
    };

    // 【新增】確認再次封禁的函數 (被 adminPermissionModal 的按鈕調用)
    window.confirmRepeatBan = () => {
        if (typeof window.closeAdminPermissionModal === 'function') {
            window.closeAdminPermissionModal();
        } else {
            const modal = document.getElementById('adminPermissionModal');
            if (modal) {
                modal.style.display = 'none';
                document.body.style.overflow = '';
            }
        }
        // 確認後，彈出選時長模態框
        setShowBlockModal(true);
    };

    //  管理員專屬：永久拉黑用戶 (調用 HTML 中的自定義漂亮彈窗) 不是react自帶的
    const handleBlacklist = (targetUsername) => {
        // 調用 HTML 中定義的全局函數，打開漂亮的自定義彈窗
        if (typeof window.openBlacklistConfirmModal === 'function') {
            window.openBlacklistConfirmModal(targetUsername);
        } else {
            // 降級處理：萬一 HTML 彈窗沒加載成功，給予提示
            notify('❌ 系統彈窗組件未加載，請刷新頁面後重試', true);
        }
    };

    const formatDate = (dateStr) => {
        if (!dateStr) return '';
        const d = new Date(dateStr);
        return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
    };

    return (
        <div className="reply-section-wrapper">
            {/* 工具欄 */}
            {(initialReplyCount > 0 || isOpen) && (
                <div className="reply-toolbar">
                    <div className="reply-toolbar-left">
                        {initialReplyCount > 0 && !isOpen && (
                            <button className="reply-btn" onClick={() => {
                                setIsOpen(true);
                                fetchReplies(0, sort);
                            }}>
                                <i className="fas fa-comments"></i> 查看 {initialReplyCount} 條回覆
                            </button>
                        )}
                        {isOpen && (
                            <button className="reply-btn" onClick={() => setIsOpen(false)}>
                                <i className="fas fa-chevron-up"></i> 收起
                            </button>
                        )}
                    </div>
                    {isOpen && (
                        <div className="reply-toolbar-right">
                            <select onChange={(e) => fetchReplies(0, e.target.value)} value={sort} className="reply-sort-select">
                                <option value="newest">最新發佈</option>
                                <option value="oldest">最早發佈</option>
                                <option value="popular">最多點贊</option>
                            </select>
                        </div>
                    )}
                </div>
            )}
            {/* 列表區域 */}
            {isOpen && (
                <div className="reply-list-container">
                    {/* 情況1：回復「根評論」，表單顯示在列表最上方 */}
                    {showReplyForm && replyParentId === null && (
                        <div className="reply-form active" style={{ marginBottom: '1rem' }}>
                            <label>回復 @{replyToUser}：</label>
                            <textarea
                                value={replyText}
                                onChange={(e) => setReplyText(e.target.value)}
                                placeholder="輸入回復內容..."
                                maxLength="500"
                                autoFocus
                            ></textarea>
                            <div className="form-actions">
                                <button className="btn-cancel-reply" onClick={() => { setShowReplyForm(false); setReplyParentId(null); }}>取消</button>
                                <button className="btn-submit-reply" onClick={handleSubmitReply}>提交</button>
                            </div>
                        </div>
                    )}
                    {/* 評論列表 */}
                    {loading ? (
                        <div className="reply-loading"><i className="fas fa-spinner fa-spin"></i> 加載中...</div>
                    ) : (
                        <div className="reply-items">
                            {replies.map(reply => (
                                <div key={reply.id}>
                                    <div className="reply-item">
                                        <div className="reply-header">
                                            <div className="reply-user">
                                                <strong>{reply.customer.username}</strong>
                                                {reply.replyToUser && <span className="reply-to"> 回復 @{reply.replyToUser}</span>}
                                                {reply.customer.username === currentUsername && <span className="reply-badge">我</span>}
                                            </div>
                                            <div className="reply-date">{formatDate(reply.createdAt)}</div>
                                        </div>
                                        <div className="reply-content">{reply.content}</div>
                                        <div className="reply-actions">
                                            {/* 點贊按鈕 */}
                                            <button
                                                className={`reply-action-btn btn-like ${reply.isLikedByMe ? 'active' : ''}`}
                                                onClick={() => handleReaction(reply.id)}
                                            >
                                                <i className={reply.isLikedByMe ? "fas fa-thumbs-up" : "far fa-thumbs-up"}></i> {reply.likeCount || 0}
                                            </button>

                                            {/* 踩按鈕 */}
                                            <button
                                                className={`reply-action-btn btn-dislike ${reply.isDislikedByMe ? 'active' : ''}`}
                                                onClick={() => handleReaction(reply.id, true)}
                                            >
                                                <i className={reply.isDislikedByMe ? "fas fa-thumbs-down" : "far fa-thumbs-down"}></i> {reply.dislikeCount || 0}
                                            </button>

                                            {/* 【核心修復 3】：樓中樓回復按鈕，加入禁言攔截與全局彈窗 */}
                                            <button className="reply-action-btn" onClick={async () => {
                                                // 1. 登錄檢查
                                                if (!currentUsername || currentUsername === 'anonymousUser') {
                                                    if (typeof window.showLoginRequiredModal === 'function') {
                                                        window.showLoginRequiredModal();
                                                    }
                                                    return;
                                                }
                                                // 2. 核心攔截：檢查是否被該樓中樓作者禁言
                                                const targetUsername = reply.customer.username;
                                                if (targetUsername !== currentUsername) {
                                                    try {
                                                        const res = await fetch(`/api/user/can-reply/${targetUsername}`, { credentials: 'same-origin' });
                                                        const data = await res.json();
                                                        if (data.success && !data.canReply) {
                                                            if (window.showBlockedModal) {
                                                                window.showBlockedModal('您已經被對方禁言，無法回復！！！');
                                                            } else {
                                                                alert('您已經被對方禁言，無法回復！！！');
                                                            }
                                                            return; // 阻止彈出回復框
                                                        }
                                                    } catch (e) {
                                                        console.error('檢查禁言狀態失敗', e);
                                                    }
                                                }
                                                // 3. 檢查通過，正常彈出回復框
                                                setReplyParentId(reply.id);
                                                setReplyToUser(reply.customer.username);
                                                setShowReplyForm(true);
                                            }}>
                                                <i className="fas fa-reply"></i> 回復
                                            </button>

                                            {(reply.customer.username === currentUsername || isAdmin) && (
                                                <button className="reply-action-btn delete" onClick={() => handleDeleteReply(reply.id)}>
                                                    <i className="fas fa-trash-alt"></i> 刪除
                                                </button>
                                            )}

                                            {/* 【核心修改】：樓中樓的禁言按鈕渲染邏輯 */}
                                            {currentUsername && currentUsername !== 'anonymousUser' && reply.customer.username !== currentUsername && (
                                                isAdmin ? (
                                                    // 管理員永遠顯示「禁言」按鈕
                                                    <button
                                                        className="reply-action-btn btn-ban"
                                                        onClick={() => handleBlockClick(reply.customer.username)}
                                                        title="全局禁言"
                                                    >
                                                        <i className="fas fa-ban"></i> 禁言
                                                    </button>
                                                ) : (
                                                    // 普通用戶邏輯：根據 blockedUsers 狀態顯示「禁言」或「解除禁言」
                                                    blockedUsers && blockedUsers.includes(reply.customer.username) ? (
                                                        <button
                                                            className="reply-action-btn btn-unblock"
                                                            onClick={() => handleUnblock(reply.customer.username)}
                                                            title="解除禁言"
                                                        >
                                                            <i className="fas fa-unlock"></i> 解除禁言
                                                        </button>
                                                    ) : (
                                                        <button
                                                            className="reply-action-btn btn-ban"
                                                            onClick={() => handleBlockClick(reply.customer.username)}
                                                            title="雙向禁言"
                                                        >
                                                            <i className="fas fa-ban"></i> 禁言
                                                        </button>
                                                    )
                                                )
                                            )}

                                            {/*  舉報按鈕（僅其他登錄用戶可見，本人和admin不可見） */}
                                            {currentUsername && currentUsername !== 'anonymousUser' &&
                                                reply.customer.username !== currentUsername &&
                                                !isAdmin && (
                                                    <button
                                                        className="reply-action-btn btn-report"
                                                        onClick={() => {
                                                            if (typeof window.openReportModal === 'function') {
                                                                window.openReportModal(reply.id, reply.customer.username, 'REVIEW', reply.content);
                                                            }
                                                        }}
                                                        title="舉報不當內容"
                                                    >
                                                        <i className="fas fa-flag"></i> 舉報
                                                    </button>
                                                )}
                                            {currentUsername && isAdmin && (
                                                <button
                                                    className="reply-action-btn btn-blacklist"
                                                    onClick={() => handleBlacklist(reply.customer.username)}
                                                    title="永久拉黑該用戶"
                                                >
                                                    <i className="fas fa-user-slash"></i> 永久拉黑
                                                </button>
                                            )}
                                        </div>
                                    </div>
                                    {/* 情況2：回復「這條評論」，表單緊貼在它下方顯示 */}
                                    {showReplyForm && replyParentId === reply.id && (
                                        <div className="reply-form active" style={{ marginLeft: '2rem', marginTop: '0.5rem' }}>
                                            <label>回復 @{replyToUser}：</label>
                                            <textarea
                                                value={replyText}
                                                onChange={(e) => setReplyText(e.target.value)}
                                                placeholder={`回復 @${replyToUser}...`}
                                                maxLength="500"
                                                autoFocus
                                            ></textarea>
                                            <div className="form-actions">
                                                <button className="btn-cancel-reply" onClick={() => { setShowReplyForm(false); setReplyParentId(null); }}>取消</button>
                                                <button className="btn-submit-reply" onClick={handleSubmitReply}>提交</button>
                                            </div>
                                        </div>
                                    )}
                                </div>
                            ))}
                        </div>
                    )}
                    {/* 分頁 */}
                    {totalPages > 1 && (
                        <div className="reply-pagination">
                            {page > 0 && <button className="btn btn-outline" onClick={() => fetchReplies(page - 1)}>上一頁</button>}
                            {Array.from({ length: totalPages }, (_, i) => (
                                <button key={i} className={`btn ${i === page ? 'btn-gold' : 'btn-outline'}`} onClick={() => fetchReplies(i)}>{i + 1}</button>
                            ))}
                            {page < totalPages - 1 && <button className="btn btn-outline" onClick={() => fetchReplies(page + 1)}>下一頁</button>}
                        </div>
                    )}
                </div>
            )}
            {/* 如果沒有回覆 (isOpen 為 false)，直接顯示表單 */}
            {!isOpen && showReplyForm && replyParentId === null && (
                <div className="reply-form active" style={{ marginTop: '1rem' }}>
                    <label>回復 @{replyToUser}：</label>
                    <textarea
                        value={replyText}
                        onChange={(e) => setReplyText(e.target.value)}
                        placeholder="輸入回復內容..."
                        maxLength="500"
                        autoFocus
                    ></textarea>
                    <div className="form-actions">
                        <button className="btn-cancel-reply" onClick={() => { setShowReplyForm(false); setReplyParentId(null); }}>取消</button>
                        <button className="btn-submit-reply" onClick={handleSubmitReply}>提交</button>
                    </div>
                </div>
            )}
            {/* 管理員禁言時長選擇模態框 */}
            {showBlockModal && (
                <div
                    className="modal-overlay"
                    onClick={() => setShowBlockModal(false)}
                    style={{ position: 'fixed', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 9999 }}
                >
                    <div
                        className="modal-box"
                        onClick={e => e.stopPropagation()}
                        style={{ background: 'white', padding: '2rem', borderRadius: '12px', width: '90%', maxWidth: '500px', boxShadow: '0 10px 25px rgba(0,0,0,0.2)' }}
                    >
                        <h3 style={{ marginBottom: '1rem', color: 'var(--primary)' }}>
                            <i className="fas fa-gavel" style={{ color: 'var(--accent)', marginRight: '0.5rem' }}></i>
                            管理員禁言
                        </h3>
                        <p style={{ marginBottom: '1rem', color: 'var(--gray)' }}>
                            請輸入禁言時長，用戶 <strong>{targetBlockUser}</strong> 在期間內將無法在任何評論區回覆：
                        </p>
                        <div style={{ display: 'flex', gap: '1rem', alignItems: 'center', margin: '1.5rem 0' }}>
                            <input
                                type="number"
                                min="1"
                                value={blockValue}
                                onChange={(e) => setBlockValue(e.target.value)}
                                placeholder="請輸入數字"
                                style={{ flex: 1, padding: '0.5rem', border: '1px solid #ddd', borderRadius: '4px' }}
                            />
                            <select
                                value={blockUnit}
                                onChange={(e) => setBlockUnit(e.target.value)}
                                style={{ padding: '0.5rem', border: '1px solid #ddd', borderRadius: '4px', minWidth: '80px' }}
                            >
                                <option value="day">天</option>
                                <option value="week">週</option>
                                <option value="month">月</option>
                            </select>
                        </div>
                        <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '1.5rem' }}>
                            <button
                                onClick={() => setShowBlockModal(false)}
                                style={{ padding: '0.5rem 1rem', background: '#e9ecef', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
                            >
                                取消
                            </button>
                            <button
                                onClick={handleConfirmBlock}
                                style={{ padding: '0.5rem 1rem', background: 'var(--accent)', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
                            >
                                確認禁言
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* ========================================== */}
            {/* 新增：管理員刪除回覆確認模態框 */}
            {/* ========================================== */}
            {showDeleteModal && (
                <div
                    className="modal-overlay"
                    onClick={() => setShowDeleteModal(false)}
                    style={{
                        position: 'fixed',
                        top: 0,
                        left: 0,
                        right: 0,
                        bottom: 0,
                        backgroundColor: 'rgba(0,0,0,0.5)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        zIndex: 9999
                    }}
                >
                    <div
                        className="modal-box"
                        onClick={e => e.stopPropagation()}
                        style={{
                            background: 'white',
                            padding: '2rem',
                            borderRadius: '12px',
                            width: '90%',
                            maxWidth: '500px',
                            boxShadow: '0 10px 25px rgba(0,0,0,0.2)'
                        }}
                    >
                        <h3 style={{ marginBottom: '1rem', color: 'var(--primary)' }}>
                            <i className="fas fa-exclamation-triangle" style={{ color: 'var(--accent)', marginRight: '0.5rem' }}></i>
                            刪除回覆確認
                        </h3>
                        <p style={{ marginBottom: '1rem', color: 'var(--gray)' }}>
                            請選擇刪除原因，這將發送通知給用戶 <strong>{replies.find(r => r.id === deletingReplyId)?.customer?.username || '該用戶'}</strong>：
                        </p>

                        <div style={{ marginBottom: '1rem' }}>
                            <label style={{ display: 'block', marginBottom: '0.5rem', cursor: 'pointer' }}>
                                <input
                                    type="radio"
                                    name="deleteReason"
                                    value="inappropriate"
                                    checked={deleteReason === 'inappropriate'}
                                    onChange={(e) => setDeleteReason(e.target.value)}
                                    style={{ marginRight: '0.5rem' }}
                                />
                                內容不合規 (默認)
                            </label>
                            <label style={{ display: 'block', marginBottom: '0.5rem', cursor: 'pointer' }}>
                                <input
                                    type="radio"
                                    name="deleteReason"
                                    value="ads"
                                    checked={deleteReason === 'ads'}
                                    onChange={(e) => setDeleteReason(e.target.value)}
                                    style={{ marginRight: '0.5rem' }}
                                />
                                廣告或推廣信息
                            </label>
                            <label style={{ display: 'block', marginBottom: '0.5rem', cursor: 'pointer' }}>
                                <input
                                    type="radio"
                                    name="deleteReason"
                                    value="irrelevant"
                                    checked={deleteReason === 'irrelevant'}
                                    onChange={(e) => setDeleteReason(e.target.value)}
                                    style={{ marginRight: '0.5rem' }}
                                />
                                與商品無關
                            </label>
                            <label style={{ display: 'block', marginBottom: '0.5rem', cursor: 'pointer' }}>
                                <input
                                    type="radio"
                                    name="deleteReason"
                                    value="custom"
                                    checked={deleteReason === 'custom'}
                                    onChange={(e) => setDeleteReason(e.target.value)}
                                    style={{ marginRight: '0.5rem' }}
                                />
                                自定義原因
                            </label>
                        </div>

                        {deleteReason === 'custom' && (
                            <textarea
                                style={{
                                    width: '100%',
                                    minHeight: '80px',
                                    padding: '0.5rem',
                                    borderRadius: '4px',
                                    border: '1px solid #ddd',
                                    fontFamily: 'inherit',
                                    marginBottom: '1rem'
                                }}
                                placeholder="請輸入具體的刪除原因..."
                                value={customReason}
                                onChange={(e) => setCustomReason(e.target.value)}
                            />
                        )}

                        <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '1.5rem' }}>
                            <button
                                onClick={() => setShowDeleteModal(false)}
                                style={{
                                    padding: '0.5rem 1rem',
                                    background: '#e9ecef',
                                    border: 'none',
                                    borderRadius: '4px',
                                    cursor: 'pointer'
                                }}
                            >
                                取消
                            </button>
                            <button
                                onClick={confirmDeleteReply}
                                style={{
                                    padding: '0.5rem 1rem',
                                    background: 'var(--accent)',
                                    color: 'white',
                                    border: 'none',
                                    borderRadius: '4px',
                                    cursor: 'pointer'
                                }}
                            >
                                確認刪除
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

export default ReplySection;