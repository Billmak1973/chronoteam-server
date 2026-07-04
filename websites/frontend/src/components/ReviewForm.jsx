import React, { useState } from 'react';

// 核心修復：必須接收 currentUsername 作為 props，否則下方調用會報 ReferenceError
const ReviewForm = ({ productId, orderNo, onSuccess, currentUsername }) => {
    const [rating, setRating] = useState(0);
    const [hoverRating, setHoverRating] = useState(0);
    const [content, setContent] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);

    //  核心：用于控制表单是否显示（评价成功后立即隐藏）
    const [isSubmitted, setIsSubmitted] = useState(false);

    const handleStarClick = (e, starIndex) => {
        const rect = e.currentTarget.getBoundingClientRect();
        const isLeftHalf = (e.clientX - rect.left) < (rect.width / 2);
        setRating(isLeftHalf ? starIndex - 0.5 : starIndex);
    };

    const handleStarHover = (e, starIndex) => {
        const rect = e.currentTarget.getBoundingClientRect();
        const isLeftHalf = (e.clientX - rect.left) < (rect.width / 2);
        setHoverRating(isLeftHalf ? starIndex - 0.5 : starIndex);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (rating === 0) return alert('請先選擇評分');
        if (!content.trim()) return alert('請輸入評價內容');

        setIsSubmitting(true);
        try {
            const res = await fetch('/api/review/submit', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify({ productId, orderNo, rating, content })
            });
            const result = await res.json();

            if (result.success) {
                alert('✅ 評價提交成功！');

                //  核心修复 1：立即隐藏当前表单，防止重复提交
                setIsSubmitted(true);

                //  核心修复 2：通知父组件刷新数据
                if (onSuccess) {
                    onSuccess();
                }
            } else {
               // 攔截「永久拉黑」錯誤，彈出紅色警告框
               if (result.message === "BLACKLISTED") {
                   if (window.showBlacklistedModal) {
                          window.showBlacklistedModal(); // 直接調用，彈出紅色的永久拉黑彈窗
                   }
                     return; // 攔截，不執行後續的 alert
               }
                //  新增：處理管理員全局禁言錯誤
                if (result.message === "GLOBAL_BAN" && result.data && result.data.banned) {
                    // 調用 HTML 中定義的全局彈窗函數
                    if (window.showGlobalBanModal) {
                        window.showGlobalBanModal(
                            currentUsername,  // 當前用戶名
                            result.data.expiresAt  // 禁言過期時間
                        );
                    } else {
                        // 降級處理：如果函數不存在，顯示普通 alert
                        const date = new Date(result.data.expiresAt);
                        alert(`您已被管理員禁言至 ${date.toLocaleString('zh-TW')}，期間無法發表評論`);
                    }
                } else {
                    // 普通錯誤提示
                    alert(result.message || '提交失敗');
                }
            }
        } catch (err) {
            alert('網絡錯誤');
        } finally {
            setIsSubmitting(false);
        }
    };

    //  如果已经提交成功，直接不渲染任何内容
    if (isSubmitted) {
        return null;
    }

    const displayRating = hoverRating || rating;

    return (
        <div className="review-form order-review">
            <h3><i className="fas fa-star" style={{color: 'var(--gold)'}}></i> 對該訂單商品進行評價</h3>
            <form onSubmit={handleSubmit}>
                <div className="rating-section">
                    <label className="rating-label">評分：<span className="rating-display">{displayRating.toFixed(1)}</span></label>
                    <div className="rating-stars" onMouseLeave={() => setHoverRating(0)}>
                        {[1, 2, 3, 4, 5].map(i => (
                            <i
                                key={i}
                                className={
                                    displayRating >= i ? "fas fa-star rating-star" :
                                    displayRating === i - 0.5 ? "fas fa-star-half-alt rating-star" :
                                    "far fa-star rating-star"
                                }
                                onClick={(e) => handleStarClick(e, i)}
                                onMouseMove={(e) => handleStarHover(e, i)}
                            ></i>
                        ))}
                    </div>
                    <div className="rating-hint">
                        <i className="fas fa-info-circle"></i> 懸停並點擊星星的左半邊為 0.5 分，右半邊為整星
                    </div>
                </div>

                <div style={{marginBottom: '1rem'}}>
                    <label style={{display: 'block', marginBottom: '0.5rem', fontWeight: 500}}>評價內容：</label>
                    <textarea
                        className="review-textarea"
                        value={content}
                        onChange={(e) => setContent(e.target.value)}
                        placeholder="請分享您對該商品的使用體驗..."
                        maxLength="1000"
                        required
                    ></textarea>
                    <div className="char-count">{content.length}/1000</div>
                </div>

                <button type="submit" className="btn btn-primary" disabled={isSubmitting}>
                    {isSubmitting ? <><i className="fas fa-spinner fa-spin"></i> 提交中...</> : <><i className="fas fa-paper-plane"></i> 提交評價</>}
                </button>
            </form>
        </div>
    );
};

export default ReviewForm;