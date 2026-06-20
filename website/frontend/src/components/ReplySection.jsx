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

  // 🟢【核心修復】當 showReplyForm 變成 true 時，根據是否有回覆決定是否展開
  useEffect(() => {
    if (showReplyForm) {
      // 只有當已有回覆時，才展開評論區列表
      if (initialReplyCount > 0) {
        setIsOpen(true);
      } else {
        setIsOpen(false); // 🟢 沒有回覆時，不展開列表區域，直接顯示表單
      }
      setReplyParentId(null);
      if (rootUsername) setReplyToUser(rootUsername);
    }
  }, [showReplyForm, rootUsername, initialReplyCount]); // 🟢 加入 initialReplyCount 依賴

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

    </div>
  );
};

export default ReplySection;