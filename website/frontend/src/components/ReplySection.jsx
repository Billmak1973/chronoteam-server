import React, { useState, useEffect, useCallback } from 'react';

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
            }else {
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

    const handleDeleteReply = async (replyId) => {
        if (!confirm('確定刪除這條回覆嗎？')) return;
        try {
            const res = await fetch(`/api/review/${replyId}`, { method: 'DELETE', credentials: 'same-origin' });
            const result = await res.json();
            if (result.success) {
                setReplies(prev => prev.filter(r => r.id !== replyId));
                if (onReplyCountChange) onReplyCountChange(reviewId, -1);
            }
        } catch (error) { console.error('刪除失敗:', error); }
    };

    const handleReaction = async (replyId, isDislike = false) => {
        const api = isDislike ? `/api/review/${replyId}/dislike` : `/api/review/${replyId}/like`;
        try {
            const res = await fetch(api, { method: 'POST', credentials: 'same-origin' });
            const result = await res.json();
            if (result.success) {
                setReplies(prev => prev.map(r =>
                    r.id === replyId ? { ...r, likeCount: result.likeCount, dislikeCount: result.dislikeCount } : r
                ));
            }
        } catch (err) { console.error(err); }
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
            try {
                const response = await fetch(
                    `/api/user/${targetBlockUser}/toggle-block?${params}`,
                    { method: 'POST', credentials: 'same-origin' }
                );
                const result = await response.json();
                if (response.ok) {
                    notify(`已全局禁言用戶 ${blockValue} ${blockUnit === 'day' ? '天' : blockUnit === 'week' ? '週' : '月'}`);
                    // 調用父組件的 onBlockUser 更新共享狀態
                    await onBlockUser(targetBlockUser, true);
                } else {
                    notify(' ' + result.message, true);
                }
            } catch (error) {
                notify(' 網絡錯誤', true);
            }
        }
        setShowBlockModal(false);
    };

    const handleConfirmBlock = () => {
        const val = parseInt(blockValue);
        if (!val || val <= 0) {
            notify('❌ 請輸入有效的數字', true);
            return;
        }
        confirmBlock(val);
    };

    // 【架構優化】：使用父組件傳遞的 onBlockUser 函數
    const handleUnblock = async (targetUsername) => {
        if (!window.confirm('確定要解除禁言嗎？')) return;
        const result = await onBlockUser(targetUsername, false);
        if (result.success) {
            notify('✅ 已解除禁言');
        } else {
            notify(' ' + result.message, true);
        }
    };

    // 【禁止改動下面的代碼，以免傳出空值】普通用戶禁言時，直接傳入 targetUsername，避免 setState 非同步導致的空值問題
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

    const formatDate = (dateStr) => {
        if(!dateStr) return '';
        const d = new Date(dateStr);
        return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
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
                                            <button className="reply-action-btn" onClick={() => handleReaction(reply.id)}>
                                                <i className="far fa-thumbs-up"></i> {reply.likeCount || 0}
                                            </button>
                                            <button className="reply-action-btn" onClick={() => handleReaction(reply.id, true)}>
                                                <i className="far fa-thumbs-down"></i> {reply.dislikeCount || 0}
                                            </button>
                                            {/* 【核心修復 3】：樓中樓回復按鈕，加入禁言攔截與全局彈窗 */}
                                            <button className="reply-action-btn" onClick={async () => {
                                                // 1. 登錄檢查
                                                if (!currentUsername || currentUsername === 'anonymousUser') {
                                                    alert('⚠️ 請先登錄！未登錄用戶不能回復。');
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
                                            {/* 禁言/解除禁言按鈕：使用父組件傳來的共享 blockedUsers 狀態 */}
                                            {currentUsername && currentUsername !== 'anonymousUser' && reply.customer.username !== currentUsername && (
                                                blockedUsers && blockedUsers.includes(reply.customer.username) ? (
                                                    <button
                                                        className="reply-action-btn btn-unblock"
                                                        onClick={() => handleUnblock(reply.customer.username)}
                                                        title="解除禁言"
                                                        style={{ color: '#28a745', borderColor: '#28a745' }}
                                                    >
                                                        <i className="fas fa-unlock"></i> 解除禁言
                                                    </button>
                                                ) : (
                                                    <button
                                                        className="reply-action-btn btn-ban"
                                                        onClick={() => handleBlockClick(reply.customer.username)}
                                                        title={isAdmin ? "全局禁言" : "雙向禁言"}
                                                    >
                                                        <i className="fas fa-ban"></i> 禁言
                                                    </button>
                                                )
                                            )}

                                        {/*  舉報按鈕（僅其他登錄用戶可見，本人和admin不可見） */}
                                        {currentUsername && currentUsername !== 'anonymousUser' &&
                                         reply.customer.username !== currentUsername &&
                                         !isAdmin && (
                                            <button
                                                className="reply-action-btn btn-report"
                                                onClick={() => notify('🚨 舉報功能開發中')}
                                                title="舉報不當內容"
                                            >
                                                <i className="fas fa-flag"></i> 舉報
                                            </button>
                                        )}
                                            {currentUsername && isAdmin && (
                                                <button
                                                    className="reply-action-btn btn-blacklist"
                                                    onClick={() => notify(`🚫 拉黑用戶 ${reply.customer.username} 功能開發中`)}
                                                    title="拉黑用戶"
                                                >
                                                    <i className="fas fa-user-slash"></i> 拉黑
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
        </div>
    );
};

export default ReplySection;