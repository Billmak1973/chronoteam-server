import React, { useState } from 'react';
import ReplySection from './ReplySection';

const ReviewCard = ({ review, currentUsername, isAdmin, productId, onReplyCountChange, onReviewDeleted, onReviewUpdated }) => {
    // --- 现有状态 ---
    const [likeCount, setLikeCount] = useState(review.likeCount || 0);
    const [dislikeCount, setDislikeCount] = useState(review.dislikeCount || 0);
    const [isEditing, setIsEditing] = useState(false);
    const [editContent, setEditContent] = useState(review.content);

    // 核心修复：使用后端传来的初始状态
    const [isLiked, setIsLiked] = useState(review.isLikedByMe || false);
    const [isDisliked, setIsDisliked] = useState(review.isDislikedByMe || false);

    //  直接管理 showReplyForm
    const [showReplyForm, setShowReplyForm] = useState(false);

    //  新增：删除模态框相关状态
    const [showDeleteModal, setShowDeleteModal] = useState(false);
    const [deleteReason, setDeleteReason] = useState('');
    const [customReason, setCustomReason] = useState('');

    // 🟢 新增：禁言相关状态
    const [showBlockModal, setShowBlockModal] = useState(false);
    const [blockValue, setBlockValue] = useState(1);
    const [blockUnit, setBlockUnit] = useState('day');
    const [isBlocked, setIsBlocked] = useState(false);

    // --- 现有函数 ---

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
                alert(result.message || '点赞失败');
            }
        } catch (error) {
            console.error('点赞网络错误:', error);
            alert('网络错误');
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
                alert(result.message || '踩失败');
            }
        } catch (error) {
            console.error('踩网络错误:', error);
            alert('网络错误');
        }
    };

    //  修改：点击删除按钮时，根据身份决定行为
    const handleDeleteClick = () => {
        if (isAdmin) {
            // 管理员：打开模态框选择原因
            setShowDeleteModal(true);
            setDeleteReason('inappropriate'); // 默认选项
            setCustomReason('');
        } else {
            // 普通用户：直接确认删除
            if(window.confirm('确定删除这条评论吗？')) {
                handleDeleteAction();
            }
        }
    };

    //  核心：执行实际删除操作的函数 (支持发送原因)
    const handleDeleteAction = async (reasonText = null) => {
        try {
            const body = {};
            // 如果是管理员且提供了原因，则放入请求体
            if (isAdmin && reasonText) {
                body.deleteReason = reasonText;
            }

            const res = await fetch(`/api/review/${review.id}`, {
                method: 'DELETE',
                headers: { 'Content-Type': 'application/json' }, // 重要：发送 JSON
                credentials: 'same-origin',
                body: Object.keys(body).length > 0 ? JSON.stringify(body) : undefined
            });

            const result = await res.json();

            if (res.ok && result.success) {
                setShowDeleteModal(false); // 关闭模态框
                onReviewDeleted(); // 通知父组件刷新
            } else {
                alert(result.message || '删除失败');
            }
        } catch (error) {
            console.error('网络错误:', error);
            alert('网络错误，请稍后重试');
        }
    };

    //  处理模态框中的提交逻辑
    const confirmDeleteWithReason = () => {
        let finalReason = '';
        if (deleteReason === 'custom') {
            if (!customReason.trim()) {
                alert('请输入自定义原因');
                return;
            }
            finalReason = customReason;
        } else {
            // 根据选项映射为更委婉的文字
            switch (deleteReason) {
                case 'inappropriate':
                    finalReason = '该内容不符合社区规范，因此被移除。';
                    break;
                case 'ads':
                    finalReason = '检测到广告或推广信息，因此被移除。';
                    break;
                case 'irrelevant':
                    finalReason = '该评论与商品无关，因此被移除。';
                    break;
                default:
                    finalReason = '该内容不符合社区规范，因此被移除。';
            }
        }
        handleDeleteAction(finalReason);
    };

    const handleSaveEdit = async () => {
        if (!editContent.trim()) {
            alert('评论内容不能为空');
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
                alert(result.message || '修改失败');
            }
        } catch (error) {
            console.error('修改评论错误:', error);
            alert('网络错误');
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
                alert(result.message || '置顶操作失败');
            }
        } catch (error) {
            console.error('置顶错误:', error);
            alert('网络错误');
        }
    };

    // 🟢 新增：禁言相关函数
    const confirmBlock = async (durationMinutes) => {
        try {
            const params = new URLSearchParams();
            if (isAdmin && durationMinutes) {
                params.append('durationMinutes', durationMinutes);
                params.append('reason', '管理员禁言');
            }

            const response = await fetch(
                `/api/user/${review.customer.username}/toggle-block?${params}`,
                { method: 'POST', credentials: 'same-origin' }
            );

            const result = await response.json();
            if (response.ok) {
                showNotification(isAdmin ?
                    `✅ 已全局禁言用户 ${blockValue} ${blockUnit === 'day' ? '天' : blockUnit === 'week' ? '周' : '月'}` :
                    '✅ 已禁言该用户，双方将无法互相回复');
                setIsBlocked(true);
            } else {
                showNotification('❌ ' + result.message, true);
            }
        } catch (error) {
            showNotification('❌ 网络错误', true);
        }
        setShowBlockModal(false);
    };

    const handleConfirmBlock = () => {
        const val = parseInt(blockValue);
        if (!val || val <= 0) {
            showNotification('❌ 请输入有效的数字', true);
            return;
        }

        let totalMinutes = 0;
        if (blockUnit === 'day') {
            totalMinutes = val * 24 * 60;
        } else if (blockUnit === 'week') {
            totalMinutes = val * 7 * 24 * 60;
        } else if (blockUnit === 'month') {
            totalMinutes = val * 30 * 24 * 60;
        }

        confirmBlock(totalMinutes);
    };

    const handleUnblock = async () => {
        if (!window.confirm('确定要解除禁言吗？')) return;
        try {
            const response = await fetch(`/api/user/${review.customer.username}/unblock`, {
                method: 'DELETE',
                credentials: 'same-origin'
            });
            if (response.ok) {
                showNotification('✅ 已解除禁言');
                setIsBlocked(false);
            } else {
                const result = await response.json();
                showNotification('❌ ' + (result.message || '解除失败'), true);
            }
        } catch (error) {
            showNotification('❌ 网络错误', true);
        }
    };

    const handleBlockClick = () => {
        if (isAdmin) {
            setShowBlockModal(true); // 管理员弹出模态框
        } else {
            confirmBlock(null); // 普通用户直接双向禁言
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
            {/* 头部：左边是用户信息，右边是点赞/踩/回复/置顶 */}
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
                        <button className="review-action-btn btn-reply" onClick={() =>{
                                //  核心拦截：判断是否未登录
                                if (!currentUsername || currentUsername === 'anonymousUser') {
                                    alert('⚠️ 请先登录！未登录用户不能回复。');
                                    // 如果你有全局的登录弹窗函数，可以在这里调用，例如：
                                    // if (typeof openLoginModal === 'function') openLoginModal();
                                    return;
                                }
                            setShowReplyForm(true);
                            }}>
                            <i className="fas fa-reply"></i> 回复
                        </button>

                        {/* 修改：将管理员的置顶按钮移到这里（右上角），跟回复在一起 （這裏格局不能改）*/}
                        {isAdmin && (
                            <button
                                className={`review-action-btn btn-pin-header ${review.pinned ? 'active' : ''}`}
                                onClick={handlePin}
                                title={review.pinned ? "取消置顶" : "置顶评论"}
                            >
                                <i className="fas fa-thumbtack"></i>
                                <span className="pin-text">{review.pinned ? '取消置顶' : '置顶'}</span>
                            </button>
                        )}
                    </div>
                </div>
            </div>

            {/* 评论内容 / 编辑框 */}
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

            {/* 底部操作区：修改、删除 (置顶按钮已移走) */}
            <div className="review-actions">
                {/* 修改按钮：仅作者可见，且仅当未置顶时可见 */}
                {review.customer.username === currentUsername && !review.pinned && (
                    <button className="btn-edit" onClick={() => setIsEditing(true)}>
                        <i className="fas fa-edit"></i> 修改
                    </button>
                )}

                {/*
                   删除按钮逻辑修复：
                   1. 作者：只能删除未置顶的评论 (!review.pinned)
                   2. 管理员：随时可以删除 (isAdmin)，不受置顶限制
                */}
                {((review.customer.username === currentUsername && !review.pinned) || isAdmin) && (
                    <button className="btn-delete" onClick={handleDeleteClick}>
                        <i className="fas fa-trash-alt"></i> 删除
                    </button>
                )}

                {/*  禁言/解除禁言按钮：只要不是自己的评论就能看到  這裏有問題*/}
                {currentUsername && currentUsername !== 'anonymousUser' && review.customer.username !== currentUsername && (
                    isBlocked ? (
                        <button className="btn-unblock" onClick={handleUnblock} title="解除禁言">
                            <i className="fas fa-unlock"></i> 解除禁言
                        </button>
                    ) : (
                        <button className="btn-ban" onClick={handleBlockClick} title={isAdmin ? "全局禁言该用户" : "双向禁言该用户"}>
                            <i className="fas fa-ban"></i> 禁言
                        </button>
                    )
                )}

                {/*  拉黑按钮：仅管理员可见 */}
                {isAdmin && (
                    <button
                        className="btn-blacklist"
                        onClick={() => showNotification(`🚫 拉黑用戶 ${review.customer.username} 功能開發中`)}
                        title="拉黑用戶（加入黑名單）"
                    >
                        <i className="fas fa-user-slash"></i> 拉黑
                    </button>
                )}
            </div>

            {/* 把 showReplyForm 和它的设置方法直接传给子组件 */}
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

            {/* 🟢 新增：管理员删除确认模态框 */}
            {showDeleteModal && (
                <div style={modalOverlayStyle}>
                    <div style={modalContentStyle}>
                        <h3 style={{marginBottom: '1rem', color: 'var(--primary)'}}>
                            <i className="fas fa-exclamation-triangle" style={{color: 'var(--accent)', marginRight: '0.5rem'}}></i>
                            删除评论确认
                        </h3>
                        <p style={{marginBottom: '1rem', color: 'var(--gray)'}}>
                            请选择删除原因，这将发送通知给用户 <strong>{review.customer.username}</strong>：
                        </p>

                        <div style={{marginBottom: '1rem'}}>
                            <label style={{display: 'block', marginBottom: '0.5rem', cursor: 'pointer'}}>
                                <input type="radio" name="deleteReason" value="inappropriate" checked={deleteReason === 'inappropriate'} onChange={(e) => setDeleteReason(e.target.value)} style={{marginRight: '0.5rem'}} />
                                内容不合规 (默认)
                            </label>
                            <label style={{display: 'block', marginBottom: '0.5rem', cursor: 'pointer'}}>
                                <input type="radio" name="deleteReason" value="ads" checked={deleteReason === 'ads'} onChange={(e) => setDeleteReason(e.target.value)} style={{marginRight: '0.5rem'}} />
                                广告或推广信息
                            </label>
                            <label style={{display: 'block', marginBottom: '0.5rem', cursor: 'pointer'}}>
                                <input type="radio" name="deleteReason" value="irrelevant" checked={deleteReason === 'irrelevant'} onChange={(e) => setDeleteReason(e.target.value)} style={{marginRight: '0.5rem'}} />
                                与商品无关
                            </label>
                            <label style={{display: 'block', marginBottom: '0.5rem', cursor: 'pointer'}}>
                                <input type="radio" name="deleteReason" value="custom" checked={deleteReason === 'custom'} onChange={(e) => setDeleteReason(e.target.value)} style={{marginRight: '0.5rem'}} />
                                自定义原因
                            </label>
                        </div>

                        {deleteReason === 'custom' && (
                            <textarea
                                style={{width: '100%', minHeight: '80px', padding: '0.5rem', borderRadius: '4px', border: '1px solid #ddd', fontFamily: 'inherit'}}
                                placeholder="请输入具体的删除原因..."
                                value={customReason}
                                onChange={(e) => setCustomReason(e.target.value)}
                            />
                        )}

                        <div style={{display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '1.5rem'}}>
                            <button onClick={() => setShowDeleteModal(false)} style={{padding: '0.5rem 1rem', background: '#e9ecef', border: 'none', borderRadius: '4px', cursor: 'pointer'}}>取消</button>
                            <button onClick={confirmDeleteWithReason} style={{padding: '0.5rem 1rem', background: 'var(--accent)', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer'}}>确认删除</button>
                        </div>
                    </div>
                </div>
            )}

            {/* 🟢 新增：管理员禁言时长选择模态框 */}
            {showBlockModal && (
                <div style={modalOverlayStyle} onClick={() => setShowBlockModal(false)}>
                    <div style={modalContentStyle} onClick={e => e.stopPropagation()}>
                        <h3 style={{marginBottom: '1rem', color: 'var(--primary)'}}>
                            <i className="fas fa-gavel" style={{color: 'var(--accent)', marginRight: '0.5rem'}}></i>
                            管理员禁言
                        </h3>
                        <p style={{marginBottom: '1rem', color: 'var(--gray)'}}>
                            请输入禁言时长，用户在期间内将无法在任何评论区回复：
                        </p>

                        <div style={{ display: 'flex', gap: '1rem', alignItems: 'center', margin: '1.5rem 0' }}>
                            <input
                                type="number"
                                min="1"
                                value={blockValue}
                                onChange={(e) => setBlockValue(e.target.value)}
                                placeholder="请输入数字"
                                style={{ flex: 1, padding: '0.5rem', border: '1px solid #ddd', borderRadius: '4px' }}
                            />
                            <select
                                value={blockUnit}
                                onChange={(e) => setBlockUnit(e.target.value)}
                                style={{ padding: '0.5rem', border: '1px solid #ddd', borderRadius: '4px', minWidth: '80px' }}
                            >
                                <option value="day">天</option>
                                <option value="week">周</option>
                                <option value="month">月</option>
                            </select>
                        </div>

                        <div style={{display: 'flex', gap: '1rem', justifyContent: 'flex-end', marginTop: '1.5rem'}}>
                            <button
                                onClick={() => setShowBlockModal(false)}
                                style={{padding: '0.5rem 1rem', background: '#e9ecef', border: 'none', borderRadius: '4px', cursor: 'pointer'}}
                            >
                                取消
                            </button>
                            <button
                                onClick={handleConfirmBlock}
                                style={{padding: '0.5rem 1rem', background: 'var(--accent)', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer'}}
                            >
                                确认禁言
                            </button>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
};

// 🟢 简单的内联样式，确保模态框居中 (复用)
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