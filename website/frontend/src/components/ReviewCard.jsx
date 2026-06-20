import React, { useState } from 'react';
import ReplySection from './ReplySection';

const ReviewCard = ({ review, currentUsername, isAdmin, productId, onReplyCountChange, onReviewDeleted, onReviewUpdated }) => {
    // --- 現有狀態 ---
    const [likeCount, setLikeCount] = useState(review.likeCount || 0);
    const [dislikeCount, setDislikeCount] = useState(review.dislikeCount || 0);
    const [isEditing, setIsEditing] = useState(false);
    const [editContent, setEditContent] = useState(review.content);

    // 🟢 核心修復：使用後端傳來的初始狀態
    const [isLiked, setIsLiked] = useState(review.isLikedByMe || false);
    const [isDisliked, setIsDisliked] = useState(review.isDislikedByMe || false);

    // 🟢 直接管理 showReplyForm
    const [showReplyForm, setShowReplyForm] = useState(false);

    // 🟢 新增：刪除模態框相關狀態
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [deleteReason, setDeleteReason] = useState('');
    const [customReason, setCustomReason] = useState('');

    // --- 現有函數 ---

    const handleLike = async () => {
        try {
            const res = await fetch(`/api/review/${review.id}/like`, { method: 'POST', credentials: 'same-origin' });
            const result = await res.json();
            if (result.success) {
                setLikeCount(result.likeCount);
                setDislikeCount(result.dislikeCount);
                setIsLiked(result.liked);
                setIsDisliked(result.disliked);
            } else {
                alert(result.message || '點贊失敗');
            }
        } catch (error) {
            console.error('點贊網絡錯誤:', error);
            alert('網絡錯誤');
        }
    };

    const handleDislike = async () => {
        try {
            const res = await fetch(`/api/review/${review.id}/dislike`, { method: 'POST', credentials: 'same-origin' });
            const result = await res.json();
            if (result.success) {
                setLikeCount(result.likeCount);
                setDislikeCount(result.dislikeCount);
                setIsLiked(result.liked);
                setIsDisliked(result.disliked);
            } else {
                alert(result.message || '踩失敗');
            }
        } catch (error) {
            console.error('踩網絡錯誤:', error);
            alert('網絡錯誤');
        }
    };

    // 🟢 修改：點擊刪除按鈕時，根據身份決定行為
    const handleDeleteClick = () => {
        if (isAdmin) {
            // 管理員：打開模態框選擇原因
            setShowDeleteModal(true);
            setDeleteReason('inappropriate'); // 默認選項
            setCustomReason('');
        } else {
            // 普通用戶：直接確認刪除
            if(window.confirm('確定刪除這條評論嗎？')) {
                handleDeleteAction();
            }
        }
    };

    // 🟢 核心：執行實際刪除操作的函數 (支持發送原因)
    const handleDeleteAction = async (reasonText = null) => {
        try {
            const body = {};
            // 如果是管理員且提供了原因，則放入請求體
            if (isAdmin && reasonText) {
                body.deleteReason = reasonText;
            }

            const res = await fetch(`/api/review/${review.id}`, {
                method: 'DELETE',
                headers: { 'Content-Type': 'application/json' }, // 重要：發送 JSON
                credentials: 'same-origin',
                body: Object.keys(body).length > 0 ? JSON.stringify(body) : undefined
            });

            const result = await res.json();

            if (res.ok && result.success) {
                setShowDeleteModal(false); // 關閉模態框
                onReviewDeleted(); // 通知父組件刷新
            } else {
                alert(result.message || '刪除失敗');
            }
        } catch (error) {
            console.error('網絡錯誤:', error);
            alert('網絡錯誤，請稍後重試');
        }
    };

    // 🟢 處理模態框中的提交邏輯
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
            {/* 頭部：左邊是用戶信息，右邊是點贊/踩/回復 */}
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
                        <button className="review-action-btn btn-reply" onClick={() => setShowReplyForm(true)}>
                            <i className="fas fa-reply"></i> 回復
                        </button>
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
                <div className="review-content">{review.content}</div>
            )}

            {/* 底部操作區：修改、刪除、置頂 */}
            <div className="review-actions">
                {review.customer.username === currentUsername && !review.pinned && (
                    <button className="btn-edit" onClick={() => setIsEditing(true)}>
                        <i className="fas fa-edit"></i> 修改
                    </button>
                )}

                {(review.customer.username === currentUsername || isAdmin) && !review.pinned && (
                    <button className="btn-delete" onClick={handleDeleteClick}>
                        <i className="fas fa-trash-alt"></i> 刪除
                    </button>
                )}

                {isAdmin && (
                    <button className={`btn-pin ${review.pinned ? 'pinned' : ''}`} onClick={handlePin}>
                        <i className="fas fa-thumbtack"></i> {review.pinned ? '取消置頂' : '置頂'}
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
            />

            {/* 🟢 新增：管理員刪除確認模態框 */}
            {showDeleteModal && (
                <div style={modalOverlayStyle}>
                    <div style={modalContentStyle}>
                        <h3 style={{marginBottom: '1rem', color: 'var(--primary)'}}>
                            <i className="fas fa-exclamation-triangle" style={{color: 'var(--accent)', marginRight: '0.5rem'}}></i>
                            刪除評論確認
                        </h3>
                        <p style={{marginBottom: '1rem', color: 'var(--gray)'}}>
                            請選擇刪除原因，這將發送通知給用戶 <strong>{review.customer.username}</strong>：
                        </p>

                        <div style={{marginBottom: '1rem'}}>
                            <label style={{display: 'block', marginBottom: '0.5rem', cursor: 'pointer'}}>
                                <input
                                    type="radio"
                                    name="deleteReason"
                                    value="inappropriate"
                                    checked={deleteReason === 'inappropriate'}
                                    onChange={(e) => setDeleteReason(e.target.value)}
                                    style={{marginRight: '0.5rem'}}
                                />
                                內容不合規 (默認)
                            </label>
                            <label style={{display: 'block', marginBottom: '0.5rem', cursor: 'pointer'}}>
                                <input
                                    type="radio"
                                    name="deleteReason"
                                    value="ads"
                                    checked={deleteReason === 'ads'}
                                    onChange={(e) => setDeleteReason(e.target.value)}
                                    style={{marginRight: '0.5rem'}}
                                />
                                廣告或推廣信息
                            </label>
                            <label style={{display: 'block', marginBottom: '0.5rem', cursor: 'pointer'}}>
                                <input
                                    type="radio"
                                    name="deleteReason"
                                    value="irrelevant"
                                    checked={deleteReason === 'irrelevant'}
                                    onChange={(e) => setDeleteReason(e.target.value)}
                                    style={{marginRight: '0.5rem'}}
                                />
                                與商品無關
                            </label>
                            <label style={{display: 'block', marginBottom: '0.5rem', cursor: 'pointer'}}>
                                <input
                                    type="radio"
                                    name="deleteReason"
                                    value="custom"
                                    checked={deleteReason === 'custom'}
                                    onChange={(e) => setDeleteReason(e.target.value)}
                                    style={{marginRight: '0.5rem'}}
                                />
                                自定義原因
                            </label>
                        </div>

                        {deleteReason === 'custom' && (
                            <textarea
                                style={{width: '100%', minHeight: '80px', padding: '0.5rem', borderRadius: '4px', border: '1px solid #ddd', fontFamily: 'inherit'}}
                                placeholder="請輸入具體的刪除原因..."
                                value={customReason}
                                onChange={(e) => setCustomReason(e.target.value)}
                            />
                        )}

                        <div style={{display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '1.5rem'}}>
                            <button
                                onClick={() => setShowDeleteModal(false)}
                                style={{padding: '0.5rem 1rem', background: '#e9ecef', border: 'none', borderRadius: '4px', cursor: 'pointer'}}
                            >
                                取消
                            </button>
                            <button
                                onClick={confirmDeleteWithReason}
                                style={{padding: '0.5rem 1rem', background: 'var(--accent)', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer'}}
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

// 🟢 簡單的內聯樣式，確保模態框居中
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