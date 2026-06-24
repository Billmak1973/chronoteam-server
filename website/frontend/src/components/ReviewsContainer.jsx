import React, { useState, useEffect, useCallback } from 'react';
import ReviewForm from './ReviewForm';
import ReviewCard from './ReviewCard';

const ReviewsContainer = ({ productId, currentUsername, isAdmin, canReview, reviewOrderNo, initialTotalCount, initialAvgRating }) => {
    const [reviews, setReviews] = useState([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [sort, setSort] = useState('popular');
    const [loading, setLoading] = useState(false);
    const [totalCount, setTotalCount] = useState(initialTotalCount);
    const [avgRating, setAvgRating] = useState(initialAvgRating);
    const [localCanReview, setLocalCanReview] = useState(canReview);

    // 統一管理禁言用戶列表（在所有評論和回覆中共享）
    const [blockedUsers, setBlockedUsers] = useState([]);

    const fetchReviews = useCallback(async (targetPage = 0, targetSort = sort) => {
        setLoading(true);
        try {
            const res = await fetch(`/api/review/product/${productId}/root?page=${targetPage}&size=30&sort=${targetSort}`);
            const result = await res.json();
            if (result.success) {
                setReviews(result.data.reviews);
                setTotalPages(result.data.totalPages);
                setTotalCount(result.data.totalElements);
                setPage(targetPage);
                setSort(targetSort);
                if (result.data.avgRating !== undefined) {
                    setAvgRating(result.data.avgRating);
                }
            }
        } catch (err) { console.error(err); }
        finally { setLoading(false); }
    }, [productId, sort]);

    // 加載當前用戶已禁言的用戶列表
    useEffect(() => {
        if (!currentUsername || currentUsername === 'anonymousUser') return;
        const fetchBlockedUsers = async () => {
            try {
                const res = await fetch('/api/user/blocked-list', { credentials: 'same-origin' });
                if (res.ok) {
                    const list = await res.json();
                    setBlockedUsers(list);
                }
            } catch (error) {
                console.error('加載禁言列表失敗', error);
            }
        };
        fetchBlockedUsers();
    }, [currentUsername]);

    // 禁言/解除禁言的統一處理函數
    const handleBlockUser = async (targetUsername, shouldBlock = true) => {
        if (shouldBlock) {
            try {
                const response = await fetch(`/api/user/${targetUsername}/toggle-block`, {
                    method: 'POST',
                    credentials: 'same-origin'
                });
                const result = await response.json();
                if (response.ok && result.success) {
                    setBlockedUsers(prev => [...prev, targetUsername]);
                    return { success: true };
                } else {
                    return { success: false, message: result.message };
                }
            } catch (error) {
                return { success: false, message: '網絡錯誤' };
            }
        } else {
            try {
                const response = await fetch(`/api/user/${targetUsername}/unblock`, {
                    method: 'DELETE',
                    credentials: 'same-origin'
                });
                const result = await response.json();
                if (response.ok) {
                    setBlockedUsers(prev => prev.filter(u => u !== targetUsername));
                    return { success: true };
                } else {
                    return { success: false, message: result.message };
                }
            } catch (error) {
                return { success: false, message: '網絡錯誤' };
            }
        }
    };

    // 下面的注釋不能刪掉，絕對不能
    //  核心修復：依賴 productId，確保 props 傳入後觸發請求 （這裏不能變，否則評論區消失）
    // 監聽變化：[productId] 告訴 React：「請幫我盯緊 productId 這個變數，只要它的值發生改變，就重新執行裡面的代碼」。
    // 精準攔截：當父組件終於把正確的 productId（例如 123）傳入時，productId 從 undefined 變成了 123，觸發了 useEffect 重新執行。
    //安全護欄：if (productId) 確保了不會用空值去發送無效的 API 請求。
    // 結果：資料請求完美對上了正確的 productId，後端成功返回數據，評論區順利渲染
    useEffect(() => {
        if (productId) {
            fetchReviews();
        }
    }, [productId]);

    useEffect(() => {
        setLocalCanReview(canReview);
    }, [canReview]);

    const handleReplyCountChange = (reviewId, delta) => {
        setReviews(prev => prev.map(r =>
            r.id === reviewId ? { ...r, replyCount: Math.max(0, (r.replyCount || 0) + delta) } : r
        ));
    };

    //  【核心修復 1】：處理新增根評論後刷新列表
    const handleNewReviewSubmitted = () => {
        // 1. 手動將 localCanReview 設為 false，徹底卸載 ReviewForm 組件
        setLocalCanReview(false);
        // 2. 強制刷新第一頁，更新評論列表和統計數據
        fetchReviews(0, sort);
    };

    // 【核心修復 2】：處理刪除根評論 (開啟表單)
    const handleReviewDeleted = async () => {
        // 1. 先刷新列表，等待 API 返回並更新 DOM
        await fetchReviews(page, sort);
        // 2. 核心：將 localCanReview 設為 true，重新掛載 ReviewForm
        // 因為提交後 localCanReview 已經是 false，這裡設為 true 會觸發 React 重新渲染
        // 組件重新掛載後，內部的 isSubmitted 狀態會自動重置為 false，表單完美顯示！
        setLocalCanReview(true);
    };

    const handleReviewUpdated = (updatedReview) => {
        setReviews(prev => prev.map(r => r.id === updatedReview.id ? updatedReview : r));
    };

    const renderPagination = () => {
        if (totalPages <= 1) return null;
        let pagesToShow = new Set([0, totalPages - 1]);
        for (let i = page - 2; i <= page + 2; i++) {
            if (i >= 0 && i < totalPages) pagesToShow.add(i);
        }
        let sortedPages = Array.from(pagesToShow).sort((a, b) => a - b);
        return (
            <div className="review-pagination">
                {page > 0 && <button className="btn btn-outline" onClick={() => fetchReviews(page - 1)}><i className="fas fa-chevron-left"></i> 上一頁</button>}
                {sortedPages.map((p, idx) => {
                    if (idx > 0 && p - sortedPages[idx - 1] > 1) return <span key={`ellipsis-${p}`} className="page-ellipsis">...</span>;
                    return (
                        <button key={p} className={`btn ${p === page ? 'btn-gold' : 'btn-outline'}`} onClick={() => fetchReviews(p)}>{p + 1}</button>
                    );
                })}
                {page < totalPages - 1 && <button className="btn btn-outline" onClick={() => fetchReviews(page + 1)}>下一頁 <i className="fas fa-chevron-right"></i></button>}
            </div>
        );
    };

    return (
        <div className="reviews-section">
            <div className="reviews-header">
                <h2><i className="fas fa-comments"></i> 用戶評論 ({totalCount})</h2>
                <select value={sort} onChange={(e) => fetchReviews(0, e.target.value)} className="review-sort-select">
                    <option value="popular">最多點贊</option>
                    <option value="newest">最新發布</option>
                    <option value="oldest">最早發布</option>
                </select>
            </div>
            <div className="review-rating-summary">
                <div className="summary-left">
                    <div className="avg-score">{avgRating.toFixed(1)}</div>
                    <div className="avg-stars">
                        {[1, 2, 3, 4, 5].map(i => {
                            const fullStars = Math.floor(avgRating);
                            const hasHalfStar = (avgRating - fullStars) >= 0.5;
                            let starClass = "far fa-star";
                            if (i <= fullStars) {
                                starClass = "fas fa-star";
                            } else if (i === fullStars + 1 && hasHalfStar) {
                                starClass = "fas fa-star-half-alt";
                            }
                            return <i key={i} className={starClass}></i>;
                        })}
                    </div>
                </div>
                <div className="summary-right">
                    <div className="total-count">{totalCount}</div>
                    <div className="total-text">條評論</div>
                </div>
            </div>
            {/*  評價表單：依賴 localCanReview 來決定是否掛載 */}
            {localCanReview && (
                <ReviewForm
                    productId={productId}
                    orderNo={reviewOrderNo}
                    onSuccess={handleNewReviewSubmitted}
                />
            )}
            {loading ? (
                <div className="loading-state"><i className="fas fa-spinner fa-spin"></i> 載入中...</div>
            ) : (
                <div className="reviews-list">
                    {reviews.map(review => (
                        <ReviewCard
                            key={review.id}
                            review={review}
                            currentUsername={currentUsername}
                            isAdmin={isAdmin}
                            productId={productId}  //樓中樓回復的關鍵，不能刪
                            onReplyCountChange={handleReplyCountChange}
                            onReviewDeleted={handleReviewDeleted}
                            onReviewUpdated={handleReviewUpdated}
                            blockedUsers={blockedUsers}
                            onBlockUser={handleBlockUser}
                        />
                    ))}
                </div>
            )}
            {renderPagination()}
        </div>
    );
};

export default ReviewsContainer;