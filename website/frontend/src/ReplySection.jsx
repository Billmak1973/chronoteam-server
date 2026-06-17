import React, { useState, useEffect } from 'react'

const ReplySection = ({ reviewId, initialReplyCount, currentUsername, isAdmin, productId }) => {
    // 🟢 核心優勢 1：狀態內聚。不再需要全局的 replyPageMap，每個組件自己管自己的頁碼
    const [isOpen, setIsOpen] = useState(false)
    const [replies, setReplies] = useState([])
    const [page, setPage] = useState(0)
    const [totalPages, setTotalPages] = useState(0)
    const [loading, setLoading] = useState(false)
    const [sort, setSort] = useState('popular')
    
    const [showReplyForm, setShowReplyForm] = useState(false)
    const [replyText, setReplyText] = useState('')
    const [replyToUser, setReplyToUser] = useState('')

    // 加載樓中樓回覆
    const fetchReplies = async (targetPage = 0, targetSort = sort) => {
        if (!isOpen && targetPage === 0) setIsOpen(true)
        setLoading(true)
        try {
            const res = await fetch(`/api/review/${reviewId}/replies?page=${targetPage}&size=20&sort=${targetSort}`)
            const result = await res.json()
            if (result.success) {
                setReplies(result.data.replies)
                setTotalPages(result.data.totalPages)
                setPage(targetPage)
                setSort(targetSort)
            }
        } catch (err) { console.error(err) } 
        finally { setLoading(false) }
    }

    // 🟢 核心優勢 2：樂觀更新與狀態驅動。告別 DOM 操作和 innerHTML
    const handleLike = async (replyId, isDislike = false) => {
        const api = isDislike ? `/api/review/${replyId}/dislike` : `/api/review/${replyId}/like`
        const res = await fetch(api, { method: 'POST' })
        const result = await res.json()
        if (result.success) {
            // 直接更新 State，React 自動重渲染 UI，極致流暢
            setReplies(prev => prev.map(r => 
                r.id === replyId ? { ...r, likeCount: result.likeCount, dislikeCount: result.dislikeCount } : r
            ))
        }
    }

    const handleSubmitReply = async () => {
        if (!replyText.trim()) return alert('請輸入內容')
        const res = await fetch('/api/review/submit', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                productId: productId,
                content: replyText,
                parentId: reviewId,
                replyToUser: replyToUser || null
            })
        })
        if ((await res.json()).success) {
            setReplyText('')
            setReplyToUser('')
            setShowReplyForm(false)
            fetchReplies(page, sort) // 刷新列表
        }
    }

    const handleDelete = async (replyId) => {
        if (!confirm('確定刪除這條回覆嗎？')) return
        const res = await fetch(`/api/review/${replyId}`, { method: 'DELETE' })
        if ((await res.json()).success) {
            setReplies(prev => prev.filter(r => r.id !== replyId))
        }
    }

    // 格式化日期
    const formatDate = (dateStr) => {
        if(!dateStr) return '';
        const d = new Date(dateStr);
        return `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')} ${String(d.getHours()).padStart(2,'0')}:${String(d.getMinutes()).padStart(2,'0')}`;
    }

    return (
        <div className="react-reply-wrapper">
            {/* 展開/收起按鈕 */}
            {initialReplyCount > 0 && !isOpen && (
                <div className="reply-loading" onClick={() => fetchReplies(0, sort)}>
                    <i className="fas fa-clock"></i> 共 {initialReplyCount} 條回復，點擊加載
                </div>
            )}

            {isOpen && (
                <>
                    {/* 排序與收起 */}
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
                        <select onChange={(e) => fetchReplies(0, e.target.value)} value={sort} className="review-sort-select" style={{ minWidth: '100px', padding: '0.3rem' }}>
                            <option value="popular">最多點贊</option>
                            <option value="newest">最新發佈</option>
                            <option value="oldest">最早發佈</option>
                        </select>
                        <button className="btn-collapse" onClick={() => setIsOpen(false)}>
                            <i className="fas fa-chevron-up"></i> 收起
                        </button>
                    </div>

                    {/* 🟢 核心優勢 3：告別 innerHTML 拼接，JSX 自帶 XSS 防護 */}
                    {loading ? (
                        <div style={{ textAlign: 'center', padding: '1rem', color: 'var(--gray)' }}><i className="fas fa-spinner fa-spin"></i> 加載中...</div>
                    ) : (
                        <div className="reply-list-container" style={{ display: 'block', borderLeft: 'none', paddingLeft: 0 }}>
                            {replies.map(reply => (
                                <div key={reply.id} className="reply-item">
                                    <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '0.3rem' }}>
                                        <span style={{ fontWeight: 600, color: 'var(--primary)' }}>
                                            {reply.customer.username}
                                            {reply.replyToUser && <span style={{ color: 'var(--gray)', fontWeight: 'normal' }}> 回復 @{reply.replyToUser}</span>}
                                            {reply.customer.username === currentUsername && <span style={{ background: 'var(--gold)', color: 'var(--primary)', padding: '0.1rem 0.3rem', borderRadius: '3px', fontSize: '0.7rem', marginLeft: '0.3rem' }}>我</span>}
                                        </span>
                                        <span style={{ color: 'var(--gray)', fontSize: '0.8rem' }}>{formatDate(reply.createdAt)}</span>
                                    </div>
                                    
                                    {/* React 自動轉義文本，防止 XSS 攻擊 */}
                                    <div style={{ color: '#555', lineHeight: 1.5, marginBottom: '0.5rem', whiteSpace: 'pre-wrap' }}>{reply.content}</div>
                                    
                                    <div style={{ display: 'flex', gap: '0.8rem', alignItems: 'center' }}>
                                        <button className="btn-sm" style={{ background: '#e9ecef', border: '1px solid #dee2e6', borderRadius: '4px' }} onClick={() => handleLike(reply.id)}>
                                            <i className="far fa-thumbs-up"></i> {reply.likeCount || 0}
                                        </button>
                                        <button className="btn-sm" style={{ background: '#e9ecef', border: '1px solid #dee2e6', borderRadius: '4px' }} onClick={() => handleLike(reply.id, true)}>
                                            <i className="far fa-thumbs-down"></i> {reply.dislikeCount || 0}
                                        </button>
                                        <button className="btn-sm" onClick={() => { setShowReplyForm(true); setReplyToUser(reply.customer.username); }}>
                                            <i className="fas fa-reply"></i> 回復
                                        </button>
                                        {(reply.customer.username === currentUsername || isAdmin) && (
                                            <button className="btn-sm" style={{ color: 'var(--accent)' }} onClick={() => handleDelete(reply.id)}>
                                                <i className="fas fa-trash-alt"></i> 刪除
                                            </button>
                                        )}
                                    </div>
                                </div>
                            ))}

                            {/* 分頁 */}
                            {totalPages > 1 && (
                                <div style={{ display: 'flex', justifyContent: 'center', gap: '6px', marginTop: '15px' }}>
                                    {page > 0 && <button className="btn btn-outline" style={{ padding: '0.3rem 0.8rem', fontSize: '0.85rem' }} onClick={() => fetchReplies(page - 1)}>上一頁</button>}
                                    {Array.from({ length: totalPages }, (_, i) => (
                                        <button key={i} className={i === page ? 'btn btn-gold' : 'btn btn-outline'} style={{ padding: '0.3rem 0.8rem', fontSize: '0.85rem', minWidth: '35px' }} onClick={() => fetchReplies(i)}>{i + 1}</button>
                                    ))}
                                    {page < totalPages - 1 && <button className="btn btn-outline" style={{ padding: '0.3rem 0.8rem', fontSize: '0.85rem' }} onClick={() => fetchReplies(page + 1)}>下一頁</button>}
                                </div>
                            )}
                        </div>
                    )}

                    {/* 回覆表單 */}
                    {showReplyForm && (
                        <div className="reply-form active" style={{ marginTop: '1rem' }}>
                            <label>回復 @{replyToUser}：</label>
                            <textarea value={replyText} onChange={(e) => setReplyText(e.target.value)} placeholder="輸入回復內容..." maxLength="500" style={{ width: '100%', minHeight: '60px', marginBottom: '0.8rem', padding: '0.8rem', borderRadius: '8px', border: '2px solid #e9ecef' }}></textarea>
                            <div style={{ display: 'flex', gap: '0.5rem', justifyContent: 'flex-end' }}>
                                <button className="btn-cancel-reply" onClick={() => setShowReplyForm(false)}>取消</button>
                                <button className="btn-submit-reply" onClick={handleSubmitReply}>提交</button>
                            </div>
                        </div>
                    )}
                    
                    {!showReplyForm && (
                        <button className="btn-sm" style={{ marginTop: '1rem', background: '#6c757d', color: 'white', padding: '0.3rem 0.8rem', borderRadius: '6px' }} onClick={() => setShowReplyForm(true)}>
                            <i className="fas fa-reply"></i> 回復此評論
                        </button>
                    )}
                </>
            )}
        </div>
    )
}

export default ReplySection