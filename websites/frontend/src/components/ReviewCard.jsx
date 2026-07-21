import React, { useState, useCallback, useRef } from 'react';
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
    // 【新增】富文本編輯模式狀態：'text' 或 'rich'
    const [editMode, setEditMode] = useState('text');
    // 【新增】富文本編輯器的 ref
    const editorRef = useRef(null);
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

    // 【新增 2】用於記錄當前操作封禁的目標用戶名
    const [targetBlockUser, setTargetBlockUser] = useState('');

    // 不再需要本地 isBlocked 狀態，改用 props 中的 blockedUsers
    // 檢查當前評論作者是否被禁言
    const isAuthorBlocked = blockedUsers && blockedUsers.includes(review.customer.username);

    // 檢查評論是否有互動（點贊/踩/回覆）
    // 核心修復：必須使用局部的 likeCount 和 dislikeCount state！
    const hasInteractions = useCallback(() => {
        if (likeCount > 0 || dislikeCount > 0) {
            return true;
        }
        if (review.replyCount > 0) {
            return true;
        }
        return false;
    }, [likeCount, dislikeCount, review.replyCount]);

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
                if (result.message === "BLACKLISTED" || result.blacklisted) {
                    if (window.showBlacklistedModal) {
                        window.showBlacklistedModal();
                    }
                    return;
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
                if (result.message === "BLACKLISTED" || result.blacklisted) {
                    if (window.showBlacklistedModal) {
                        window.showBlacklistedModal();
                    }
                    return;
                }
                alert(result.message || '踩失敗');
            }
        } catch (error) {
            console.error('踩網絡錯誤:', error);
            alert('網絡錯誤');
        }
    };

    const handleDeleteAction = async (reasonText = null) => {
        try {
            const body = {};
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
                setShowDeleteModal(false);
                if (typeof window.showNotification === 'function') {
                    window.showNotification('✅ 評論已刪除，頁面將刷新...');
                }
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

    const handleDeleteClick = () => {
        if (isAdmin) {
            setShowDeleteModal(true);
            setDeleteReason('inappropriate');
            setCustomReason('');
        } else {
            window.openDeleteModal('確定刪除這條評論嗎？此操作無法恢復！', async () => {
                try {
                    const res = await fetch(`/api/review/${review.id}`, {
                        method: 'DELETE',
                        credentials: 'same-origin'
                    });
                    const result = await res.json();
                    if (result.success) {
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

    const confirmDeleteWithReason = () => {
        let finalReason = '';
        if (deleteReason === 'custom') {
            if (!customReason.trim()) {
                alert('請輸入自定義原因');
                return;
            }
            finalReason = customReason;
        } else {
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

    const handleEditClick = () => {
        if (isAdmin) {
            setEditMode('rich');
            setEditContent(review.formattedContent || review.content);
            setIsEditing(true);
        } else {
            setEditMode('text');
            setEditContent(review.content);
            setIsEditing(true);
        }
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
                credentials: 'same-origin',
                body: JSON.stringify({
                    content: editContent,
                    isFormatted: editMode === 'rich'
                })
            });
            const result = await res.json();

            if (result.success) {
                setLikeCount(review.likeCount);
                setDislikeCount(review.dislikeCount);
                onReviewUpdated({
                    ...review,
                    content: editContent,
                    formattedContent: editMode === 'rich' ? editContent : null,
                    isFormatted: editMode === 'rich'
                });
                setIsEditing(false);

                if (typeof window.refreshReviews === 'function') {
                    window.refreshReviews();
                }
            } else {
                if (result.message === "GLOBAL_BAN" && result.data && result.data.banned) {
                    if (window.showGlobalBanModal) {
                        window.showGlobalBanModal(currentUsername, result.data.expiresAt);
                    }
                    return;
                }
                if (result.message === "BLACKLISTED") {
                    if (window.showBlacklistedModal) {
                        window.showBlacklistedModal();
                    }
                    return;
                }
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

    const confirmBlock = async (durationMinutes) => {
        const targetUsername = review.customer.username;
        if (isAdmin && durationMinutes) {
            let totalMinutes = 0;
            if (blockUnit === 'day') {
                totalMinutes = durationMinutes * 24 * 60;
            } else if (blockUnit === 'week') {
                totalMinutes = durationMinutes * 7 * 24 * 60;
            } else if (blockUnit === 'month') {
                totalMinutes = durationMinutes * 30 * 24 * 60;
            }
            const params = new URLSearchParams();
            params.append('durationMinutes', totalMinutes);
            params.append('reason', '管理員禁言');
             params.append('reviewId', review.id);
             params.append('reviewContent', review.content); // 這裡的 content 是純文本，長度適中，適合 URL 傳遞
            try {
                const response = await fetch(
                    `/api/user/${targetUsername}/toggle-block?${params}`,
                    { method: 'POST', credentials: 'same-origin' }
                );
                const result = await response.json();
                            if (result.alreadyBanned) {
                                // 彈出警告彈窗（復用 adminPermissionModal）
                                const modal = document.getElementById('adminPermissionModal');
                                if (modal) {
                                    modal.style.display = 'flex';
                                    document.body.style.overflow = 'hidden';
                                }
                                return; // 阻止後續操作，不關閉 showBlockModal，讓管理員看到警告
                            }
                if (response.ok) {
                    notify(`✅ 已全局禁言用戶 ${blockValue} ${blockUnit === 'day' ? '天' : blockUnit === 'week' ? '週' : '月'}`);
                    await onBlockUser(targetUsername, true);
                } else {
                    notify('❌ ' + result.message, true);
                }
            } catch (error) {
                notify('❌ 網絡錯誤', true);
            }
        } else {
            const result = await onBlockUser(targetUsername, true);
            if (result.success) {
                notify('✅ 已禁言該用戶，雙方將無法互相回復');
            } else {
                notify('❌ ' + result.message, true);
            }
        }
        setShowBlockModal(false);
    };

    // 【修改 2】確認封禁 (選時長模態框的確認按鈕)
    const handleConfirmBlock = () => {
        const val = parseInt(blockValue);
        if (!val || val <= 0) {
            notify('❌ 請輸入有效的數字', true);
            return;
        }
        confirmBlock(val);

    };

    const handleUnblock = async () => {
        // 0. 【關鍵修復】：必須在最開頭定義 targetUsername，否則後面會報錯！
        const targetUsername = review.customer.username;

        // 1. 【核心攔截】：如果是管理員，直接彈出權限提示並阻止操作
        if (isAdmin) {
            const modal = document.getElementById('adminPermissionModal');
            if (modal) {
                modal.style.display = 'flex';
                document.body.style.overflow = 'hidden'; // 禁止背景滾動
            }
            return; // 攔截，不執行後續的解除禁言邏輯
        }

        // 2. 普通用戶的解除禁言邏輯
        if (typeof window.openUnblockModal === 'function') {
            // 調用我們在 HTML 中定義的全局漂亮彈窗
            window.openUnblockModal(targetUsername, async (username) => {
                const result = await onBlockUser(username, false);
                if (result.success) {
                    notify('✅ 已解除禁言');
                } else {
                    notify('❌ ' + result.message, true);
                }
            });
        } else {
            // 降級處理：萬一全局函數沒加載出來，使用原生 confirm
            if (!window.confirm('確定要解除禁言嗎？')) return;
            const result = await onBlockUser(targetUsername, false);
            if (result.success) {
                notify('✅ 已解除禁言');
            } else {
                notify('❌ ' + result.message, true);
            }
        };
        // 3. 【關鍵修復】：執行完上面的邏輯後必須 return，防止代碼繼續往下跑造成重複請求！
        return;
    };

    // 【修改 2】禁言/解除禁言按鈕點擊處理
    const handleBlockClick = (targetUsername) => {
        setTargetBlockUser(targetUsername); // 僅用於管理員模態框顯示

        if (isAdmin) {
                setShowBlockModal(true)
        } else {
            // 普通用戶邏輯保持不變 (雙向禁言)
            onBlockUser(targetUsername, true).then(result => {
                if (result.success) {
                    notify('✅ 已禁言該用戶，雙方將無法互相回復');
                } else {
                    notify('❌ ' + result.message, true);
                }
            });
        }
    };

    // 【新增 2】確認再次封禁的函數 (被 adminPermissionModal 的按鈕調用)
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

    // 管理員專屬：永久拉黑用戶 (調用 HTML 中的自定義漂亮彈窗)
    const handleBlacklist = (targetUsername) => {
        if (typeof window.openBlacklistConfirmModal === 'function') {
            window.openBlacklistConfirmModal(targetUsername);
        } else {
            notify('❌ 系統彈窗組件未加載，請刷新頁面後重試', true);
        }
    };

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

    // 富文本格式化功能
    const formatText = (command, value = null) => {
        document.execCommand(command, false, value);
        if (editorRef.current) {
            setEditContent(editorRef.current.innerHTML);
        }
        editorRef.current?.focus();
    };

    const insertLink = () => {
        const url = prompt('請輸入連結地址：', 'https://');
        if (url) {
            formatText('createLink', url);
        }
    };

    // 【新增】工具栏按钮样式
    const toolbarBtnStyle = {
        padding: '0.4rem 0.8rem',
        border: '1px solid #ddd',
        borderRadius: '4px',
        backgroundColor: '#fff',
        cursor: 'pointer',
        fontSize: '0.9rem',
        transition: 'all 0.2s',
        minWidth: '40px'
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
                        <button className="review-action-btn btn-reply" onClick={async () => {
                            if (!currentUsername || currentUsername === 'anonymousUser') {
                                if (typeof window.showLoginRequiredModal === 'function') {
                                    window.showLoginRequiredModal();
                                }
                                return;
                            }
                            const authorUsername = review.customer.username;
                            if (authorUsername !== currentUsername) {
                                try {
                                    const res = await fetch(`/api/user/can-reply/${authorUsername}`, { credentials: 'same-origin' });
                                    const data = await res.json();
                                    if (data.success && !data.canReply) {
                                        if (window.showBlockedModal) {
                                            window.showBlockedModal('您已經被對方禁言，無法回復！！！');
                                        } else {
                                            alert('您已經被對方禁言，無法回復！！！');
                                        }
                                        return;
                                    }
                                } catch (e) {
                                    console.error('檢查禁言狀態失敗', e);
                                }
                            }
                            setShowReplyForm(true);
                        }}>
                            <i className="fas fa-reply"></i> 回復
                        </button>
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

            {/* 評論內容 / 編輯框：整合富文本編輯器 */}
            {isEditing ? (
                <div className="edit-container" style={{
                    border: '2px solid var(--gold)',
                    borderRadius: '8px',
                    padding: '1rem',
                    marginBottom: '1rem',
                    backgroundColor: '#fff'
                }}>
                    {editMode === 'rich' && (
                        <div className="editor-toolbar" style={{
                            display: 'flex',
                            gap: '0.5rem',
                            marginBottom: '1rem',
                            padding: '0.5rem',
                            backgroundColor: '#f8f9fa',
                            borderRadius: '6px',
                            flexWrap: 'wrap'
                        }}>
                            <button type="button" onClick={() => formatText('bold')} style={toolbarBtnStyle} title="粗體"><b>B</b></button>
                            <button type="button" onClick={() => formatText('italic')} style={toolbarBtnStyle} title="斜體"><i>I</i></button>
                            <button type="button" onClick={() => formatText('underline')} style={toolbarBtnStyle} title="下劃線"><u>U</u></button>
                            <div style={{ width: '1px', backgroundColor: '#ddd', margin: '0 0.25rem' }}></div>
                            <button type="button" onClick={() => formatText('foreColor', '#e94560')} style={{...toolbarBtnStyle, color: '#e94560'}} title="紅色字體">A</button>
                            <button type="button" onClick={() => formatText('hiliteColor', '#fff3cd')} style={{...toolbarBtnStyle, backgroundColor: '#fff3cd'}} title="背景色">A</button>
                            <div style={{ width: '1px', backgroundColor: '#ddd', margin: '0 0.25rem' }}></div>
                            <button type="button" onClick={() => formatText('insertUnorderedList')} style={toolbarBtnStyle} title="列表">• List</button>
                            <button type="button" onClick={insertLink} style={toolbarBtnStyle} title="插入連結">🔗</button>
                        </div>
                    )}

                    {editMode === 'rich' ? (
                        <div
                            ref={editorRef}
                            contentEditable
                            dangerouslySetInnerHTML={{ __html: editContent }}
                            onInput={(e) => setEditContent(e.target.innerHTML)}
                            style={{
                                minHeight: '150px',
                                padding: '1rem',
                                border: '1px solid #ddd',
                                borderRadius: '6px',
                                outline: 'none',
                                backgroundColor: '#fff'
                            }}
                        />
                    ) : (
                        <textarea
                            value={editContent}
                            onChange={(e) => setEditContent(e.target.value)}
                            maxLength="1000"
                            style={{
                                width: '100%',
                                minHeight: '150px',
                                padding: '1rem',
                                border: '1px solid #ddd',
                                borderRadius: '6px',
                                resize: 'vertical',
                                fontFamily: 'inherit'
                            }}
                        />
                    )}

                    <div style={{ display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '1rem' }}>
                        <button
                            onClick={() => setIsEditing(false)}
                            style={{
                                padding: '0.6rem 1.5rem',
                                border: 'none',
                                borderRadius: '6px',
                                backgroundColor: '#e9ecef',
                                color: '#495057',
                                cursor: 'pointer',
                                fontWeight: '500'
                            }}
                        >
                            取消
                        </button>
                        <button
                            onClick={handleSaveEdit}
                            style={{
                                padding: '0.6rem 1.5rem',
                                border: 'none',
                                borderRadius: '6px',
                                backgroundColor: 'var(--gold)',
                                color: 'var(--primary)',
                                cursor: 'pointer',
                                fontWeight: '600'
                            }}
                        >
                            保存修改
                        </button>
                    </div>
                </div>
            ) : (
                <div className="review-content">
                    {review.isFormatted && review.formattedContent ? (
                        <span dangerouslySetInnerHTML={{ __html: review.formattedContent }} />
                    ) : (
                        <span>{review.content || ''}</span>
                    )}
                </div>
            )}

            {/* 底部操作區：修改、刪除 (置頂按鈕已移走) */}
            <div className="review-actions">
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

                {((review.customer.username === currentUsername && !review.pinned) || isAdmin) && (
                    <button className="btn-delete" onClick={handleDeleteClick}>
                        <i className="fas fa-trash-alt"></i> 刪除
                    </button>
                )}

                {/* 【核心修改 2】：禁言/解除禁言按鈕渲染邏輯 */}
                {currentUsername && currentUsername !== 'anonymousUser' && review.customer.username !== currentUsername && (
                    // 如果是管理員，永遠顯示「禁言」按鈕
                    isAdmin ? (
                        <button
                            className="btn-ban"
                            onClick={() => handleBlockClick(review.customer.username)}
                            title="全局禁言該用戶"
                        >
                            <i className="fas fa-ban"></i> 禁言
                        </button>
                    ) : (
                        // 普通用戶：根據 blockedUsers 狀態顯示「禁言」或「解除禁言」
                        blockedUsers && blockedUsers.includes(review.customer.username) ? (
                            <button
                                className="btn-unblock"
                                onClick={handleUnblock}
                                title="解除禁言"
                            >
                                <i className="fas fa-unlock"></i> 解除禁言
                            </button>
                        ) : (
                            <button
                                className="btn-ban"
                                onClick={() => handleBlockClick(review.customer.username)}
                                title="雙向禁言該用戶"
                            >
                                <i className="fas fa-ban"></i> 禁言
                            </button>
                        )
                    )
                )}

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

                {isAdmin && (
                    <button
                        className="btn-blacklist"
                        onClick={() => handleBlacklist(review.customer.username)}
                        title="永久拉黑該用戶（加入黑名單）"
                    >
                        <i className="fas fa-user-slash"></i> 永久拉黑
                    </button>
                )}
            </div>

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
                blockedUsers={blockedUsers}
                onBlockUser={onBlockUser}
            />

            {/* 管理員刪除確認模態框 */}
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

            {/* 管理員禁言時長選擇模態框 */}
            {showBlockModal && (
                <div style={modalOverlayStyle} onClick={() => setShowBlockModal(false)}>
                    <div style={modalContentStyle} onClick={e => e.stopPropagation()}>
                        <h3 style={{ marginBottom: '1rem', color: 'var(--primary)' }}>
                            <i className="fas fa-gavel" style={{ color: 'var(--accent)', marginRight: '0.5rem' }}></i>
                            管理員禁言
                        </h3>
                        <p style={{ marginBottom: '1rem', color: 'var(--gray)' }}>
                            請輸入禁言時長，用戶 <strong>{targetBlockUser}</strong> 在期間內將無法在任何評論區回復：
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