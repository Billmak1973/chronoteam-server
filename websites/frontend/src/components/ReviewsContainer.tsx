import React, { useState, useEffect, useCallback } from 'react';
import ReviewForm from './ReviewForm';
import ReviewCard from './ReviewCard';

// ==========================================
// 1. 定義型別介面 (Interfaces)
// ==========================================

// 評論數據結構 (根據您的代碼推斷，可根據實際後端返回調整)
export interface Review {
    id: number;
    content: string;
    rating?: number;
    likeCount: number;
    dislikeCount: number;
    pinned: boolean;
    createdAt: string;
    replyCount: number;
    formattedContent?: string | null;
    isFormatted?: boolean;
    customer: {
        username: string;
    };
    //  核心修復：補上這兩個後端有返回的字段
    isLikedByMe: boolean;
    isDislikedByMe: boolean;
}

// 組件 Props 型別
export interface ReviewsContainerProps {
    productId: number;
    currentUsername: string;
    isAdmin: boolean;
    canReview: boolean;
    reviewOrderNo: string;
    initialTotalCount: number;
    initialAvgRating: number;
}

// 擴充 Window 物件，讓 TS 認識全域函數
declare global {
    interface Window {
        handleReviewSubmitSuccess?: () => void;
    }
}

// ==========================================
// 2. 核心組件
// ==========================================
const ReviewsContainer: React.FC<ReviewsContainerProps> = ({
                                                               productId,
                                                               currentUsername,
                                                               isAdmin,
                                                               canReview,
                                                               reviewOrderNo,
                                                               initialTotalCount,
                                                               initialAvgRating,
                                                           }) => {
    const [reviews, setReviews] = useState<Review[]>([]);
    const [page, setPage] = useState<number>(0);
    const [totalPages, setTotalPages] = useState<number>(0);
    const [sort, setSort] = useState<string>('popular');
    const [loading, setLoading] = useState<boolean>(false);
    const [totalCount, setTotalCount] = useState<number>(initialTotalCount);
    const [avgRating, setAvgRating] = useState<number>(initialAvgRating);
    const [localCanReview, setLocalCanReview] = useState<boolean>(canReview);
    const [validMentionedUsers, setValidMentionedUsers] = useState<string[]>([]);

    // 統一管理禁言用戶列表（在所有評論和回覆中共享）
    const [blockedUsers, setBlockedUsers] = useState<string[]>([]);

    // 【新增】獲取 URL 中的 reviewId 參數 (可能是根評論，也可能是樓中樓)
    const [targetReviewId, setTargetReviewId] = useState<string | null>(null);

    useEffect(() => {
        const params = new URLSearchParams(window.location.search);
        const reviewId = params.get('reviewId');
        if (reviewId) {
            setTargetReviewId(reviewId);
        }
    }, []);

    const fetchReviews = useCallback(
        async (targetPage: number = 0, targetSort: string = sort) => {
            setLoading(true);
            try {
                const res = await fetch(
                    `/api/review/product/${productId}/root?page=${targetPage}&size=30&sort=${targetSort}`
                );
                const result = await res.json();
                if (result.success) {
                    setReviews(result.data.reviews);
                    setValidMentionedUsers(result.data.validMentionedUsers || []);
                    setTotalPages(result.data.totalPages);
                    setTotalCount(result.data.totalElements);
                    setPage(targetPage);
                    setSort(targetSort);
                    if (result.data.avgRating !== undefined) {
                        setAvgRating(result.data.avgRating);
                    }
                }
            } catch (err) {
                console.error('獲取評論失敗:', err);
            } finally {
                setLoading(false);
            }
        },
        [productId, sort]
    );

    // 加載當前用戶已禁言的用戶列表
    useEffect(() => {
        if (!currentUsername || currentUsername === 'anonymousUser') return;

        const fetchBlockedUsers = async () => {
            try {
                const res = await fetch('/api/user/blocked-list', { credentials: 'same-origin' });
                if (res.ok) {
                    const list: string[] = await res.json();
                    setBlockedUsers(list);
                }
            } catch (error) {
                console.error('加載禁言列表失敗', error);
            }
        };

        fetchBlockedUsers();
    }, [currentUsername]);

    // 禁言/解除禁言的統一處理函數
    const handleBlockUser = async (
        targetUsername: string,
        shouldBlock: boolean = true
    ): Promise<{ success: boolean; message?: string }> => {
        if (shouldBlock) {
            try {
                const response = await fetch(`/api/user/${targetUsername}/toggle-block`, {
                    method: 'POST',
                    credentials: 'same-origin',
                });
                const result = await response.json();
                if (response.ok && result.success) {
                    setBlockedUsers((prev) => [...prev, targetUsername]);
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
                    credentials: 'same-origin',
                });
                const result = await response.json();
                if (response.ok) {
                    setBlockedUsers((prev) => prev.filter((u) => u !== targetUsername));
                    return { success: true };
                } else {
                    return { success: false, message: result.message };
                }
            } catch (error) {
                return { success: false, message: '網絡錯誤' };
            }
        }
    };

    // 核心修復：依賴 productId，確保 props 傳入後觸發請求
    useEffect(() => {
        if (productId) {
            fetchReviews();
        }
    }, [productId, fetchReviews]);

    useEffect(() => {
        setLocalCanReview(canReview);
    }, [canReview]);

    const handleReplyCountChange = (reviewId: number, delta: number) => {
        setReviews((prev) =>
            prev.map((r) =>
                r.id === reviewId
                    ? { ...r, replyCount: Math.max(0, (r.replyCount || 0) + delta) }
                    : r
            )
        );
    };

    // 【核心修復 1】：處理新增根評論後刷新列表
    const handleNewReviewSubmitted = () => {
        // 1. 手動將 localCanReview 設為 false，徹底卸載 ReviewForm 組件
        setLocalCanReview(false);

        // 2. 調用全局函數更新 Thymeleaf 渲染的 UI 提示區塊
        if (typeof window.handleReviewSubmitSuccess === 'function') {
            window.handleReviewSubmitSuccess();
        }

        // 3. 強制刷新第一頁，更新評論列表和統計數據
        fetchReviews(0, sort);
    };

    // 【核心修復 2】：處理刪除根評論 (開啟表單)
    const handleReviewDeleted = async () => {
        // 1. 先刷新列表，等待 API 返回並更新 DOM
        await fetchReviews(page, sort);
        // 2. 核心：將 localCanReview 設為 true，重新掛載 ReviewForm
        setLocalCanReview(true);
    };

    const handleReviewUpdated = (updatedReview: Review) => {
        setReviews((prev) =>
            prev.map((r) => (r.id === updatedReview.id ? updatedReview : r))
        );
    };

    const renderPagination = () => {
        if (totalPages <= 1) return null;

        let pagesToShow = new Set<number>([0, totalPages - 1]);
        for (let i = page - 2; i <= page + 2; i++) {
            if (i >= 0 && i < totalPages) pagesToShow.add(i);
        }

        let sortedPages = Array.from(pagesToShow).sort((a, b) => a - b);

        return (
            <div className="review-pagination">
                {page > 0 && (
                    <button className="btn btn-outline" onClick={() => fetchReviews(page - 1)}>
                        <i className="fas fa-chevron-left"></i> 上一頁
                    </button>
                )}
                {sortedPages.map((p, idx) => {
                    if (idx > 0 && p - sortedPages[idx - 1] > 1) {
                        return (
                            <span key={`ellipsis-${p}`} className="page-ellipsis">
                ...
              </span>
                        );
                    }
                    return (
                        <button
                            key={p}
                            className={`btn ${p === page ? 'btn-gold' : 'btn-outline'}`}
                            onClick={() => fetchReviews(p)}
                        >
                            {p + 1}
                        </button>
                    );
                })}
                {page < totalPages - 1 && (
                    <button className="btn btn-outline" onClick={() => fetchReviews(page + 1)}>
                        下一頁 <i className="fas fa-chevron-right"></i>
                    </button>
                )}
            </div>
        );
    };

    return (
        <div className="reviews-section">
            <div className="reviews-header">
                <h2>
                    <i className="fas fa-comments"></i> 用戶評論 ({totalCount})
                </h2>
                <select
                    value={sort}
                    onChange={(e: React.ChangeEvent<HTMLSelectElement>) => fetchReviews(0, e.target.value)}
                    className="review-sort-select"
                >
                    <option value="popular">最多點贊</option>
                    <option value="newest">最新發布</option>
                    <option value="oldest">最早發布</option>
                </select>
            </div>

            <div className="review-rating-summary">
                <div className="summary-left">
                    <div className="avg-score">{avgRating.toFixed(1)}</div>
                    <div className="avg-stars">
                        {[1, 2, 3, 4, 5].map((i) => {
                            const fullStars = Math.floor(avgRating);
                            const hasHalfStar = avgRating - fullStars >= 0.5;
                            let starClass = 'far fa-star';
                            if (i <= fullStars) {
                                starClass = 'fas fa-star';
                            } else if (i === fullStars + 1 && hasHalfStar) {
                                starClass = 'fas fa-star-half-alt';
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

            {/* 評價表單：依賴 localCanReview 來決定是否掛載 */}
            {localCanReview && (
                <ReviewForm
                    productId={productId}
                    orderNo={reviewOrderNo}
                    onSuccess={handleNewReviewSubmitted}
                    currentUsername={currentUsername}
                    isAdmin={isAdmin}
                />
            )}

            {loading ? (
                <div className="loading-state">
                    <i className="fas fa-spinner fa-spin"></i> 載入中...
                </div>
            ) : (
                <div className="reviews-list">
                    {reviews.map((review) => (
                        <ReviewCard
                            key={review.id}
                            review={review}
                            currentUsername={currentUsername}
                            isAdmin={isAdmin}
                            productId={productId}
                            onReplyCountChange={handleReplyCountChange}
                            onReviewDeleted={handleReviewDeleted}
                            onReviewUpdated={handleReviewUpdated}
                            blockedUsers={blockedUsers}
                            onBlockUser={handleBlockUser}
                            targetReviewId={targetReviewId}
                            validMentionedUsers={validMentionedUsers}
                        />
                    ))}
                </div>
            )}

            {renderPagination()}
        </div>
    );
};

export default ReviewsContainer;