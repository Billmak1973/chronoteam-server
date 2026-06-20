import React, { useState, useEffect, useCallback } from 'react';
import ReviewForm from './ReviewForm';
import ReviewCard from './ReviewCard';

const ReviewsContainer = ({ productId, currentUsername, isAdmin, canReview, reviewOrderNo, initialTotalCount, initialAvgRating }) => {
    const [reviews, setReviews] = useState([]);
    const [page, setPage] = useState(0);
    const [totalPages, setTotalPages] = useState(0);
    const [sort, setSort] = useState('popular');
    const [loading, setLoading] = useState(false);

    // 新增狀態：用於存儲動態更新的統計數據
    const [totalCount, setTotalCount] = useState(initialTotalCount);
    const [avgRating, setAvgRating] = useState(initialAvgRating);

    // 獲取根評論
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

                //  核心修復：從 API 響應中更新平均分
                if (result.data.avgRating !== undefined) {
                    setAvgRating(result.data.avgRating);
                }
            }
        } catch (err) { console.error(err); }
        finally { setLoading(false); }
    }, [productId, sort]);

    //  核心修復：依賴 productId，確保 props 傳入後觸發請求 （這裏不能變，否則評論區消失）
   // 監聽變化：[productId] 告訴 React：「請幫我盯緊 productId 這個變數，只要它的值發生改變，就重新執行裡面的代碼」。
   // 精準攔截：當父組件終於把正確的 productId（例如 123）傳入時，productId 從 undefined 變成了 123，觸發了 useEffect 重新執行。
    //安全護欄：if (productId) 確保了不會用空值去發送無效的 API 請求。
   // 結果：資料請求完美對上了正確的 productId，後端成功返回數據，評論區順利渲染！
    useEffect(() => {
        if (productId) {
            fetchReviews();
        }
    }, [productId]);

    //  核心：處理樓中樓數量變化 (由子組件觸發)
    const handleReplyCountChange = (reviewId, delta) => {
        setReviews(prev => prev.map(r =>
            r.id === reviewId ? { ...r, replyCount: Math.max(0, (r.replyCount || 0) + delta) } : r
        ));
    };

    // 處理新增根評論後刷新列表
    const handleNewReviewSubmitted = () => {
        fetchReviews(0, sort);
    };

    // 處理刪除/修改根評論
    const handleReviewDeleted = () => fetchReviews(page, sort);
    const handleReviewUpdated = (updatedReview) => {
        setReviews(prev => prev.map(r => r.id === updatedReview.id ? updatedReview : r));
    };

    // 智能分頁邏輯
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

{/* 評分摘要 */}
<div className="review-rating-summary">
    <div className="summary-left">
        <div className="avg-score">{avgRating.toFixed(1)}</div>
        <div className="avg-stars">
            {[1, 2, 3, 4, 5].map(i => {
                // 🟢 核心修復：精確計算每顆星的狀態
                const fullStars = Math.floor(avgRating);      // 完整星數量 (4.3 -> 4)
                const hasHalfStar = (avgRating - fullStars) >= 0.5; // 是否有半星

                let starClass = "far fa-star"; // 默認空心
                if (i <= fullStars) {
                    starClass = "fas fa-star";       // 完整星
                } else if (i === fullStars + 1 && hasHalfStar) {
                    starClass = "fas fa-star-half-alt"; // 半星
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

            {/* 評價表單 */}
            {canReview && (
                <ReviewForm
                    productId={productId}
                    orderNo={reviewOrderNo}
                    onSuccess={handleNewReviewSubmitted}
                />
            )}

            {/* 評論列表 */}
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
                            onReplyCountChange={handleReplyCountChange}
                            onReviewDeleted={handleReviewDeleted}
                            onReviewUpdated={handleReviewUpdated}
                        />
                    ))}
                </div>
            )}

            {renderPagination()}
        </div>
    );
};

export default ReviewsContainer;