import React, { useState, useEffect, useCallback } from 'react';

const ReplySection = ({
  reviewId,
  initialReplyCount,
  currentUsername,
  isAdmin,
  productId,
  rootUsername,
  onReplyCountChange,
  showReplyForm,        // 接收父組件傳來的狀態
  setShowReplyForm      // 接收父組件傳來的設置方法
}) => {
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

  // 🟢 新增：禁言相關狀態
  const [showBlockModal, setShowBlockModal] = useState(false);
  const [targetBlockUser, setTargetBlockUser] = useState('');
  const [blockValue, setBlockValue] = useState(1);
  const [blockUnit, setBlockUnit] = useState('day');
  const [blockedUsers, setBlockedUsers] = useState([]); // 記錄當前頁面已禁言的用戶

  // 【核心修復】當 showReplyForm 變成 true 時，根據是否有回覆決定是否展開
  useEffect(() => {
    if (showReplyForm) {
      // 只有當已有回覆時，才展開評論區列表
      if (initialReplyCount > 0) {
        setIsOpen(true);
      } else {
        setIsOpen(false); //  沒有回覆時，不展開列表區域，直接顯示表單
      }
      setReplyParentId(null);
      if (rootUsername) setReplyToUser(rootUsername);
    }
  }, [showReplyForm, rootUsername, initialReplyCount]); //  加入 initialReplyCount 依賴

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
          parentId: replyParentId || reviewId,
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

        // 提交成功後，如果原本沒展開（initialReplyCount === 0 的情況），現在有1條了
        // 但表單已關閉，所以不需要自動展開，除非用戶再次點擊回覆
        if (result.data && page === 0 && isOpen) {
          setReplies(prev => [result.data, ...prev]);
        } else if (isOpen) {
          await fetchReplies(0, 'newest');
        }
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

  // 🟢 新增：禁言相關函數
  const confirmBlock = async (durationMinutes) => {
    try {
      const params = new URLSearchParams();
      if (isAdmin && durationMinutes) {
        params.append('durationMinutes', durationMinutes);
        params.append('reason', '管理員禁言');
      }

      const response = await fetch(
        `/api/user/${targetBlockUser}/toggle-block?${params}`,
        { method: 'POST', credentials: 'same-origin' }
      );

      const result = await response.json();
      if (response.ok) {
        showNotification(isAdmin ?
          `✅ 已全局禁言用戶 ${blockValue} ${blockUnit === 'day' ? '天' : blockUnit === 'week' ? '週' : '月'}` :
          '✅ 已禁言該用戶，雙方將無法互相回覆');
        // 更新本地狀態，將按鈕切換為「解除禁言」
        setBlockedUsers(prev => [...prev, targetBlockUser]);
      } else {
        showNotification('❌ ' + result.message, true);
      }
    } catch (error) {
      showNotification('❌ 網絡錯誤', true);
    }
    setShowBlockModal(false);
  };

  const handleConfirmBlock = () => {
    const val = parseInt(blockValue);
    if (!val || val <= 0) {
      showNotification('❌ 請輸入有效的數字', true);
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

  const handleUnblock = async (targetUsername) => {
    if (!window.confirm('確定要解除禁言嗎？')) return;
    try {
      const response = await fetch(`/api/user/${targetUsername}/unblock`, {
        method: 'DELETE',
        credentials: 'same-origin'
      });
      if (response.ok) {
        showNotification('✅ 已解除禁言');
        setBlockedUsers(prev => prev.filter(u => u !== targetUsername));
      } else {
        const result = await response.json();
        showNotification('❌ ' + (result.message || '解除失敗'), true);
      }
    } catch (error) {
      showNotification('❌ 網絡錯誤', true);
    }
  };

  const handleBlockClick = (targetUsername) => {
    setTargetBlockUser(targetUsername);
    if (isAdmin) {
      setShowBlockModal(true); // 管理員彈出模態框
    } else {
      confirmBlock(null); // 普通用戶直接雙向禁言
    }
  };

  const formatDate = (dateStr) => {
    if(!dateStr) return '';
    const d = new Date(dateStr);
    return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
  };

  return (
    <div className="reply-section-wrapper">

      {/*  工具欄：只有當有回覆記錄或已展開時才顯示 */}
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

      {/* 🟢 列表區域：只有展開時才顯示 */}
      {isOpen && (
        <div className="reply-list-container">
          {/* 情況1：回復「根評論」，表單顯示在列表最上方 (僅在展開時) */}
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
                      <button className="reply-action-btn" onClick={() => {

                      if (!currentUsername || currentUsername === 'anonymousUser') {
                          alert('⚠️ 请先登录！未登录用户不能回复。');
                                  return;
                      }
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

                      {/* 🟢 禁言/解除禁言按鈕（只要不是自己的回覆就能看到） */}
                      {currentUsername && currentUsername !== 'anonymousUser' && reply.customer.username !== currentUsername && (
                        blockedUsers.includes(reply.customer.username) ? (
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

                      {/* 拉黑按鈕（僅管理員可見） */}
                      {currentUsername && isAdmin && (
                        <button
                          className="reply-action-btn btn-blacklist"
                          onClick={() => showNotification(`🚫 拉黑用戶 ${reply.customer.username} 功能開發中`)}
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

      {/* 🟢 新增：如果沒有回覆 (isOpen 為 false)，直接顯示表單 */}
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

      {/*  新增：管理員禁言時長選擇模態框 (使用內聯樣式確保全局居中) */}
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
              {/* 數字輸入框 */}
              <input
                type="number"
                min="1"
                value={blockValue}
                onChange={(e) => setBlockValue(e.target.value)}
                placeholder="請輸入數字"
                style={{ flex: 1, padding: '0.5rem', border: '1px solid #ddd', borderRadius: '4px' }}
              />
              {/* 單位選擇下拉框 */}
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