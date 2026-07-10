import React, { useState, useCallback } from 'react';
import ReplySection from './ReplySection';

const ReviewCard = ({
    review,
    currentUsername,
    isAdmin,
    productId,
    onReplyCountChange,
    onReviewDeleted,
    onReviewUpdated,
    blockedUsers,        // 從父組件接收禁言列表
    onBlockUser          // 從父組件接收禁言處理函數
}) => {
    // 輔助函數：安全調用全局 showNotification，若不存在則降級為 alert
    const notify = (message, isError = false) => {
        if (typeof window.showNotification === 'function') {
            window.showNotification(message, isError);
        } else {
            alert(message);
        }
    };

    // --- 現有狀態 ---
    const [likeCount, setLikeCount] = useState(review.likeCount || 0);
    const [dislikeCount, setDislikeCount] = useState(review.dislikeCount || 0);
    const [isEditing, setIsEditing] = useState(false);
    const [editContent, setEditContent] = useState(review.content);
    // 核心修復：使用後端傳來的初始狀態
    const [isLiked, setIsLiked] = useState(review.isLikedByMe || false);
    const [isDisliked, setIsDisliked] = useState(review.isDislikedByMe || false);
    // 直接管理 showReplyForm
    const [showReplyForm, setShowReplyForm] = useState(false);
    // 新增：刪除模態框相關狀態
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [deleteReason, setDeleteReason] = useState('');
    const [customReason, setCustomReason] = useState('');
    // 新增：禁言相關狀態
    const [showBlockModal, setShowBlockModal] = useState(false);
    const [blockValue, setBlockValue] = useState(1);
    const [blockUnit, setBlockUnit] = useState('day');

    // 不再需要本地 isBlocked 狀態，改用 props 中的 blockedUsers
    // 檢查當前評論作者是否被禁言
    const isAuthorBlocked = blockedUsers && blockedUsers.includes(review.customer.username);
    // 檢查評論是否有互動（點贊/踩/回覆）
    //  核心修復：必須使用局部的 likeCount 和 dislikeCount state！
    // 因為點讚只會更新局部 state，不會更新 review prop！
    const hasInteractions = useCallback(() => {
        // 1. 檢查點贊或踩 (讀取實時的局部 state)
        if (likeCount > 0 || dislikeCount > 0) {
            return true;
        }
        // 2. 檢查樓中樓回覆數量 (讀取 props，因為回覆會觸發父組件更新 props)
        if (review.replyCount > 0) {
            return true;
        }
        return false;
    }, [likeCount, dislikeCount, review.replyCount]); //  依賴項必須包含這三個

    // --- 現有函數 ---
    const handleLike = async () => {
        if (!currentUsername || currentUsername === 'anonymousUser') {
            if (typeof window.showLoginRequiredModal === 'function') {
                window.showLoginRequiredModal();
            }
            return;
        }

        try {
            const res = await fetch(`/api/review/${review.id}/like`, { method: 'POST', credentials: 'same-origin' });
            const result = await res.json();
            if (result.success) {
                setLikeCount(result.likeCount);
                setDislikeCount(result.dislikeCount);
                setIsLiked(result.liked);
                setIsDisliked(result.disliked);
            } else {
                // 攔截「永久拉黑」錯誤，彈出紅色警告框
                if (result.message === "BLACKLISTED" || result.blacklisted) {
                    if (window.showBlacklistedModal) {
                        window.showBlacklistedModal();
                    }
                    return; // 攔截，不執行後續的 alert
                }
                alert(result.message || '點贊失敗');
            }
        } catch (error) {
            console.error('點贊網絡錯誤:', error);
            alert('網絡錯誤');
        }
    };

    const handleDislike = async () => {
        if (!currentUsername || currentUsername === 'anonymousUser') {
            if (typeof window.showLoginRequiredModal === 'function') {
                window.showLoginRequiredModal();
            }
            return;
        }

        try {
            const res = await fetch(`/api/review/${review.id}/dislike`, { method: 'POST', credentials: 'same-origin' });
            const result = await res.json();
            if (result.success) {
                setLikeCount(result.likeCount);
                setDislikeCount(result.dislikeCount);
                setIsLiked(result.liked);
                setIsDisliked(result.disliked);
            } else {
                // 攔截「永久拉黑」錯誤，彈出紅色警告框
                if (result.message === "BLACKLISTED" || result.blacklisted) {
                    if (window.showBlacklistedModal) {
                        window.showBlacklistedModal();
                    }
                    return; // 攔截，不執行後續的 alert
                }
                alert(result.message || '踩失敗');
            }
        } catch (error) {
            console.error('踩網絡錯誤:', error);
            alert('網絡錯誤');
        }
    };

// 核心：執行實際刪除操作的函數 (支持發送原因)
const handleDeleteAction = async (reasonText = null) => {
    try {
        const body = {};
        // 如果是管理員且提供了原因，則放入請求體
        if (isAdmin && reasonText) {
            body.deleteReason = reasonText;
        }
        const res = await fetch(`/api/review/${review.id}`, {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: Object.keys(body).length > 0 ? JSON.stringify(body) : undefined
        });
        const result = await res.json();
        if (res.ok && result.success) {
            setShowDeleteModal(false); // 關閉模態框

            //  核心修復：刪除後刷新頁面，避免出現訂單不存在錯誤
            if (typeof window.showNotification === 'function') {
                window.showNotification('✅ 評論已刪除，頁面將刷新...');
            }

            // 延遲一點點讓用戶看到提示，然後刷新頁面
            setTimeout(() => {
                window.location.reload();
            }, 1000);

        } else {
            alert(result.message || '刪除失敗');
        }
    } catch (error) {
        console.error('網絡錯誤:', error);
        alert('網絡錯誤，請稍後重試');
    }
};

// 修改：點擊刪除按鈕時，根據身份決定行為
const handleDeleteClick = () => {
    if (isAdmin) {
        // 管理員：打開模態框選擇原因
        setShowDeleteModal(true);
        setDeleteReason('inappropriate'); // 默認選項
        setCustomReason('');
    } else {
        // 普通用戶：調用漂亮的自定義彈窗
        window.openDeleteModal('確定刪除這條評論嗎？此操作無法恢復！', async () => {
            try {
                const res = await fetch(`/api/review/${review.id}`, {
                    method: 'DELETE',
                    credentials: 'same-origin'
                });
                const result = await res.json();
                if (result.success) {
                    // 刪除成功後刷新頁面
                    if(window.showNotification) {
                        window.showNotification('✅ 評論已刪除，頁面將刷新...');
                    }
                    setTimeout(() => {
                        window.location.reload();
                    }, 1000);
                } else {
                    alert(result.message || '刪除失敗');
                }
            } catch (error) {
                console.error('刪除失敗:', error);
                alert('網絡錯誤');
            }
        });
    }
};

    // 處理模態框中的提交邏輯
    const confirmDeleteWithReason = () => {
        let finalReason = '';
        if (deleteReason === 'custom') {
            if (!customReason.trim()) {
                alert('請輸入自定義原因');
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
        handleDeleteAction(finalReason);
    };

    // 編輯按鈕點擊處理（已移除互動檢查，改為直接在按鈕上禁用）
    const handleEditClick = () => {
        // 直接進入編輯模式，不再檢查互動或彈窗
        setIsEditing(true);
    };

    const handleSaveEdit = async () => {
        if (!editContent.trim()) {
            alert('評論內容不能為空');
            return;
        }
        try {
            const res = await fetch(`/api/review/${review.id}/update`, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content: editContent })
            });
            const result = await res.json();

            if (result.success) {
                onReviewUpdated({ ...review, content: editContent });
                setIsEditing(false);
            } else {
                // 🚫 已刪除：攔截「互動阻止」錯誤及相關彈窗代碼

                // 檢查是否為全局禁言
                if (result.message === "GLOBAL_BAN" && result.data && result.data.banned) {
                    if (window.showGlobalBanModal) {
                        window.showGlobalBanModal(currentUsername, result.data.expiresAt);
                    }
                    return;
                }

                // 檢查是否被永久拉黑
                if (result.message === "BLACKLISTED") {
                    if (window.showBlacklistedModal) {
                        window.showBlacklistedModal();
                    }
                    return;
                }

                // 普通錯誤提示
                alert(result.message || '修改失敗');
            }
        } catch (error) {
            console.error('修改評論錯誤:', error);
            alert('網絡錯誤');
        }
    };

    const handlePin = async () => {
        try {
            const res = await fetch(`/api/review/${review.id}/pin`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ pinned: !review.pinned })
            });
            const result = await res.json();
            if (result.success) {
                onReviewUpdated({ ...review, pinned: !review.pinned });
            } else {
                alert(result.message || '置頂操作失敗');
            }
        } catch (error) {
            console.error('置頂錯誤:', error);
            alert('網絡錯誤');
        }
    };

    // 修改：使用父組件傳遞的 onBlockUser 函數，並直接使用 review.customer.username
    const confirmBlock = async (durationMinutes) => {
        const targetUsername = review.customer.username;
        if (isAdmin && durationMinutes) {
            // 管理員禁言邏輯保持不變
            let totalMinutes = 0;
            if (blockUnit === 'day') {
                totalMinutes = durationMinutes * 24 * 60;  //  修正：1 天 = 1440 分钟
            } else if (blockUnit === 'week') {
                totalMinutes = durationMinutes * 7 * 24 * 60;  //  正确：1 周 = 7 天
            } else if (blockUnit === 'month') {
                totalMinutes = durationMinutes * 30 * 24 * 60;  //  正确：1 月 ≈ 30 天
            }
            const params = new URLSearchParams();
            params.append('durationMinutes', totalMinutes);
            params.append('reason', '管理員禁言');
            try {
                const response = await fetch(
                    `/api/user/${targetUsername}/toggle-block?${params}`,
                    { method: 'POST', credentials: 'same-origin' }
                );
                const result = await response.json();
                if (response.ok) {
                    notify(`✅ 已全局禁言用戶 ${blockValue} ${blockUnit === 'day' ? '天' : blockUnit === 'week' ? '週' : '月'}`);
                    // 調用父組件的 onBlockUser 更新共享狀態
                    await onBlockUser(targetUsername, true);
                } else {
                    notify('❌ ' + result.message, true);
                }
            } catch (error) {
                notify('❌ 網絡錯誤', true);
            }
        } else {
            // 普通用戶禁言：直接調用父組件函數，避免 state 非同步問題
            const result = await onBlockUser(targetUsername, true);
            if (result.success) {
                notify('✅ 已禁言該用戶，雙方將無法互相回復');
            } else {
                notify('❌ ' + result.message, true);
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

    // 修改：使用父組件傳遞的 onBlockUser 函數
    const handleUnblock = async () => {
        if (!window.confirm('確定要解除禁言嗎？')) return;
        const targetUsername = review.customer.username;
        const result = await onBlockUser(targetUsername, false);
        if (result.success) {
            notify('✅ 已解除禁言');
        } else {
            notify('❌ ' + result.message, true);
        }
    };

    const handleBlockClick = () => {
        if (isAdmin) {
            setShowBlockModal(true); // 管理員彈出模態框
        } else {
            confirmBlock(null); // 普通用戶直接雙向禁言
        }
    };

    // 🚫 管理員專屬：永久拉黑用戶 (調用 HTML 中的自定義漂亮彈窗)
    const handleBlacklist = (targetUsername) => {
        if (typeof window.openBlacklistConfirmModal === 'function') {
            window.openBlacklistConfirmModal(targetUsername);
        } else {
            notify('❌ 系統彈窗組件未加載，請刷新頁面後重試', true);
        }
    };
    // const handleBlacklist = async (targetUsername) => {
    //if (!window.confirm(`⚠️ 確定要永久拉黑用戶 "${targetUsername}" 嗎？\n 拉黑後該用戶將永遠無法點贊、踩、評價和回復！`)) return;

    // const reason = prompt("請輸入拉黑原因（將發送系統通知給該用戶）：", "嚴重違反社區規範");
    // if (!reason) return;

    // try {
    //   const response = await fetch(`/api/admin/penalty/blacklist/${targetUsername}?reason=${encodeURIComponent(reason)}`, {
    //   method: 'POST',
    //   credentials: 'same-origin'
    //     });
    //    const result = await response.json();
    //     if (response.ok && result.success) {
    //        notify('✅ ' + result.message);
    //    } else {
    //       notify('❌ ' + result.message, true);
    //    }
    // } catch (error) {
    //    notify('❌ 網絡錯誤', true);
    //  }
    // };

    const renderStars = (rating) => {
        if (!rating) return null;
        const full = Math.floor(rating);
        const half = (rating - full) >= 0.5;
        return (
            <div className="review-rating">
                {[1, 2, 3, 4, 5].map(i => {
                    if (i <= full) return <i key={i} className="fas fa-star"></i>;
                    if (i === full + 1 && half) return <i key={i} className="fas fa-star-half-alt"></i>;
                    return <i key={i} className="far fa-star"></i>;
                })}
            </div>
        );
    };

    return (
        <div className={`review-card ${review.pinned ? 'pinned' : ''}`}>
            {/* 頭部：左邊是用戶信息，右邊是點贊/踩/回復/置頂 */}
            <div className="review-header">
                <div className="reviewer-info">
                    <div className="reviewer-name">
                        <i className="fas fa-user-circle"></i> {review.customer.username}
                        {review.customer.username === currentUsername && <span className="me-badge">我</span>}
                    </div>
                    {renderStars(review.rating)}
                    <div className="review-date">{new Date(review.createdAt).toLocaleDateString()}</div>
                </div>
                <div className="review-reactions">
                    <div className="review-reaction-group">
                        <button className={`review-action-btn btn-like ${isLiked ? 'active' : ''}`} onClick={handleLike}>
                            <i className={isLiked ? "fas fa-thumbs-up" : "far fa-thumbs-up"}></i> {likeCount}
                        </button>
                        <button className={`review-action-btn btn-dislike ${isDisliked ? 'active' : ''}`} onClick={handleDislike}>
                            <i className={isDisliked ? "fas fa-thumbs-down" : "far fa-thumbs-down"}></i> {dislikeCount}
                        </button>
                        {/* 保留原有的回復截邏輯 */}
                        <button className="review-action-btn btn-reply" onClick={async () => {
                            // 1. 登錄檢
                            if (!currentUsername || currentUsername === 'anonymousUser') {
                                //  【修改這裡】：替換掉醜陋的 alert
                                if (typeof window.showLoginRequiredModal === 'function') {
                                    window.showLoginRequiredModal();
                                }
                                return;
                            }
                            // 2. 核心攔截：檢查是否被該評論作者禁言
                            const authorUsername = review.customer.username;
                            if (authorUsername !== currentUsername) {
                                try {
                                    // 調用後端已有的 can-reply 接口
                                    const res = await fetch(`/api/user/can-reply/${authorUsername}`, { credentials: 'same-origin' });
                                    const data = await res.json();
                                    // 如果後端返回 canReply 為 false，說明被禁言了
                                    if (data.success && !data.canReply) {
                                        // 調用 HTML 中定義的全局彈窗
                                        if (window.showBlockedModal) {
                                            window.showBlockedModal('您已經被對方禁言，無法回復！！！');
                                        } else {
                                            alert('您已經被對方禁言，無法回復！！！');
                                        }
                                        return; // 阻止後續代碼執行，不彈出回復框
                                    }
                                } catch (e) {
                                    console.error('檢查禁言狀態失敗', e);
                                }
                            }
                            // 3. 檢查通過，正常彈出回復框
                            setShowReplyForm(true);
                        }}>
                            <i className="fas fa-reply"></i> 回復
                        </button>
                        {/* 修改：將管理員的置頂按鈕移到這裡（右上角），跟回復在一起 */}
                        {isAdmin && (
                            <button
                                className={`review-action-btn btn-pin-header ${review.pinned ? 'active' : ''}`}
                                onClick={handlePin}
                                title={review.pinned ? "取消置頂" : "置頂評論"}
                            >
                                <i className="fas fa-thumbtack"></i>
                                <span className="pin-text">{review.pinned ? '取消置頂' : '置頂'}</span>
                            </button>
                        )}
                    </div>
                </div>
            </div>
            {/* 評論內容 / 編輯框 */}
            {isEditing ? (
                <div className="edit-review-form active">
                    <textarea value={editContent} onChange={(e) => setEditContent(e.target.value)} maxLength="1000"></textarea>
                    <div className="form-actions">
                        <button className="btn-cancel" onClick={() => setIsEditing(false)}>取消</button>
                        <button className="btn-save" onClick={handleSaveEdit}>保存修改</button>
                    </div>
                </div>
            ) : (
                /*                <div className="review-content">{review.content}</div> */
/*                <div className="review-content">{review.content}</div> */
                <div className="review-content">
                    {/* 核心修復：如果是富文本，渲染 HTML；否則渲染純文本（兼容舊數據） */}
                    {review.isFormatted && review.formattedContent ? (
                        // 情况 1: 富文本模式 (确保 formattedContent 存在)
                        <span dangerouslySetInnerHTML={{ __html: review.formattedContent }} />
                    ) : (
                        // 情况 2: 普通文本模式 (兼容旧数据，防止 undefined 报错)
                        <span>{review.content || ''}</span>
                    )}
                </div>
            )}
            {/* 底部操作區：修改、刪除 (置頂按鈕已移走) */}
            <div className="review-actions">
                {/* 🔒 修改按鈕：僅作者可見，且僅當未置頂時可見。如果有互動，直接禁用按鈕 */}
                {review.customer.username === currentUsername && !review.pinned && (
                    <button
                        className="btn-edit"
                        onClick={handleEditClick}
                        disabled={hasInteractions()}
                        title={hasInteractions() ? "已有互動（點贊/回覆），無法修改" : ""}
                        style={hasInteractions() ? { opacity: 0.5, cursor: 'not-allowed' } : {}}
                    >
                        <i className="fas fa-edit"></i> 修改
                    </button>
                )}
                {/*
                刪除按鈕邏輯修復：
                1. 作者：只能刪除未置頂的評論 (!review.pinned)
                2. 管理員：隨時可以刪除 (isAdmin)，不受置頂限制
                */}
                {((review.customer.username === currentUsername && !review.pinned) || isAdmin) && (
                    <button className="btn-delete" onClick={handleDeleteClick}>
                        <i className="fas fa-trash-alt"></i> 刪除
                    </button>
                )}
                {/* 禁言/解除禁言按鈕：使用共享的 blockedUsers 狀態 */}
                {currentUsername && currentUsername !== 'anonymousUser' && review.customer.username !== currentUsername && (
                    isAuthorBlocked ? (
                        <button className="btn-unblock" onClick={handleUnblock} title="解除禁言">
                            <i className="fas fa-unlock"></i> 解除禁言
                        </button>
                    ) : (
                        <button className="btn-ban" onClick={handleBlockClick} title={isAdmin ? "全局禁言該用戶" : "雙向禁言該用戶"}>
                            <i className="fas fa-ban"></i> 禁言
                        </button>
                    )
                )}

                {/*  新增：舉報按鈕（僅其他登錄用戶可見，本人和 admin 不可見） */}
                {currentUsername && currentUsername !== 'anonymousUser' &&
                    review.customer.username !== currentUsername &&
                    !isAdmin && (
                        <button
                            className="btn-report"
                            onClick={() => {
                                if (typeof window.openReportModal === 'function') {
                                    window.openReportModal(review.id, review.customer.username, 'REVIEW', review.content);
                                }
                            }}
                            title="舉報不當內容"
                        >
                            <i className="fas fa-flag"></i> 舉報
                        </button>
                    )}

                {/* 拉黑按鈕：僅管理員可見 */}
                {isAdmin && (
                    <button
                        className="btn-blacklist"
                        onClick={() => handleBlacklist(review.customer.username)} // 調用真實函數
                        title="永久拉黑該用戶（加入黑名單）"
                    >
                        <i className="fas fa-user-slash"></i> 永久拉黑
                    </button>
                )}
            </div>
            {/* 把 showReplyForm 和它的設置方法直接傳給子組件 */}
            <ReplySection
                reviewId={review.id}
                initialReplyCount={review.replyCount}
                currentUsername={currentUsername}
                isAdmin={isAdmin}
                productId={productId}
                rootUsername={review.customer.username}
                onReplyCountChange={onReplyCountChange}
                showReplyForm={showReplyForm}
                setShowReplyForm={setShowReplyForm}
                // 傳遞禁言相關 props 給 ReplySection
                blockedUsers={blockedUsers}
                onBlockUser={onBlockUser}
            />
            {/* 新增：管理員刪除確認模態框 */}
            {showDeleteModal && (
                <div style={modalOverlayStyle}>
                    <div style={modalContentStyle}>
                        <h3 style={{ marginBottom: '1rem', color: 'var(--primary)' }}>
                            <i className="fas fa-exclamation-triangle" style={{ color: 'var(--accent)', marginRight: '0.5rem' }}></i>
                            刪除評論確認
                        </h3>
                        <p style={{ marginBottom: '1rem', color: 'var(--gray)' }}>
                            請選擇刪除原因，這將發送通知給用戶 <strong>{review.customer.username}</strong>：
                        </p>
                        <div style={{ marginBottom: '1rem' }}>
                            <label style={{ display: 'block', marginBottom: '0.5rem', cursor: 'pointer' }}>
                                <input type="radio" name="deleteReason" value="inappropriate" checked={deleteReason === 'inappropriate'} onChange={(e) => setDeleteReason(e.target.value)} style={{ marginRight: '0.5rem' }} />
                                內容不合規 (默認)
                            </label>
                            <label style={{ display: 'block', marginBottom: '0.5rem', cursor: 'pointer' }}>
                                <input type="radio" name="deleteReason" value="ads" checked={deleteReason === 'ads'} onChange={(e) => setDeleteReason(e.target.value)} style={{ marginRight: '0.5rem' }} />
                                廣告或推廣信息
                            </label>
                            <label style={{ display: 'block', marginBottom: '0.5rem', cursor: 'pointer' }}>
                                <input type="radio" name="deleteReason" value="irrelevant" checked={deleteReason === 'irrelevant'} onChange={(e) => setDeleteReason(e.target.value)} style={{ marginRight: '0.5rem' }} />
                                與商品無關
                            </label>
                            <label style={{ display: 'block', marginBottom: '0.5rem', cursor: 'pointer' }}>
                                <input type="radio" name="deleteReason" value="custom" checked={deleteReason === 'custom'} onChange={(e) => setDeleteReason(e.target.value)} style={{ marginRight: '0.5rem' }} />
                                自定義原因
                            </label>
                        </div>
                        {deleteReason === 'custom' && (
                            <textarea
                                style={{ width: '100%', minHeight: '80px', padding: '0.5rem', borderRadius: '4px', border: '1px solid #ddd', fontFamily: 'inherit' }}
                                placeholder="請輸入具體的刪除原因..."
                                value={customReason}
                                onChange={(e) => setCustomReason(e.target.value)}
                            />
                        )}
                        <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '1.5rem' }}>
                            <button onClick={() => setShowDeleteModal(false)} style={{ padding: '0.5rem 1rem', background: '#e9ecef', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>取消</button>
                            <button onClick={confirmDeleteWithReason} style={{ padding: '0.5rem 1rem', background: 'var(--accent)', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>確認刪除</button>
                        </div>
                    </div>
                </div>
            )}
            {/* 新增：管理員禁言時長選擇模態框 */}
            {showBlockModal && (
                <div style={modalOverlayStyle} onClick={() => setShowBlockModal(false)}>
                    <div style={modalContentStyle} onClick={e => e.stopPropagation()}>
                        <h3 style={{ marginBottom: '1rem', color: 'var(--primary)' }}>
                            <i className="fas fa-gavel" style={{ color: 'var(--accent)', marginRight: '0.5rem' }}></i>
                            管理員禁言
                        </h3>
                        <p style={{ marginBottom: '1rem', color: 'var(--gray)' }}>
                            請輸入禁言時長，用戶在期間內將無法在任何評論區回復：
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

// 簡單的內聯樣式，確保模態框居中 (復用)
const modalOverlayStyle = {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0, 0, 0, 0.5)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 9999
};
const modalContentStyle = {
    background: 'white',
    padding: '2rem',
    borderRadius: '12px',
    width: '90%',
    maxWidth: '500px',
    boxShadow: '0 10px 25px rgba(0,0,0,0.2)'
};

export default ReviewCard;