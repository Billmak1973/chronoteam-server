// 從全局 React 對象中解構 Hooks (CDN 模式下不需要 import)
const { useState, useRef, useEffect } = React;

// ==========================================
// 這裡放入您的 ReviewForm 組件代碼
// ==========================================
const ReviewForm = ({ productId, orderNo, onSuccess, currentUsername, isAdmin }) => {
    const [rating, setRating] = useState(0);
    const [hoverRating, setHoverRating] = useState(0);
    const [content, setContent] = useState('');
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [isSubmitted, setIsSubmitted] = useState(false);
    const editorRef = useRef(null);

    // 【新增】：用於追蹤輸入法(IME)是否正在組合拼音
    const isComposing = useRef(false);

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

    const formatText = (command, value = null) => {
        document.execCommand(command, false, value);
        if (editorRef.current) {
            setContent(editorRef.current.innerHTML);
        }
        editorRef.current?.focus();
    };

    const insertLink = () => {
        const url = prompt('請輸入連結地址：', 'https://');
        if (url) {
            formatText('createLink', url);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (!isAdmin && rating === 0) return alert('請先選擇評分');
        if (!content.trim() && !editorRef.current?.innerText.trim()) {
            return alert('請輸入評價內容');
        }

        const finalContent = editorRef.current ? editorRef.current.innerHTML : content;
        if (!finalContent.trim()) {
            return alert('請輸入評價內容');
        }

        setIsSubmitting(true);
        try {
            const payload = { productId, content: finalContent };
            if (!isAdmin) {
                payload.orderNo = orderNo;
                payload.rating = rating;
            } else {
                payload.orderNo = "ADMIN_COMMENT";
                payload.rating = null;
            }

            const res = await fetch('/api/review/submit', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin',
                body: JSON.stringify(payload)
            });
            const result = await res.json();

            if (result.success) {
                if (window.showNotification) {
                    window.showNotification('✅ 評價提交成功！', false);
                } else {
                    alert('✅ 評價提交成功！');
                }
                setIsSubmitted(true);
                if (onSuccess) {
                    onSuccess();
                }
            } else {
                if (result.message === "BLACKLISTED") {
                    if (window.showBlacklistedModal) window.showBlacklistedModal();
                    return;
                }
                if (result.message === "GLOBAL_BAN" && result.data && result.data.banned) {
                    if (window.showGlobalBanModal) {
                        window.showGlobalBanModal(currentUsername, result.data.expiresAt);
                    } else {
                        const date = new Date(result.data.expiresAt);
                        alert(`您已被管理員禁言至 ${date.toLocaleString('zh-TW')}，期間無法發表評論`);
                    }
                } else {
                    if (window.showNotification) {
                        window.showNotification('❌ ' + (result.message || '提交失敗'), true);
                    } else {
                        alert(result.message || '提交失敗');
                    }
                }
            }
        } catch (err) {
            console.error(err);
            if (window.showNotification) {
                window.showNotification('❌ 網絡錯誤', true);
            } else {
                alert('網絡錯誤');
            }
        } finally {
            setIsSubmitting(false);
        }
    };

    if (isSubmitted) {
        return null;
    }

    const displayRating = hoverRating || rating;

    return (
        <div className="review-form order-review">
            <h3>
                <i className="fas fa-star" style={{color: 'var(--gold)'}}></i>
                {isAdmin ? '管理員官方評價 / 備註' : '對該訂單商品進行評價'}
            </h3>
            <form onSubmit={handleSubmit}>
                {!isAdmin && (
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
                        <div className="rating-hint" style={{fontSize: '0.8rem', color: '#6c757d', marginTop: '0.5rem'}}>
                            <i className="fas fa-info-circle"></i> 懸停並點擊星星的左半邊為 0.5 分，右半邊為整星
                        </div>
                    </div>
                )}

                <div style={{marginBottom: '1rem'}}>
                    <label style={{display: 'block', marginBottom: '0.5rem', fontWeight: 500}}>評價內容：</label>

                    {/* 富文本編輯器工具欄 */}
                    <div className="rich-editor-toolbar" style={{
                        display: 'flex', gap: '4px', padding: '8px', background: '#f8f9fa',
                        border: '1px solid #ddd', borderBottom: 'none', borderRadius: '8px 8px 0 0', flexWrap: 'wrap'
                    }}>
                        <div style={{display: 'flex', gap: '2px', borderRight: '1px solid #ddd', paddingRight: '8px'}}>
                            <button type="button" onClick={() => formatText('bold')} title="粗體" style={{padding: '4px 8px', border: '1px solid transparent', background: 'transparent', cursor: 'pointer', borderRadius: '3px', fontWeight: 'bold'}}>B</button>
                            <button type="button" onClick={() => formatText('italic')} title="斜體" style={{padding: '4px 8px', border: '1px solid transparent', background: 'transparent', cursor: 'pointer', borderRadius: '3px', fontStyle: 'italic'}}>I</button>
                            <button type="button" onClick={() => formatText('underline')} title="下劃線" style={{padding: '4px 8px', border: '1px solid transparent', background: 'transparent', cursor: 'pointer', borderRadius: '3px', textDecoration: 'underline'}}>U</button>
                        </div>
                        <div style={{display: 'flex', gap: '2px', borderRight: '1px solid #ddd', paddingRight: '8px'}}>
                            <button type="button" onClick={() => formatText('foreColor', '#e94560')} title="文字顏色" style={{padding: '4px 8px', border: '1px solid transparent', background: 'transparent', cursor: 'pointer', borderRadius: '3px'}}>
                                <span style={{borderBottom: '3px solid #e94560'}}>A</span>
                            </button>
                            <button type="button" onClick={() => formatText('hiliteColor', '#fff3cd')} title="背景色" style={{padding: '4px 8px', border: '1px solid transparent', background: '#fff3cd', cursor: 'pointer', borderRadius: '3px'}}>A</button>
                        </div>
                        <div style={{display: 'flex', gap: '2px', borderRight: '1px solid #ddd', paddingRight: '8px'}}>
                            <button type="button" onClick={() => formatText('insertUnorderedList')} title="無序列表" style={{padding: '4px 8px', border: '1px solid transparent', background: 'transparent', cursor: 'pointer', borderRadius: '3px'}}>• List</button>
                            <button type="button" onClick={() => formatText('justifyLeft')} title="左對齊" style={{padding: '4px 8px', border: '1px solid transparent', background: 'transparent', cursor: 'pointer', borderRadius: '3px'}}>⊞</button>
                            <button type="button" onClick={() => formatText('justifyCenter')} title="居中" style={{padding: '4px 8px', border: '1px solid transparent', background: 'transparent', cursor: 'pointer', borderRadius: '3px'}}>⊟</button>
                        </div>
                        <div style={{display: 'flex', gap: '2px'}}>
                            <button type="button" onClick={insertLink} title="連結" style={{padding: '4px 8px', border: '1px solid transparent', background: 'transparent', cursor: 'pointer', borderRadius: '3px'}}>🔗</button>
                        </div>
                    </div>

                    {/* 富文本編輯區域 (已修復 IME 輸入法 Bug) */}
                    <div
                        ref={editorRef}
                        contentEditable
                        className="review-textarea"
                        style={{
                            minHeight: '150px', padding: '12px', border: '2px solid #e9ecef', borderTop: 'none',
                            borderRadius: '0 0 8px 8px', fontSize: '0.95rem', outline: 'none', background: 'white'
                        }}
                        onCompositionStart={() => {
                            isComposing.current = true; // 開始輸入法組合
                        }}
                        onCompositionEnd={(e) => {
                            isComposing.current = false; // 結束輸入法組合
                            setContent(e.target.innerHTML); // 選詞結束後再同步內容
                        }}
                        onInput={(e) => {
                            // 【核心修復】：在輸入法拼寫期間，禁止更新 state，防止 React 重渲染打斷輸入法！
                            if (isComposing.current) return;
                            setContent(e.target.innerHTML);
                        }}
                        dangerouslySetInnerHTML={{ __html: content }}
                    ></div>

                    <div className="char-count" style={{textAlign: 'right', color: '#6c757d', fontSize: '0.85rem', marginTop: '0.5rem'}}>
                        {editorRef.current?.innerText.length || 0}/1000
                    </div>
                </div>

                <button type="submit" className="btn btn-primary" disabled={isSubmitting} style={{padding: '0.8rem 2rem', fontSize: '1rem'}}>
                    {isSubmitting ? <><i className="fas fa-spinner fa-spin"></i> 提交中...</> : <><i className="fas fa-paper-plane"></i> 提交評價</>}
                </button>
            </form>
        </div>
    );
};

// ==========================================
// 初始化 React 組件
// ==========================================
document.addEventListener('DOMContentLoaded', () => {
    const root = document.getElementById('review-form-root');
    if (root) {
        const productId = parseInt(root.dataset.productId);
        const currentUsername = root.dataset.currentUsername;
        const isAdmin = root.dataset.isAdmin === 'true';
        const orderNo = root.dataset.orderNo || "";

        const ReviewFormWithProps = () => (
            <ReviewForm
                productId={productId}
                orderNo={orderNo}
                onSuccess={() => {
                    console.log('評價成功');
                    if (window.handleReviewSubmitSuccess) {
                        window.handleReviewSubmitSuccess();
                    }
                }}
                currentUsername={currentUsername}
                isAdmin={isAdmin}
            />
        );

        const reactRoot = ReactDOM.createRoot(root);
        reactRoot.render(<ReviewFormWithProps />);
    }
});