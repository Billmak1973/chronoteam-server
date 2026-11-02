import React, { useState } from 'react'

// 定義 props 類型
interface FavoriteButtonProps {
    productId: number
    initialIsFavorite: boolean
}

// 定義 API 響應類型
interface ToggleFavoriteResponse {
    success: boolean
    message?: string
}

const FavoriteButton: React.FC<FavoriteButtonProps> = ({
                                                           productId,
                                                           initialIsFavorite
                                                       }) => {
    const [isFavorite, setIsFavorite] = useState<boolean>(initialIsFavorite)
    const [loading, setLoading] = useState<boolean>(false)

    const toggleFavorite = async (): Promise<void> => {
        setLoading(true)
        try {
            const response = await fetch(`/api/favorites/toggle/${productId}`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'same-origin'
            })

            const result: ToggleFavoriteResponse = await response.json()

            if (response.ok && result.success) {
                const newState: boolean = !isFavorite
                setIsFavorite(newState)

                if (typeof window.showNotification === 'function') {
                    window.showNotification(
                        newState ? '❤️ 已加入收藏' : '💔 已取消收藏',
                        false
                    )
                }
            } else {
                if (typeof window.showNotification === 'function') {
                    window.showNotification('❌ ' + (result.message || '操作失敗'), true)
                }
            }
        } catch (error) {
            console.error('收藏失敗:', error)
            if (typeof window.showNotification === 'function') {
                window.showNotification('❌ 網絡錯誤', true)
            }
        } finally {
            setLoading(false)
        }
    }

    return (
        <button
            className={`favorite-btn ${isFavorite ? 'active' : ''}`}
            onClick={toggleFavorite}
            disabled={loading}
            style={{
                position: 'absolute',
                top: '1rem',
                right: '1rem',
                width: '45px',
                height: '45px',
                borderRadius: '50%',
                background: 'white',
                border: '2px solid #e9ecef',
                cursor: loading ? 'not-allowed' : 'pointer',
                transition: 'all 0.3s',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                opacity: loading ? 0.7 : 1
            }}
        >
            {loading ? (
                <i className="fas fa-spinner fa-spin" style={{ color: '#999' }}></i>
            ) : (
                <i
                    className={isFavorite ? 'fas fa-heart' : 'far fa-heart'}
                    style={{
                        color: isFavorite ? 'var(--accent)' : '#999',
                        fontSize: '1.2rem'
                    }}
                ></i>
            )}
        </button>
    )
}

export default FavoriteButton