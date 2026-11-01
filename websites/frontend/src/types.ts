export interface CartItem {
    cartId: number;
    product: {
        productId: number;
        description: string;
        image: string;
        stock: number;
    };
    price: number;
    quantity: number;
    selected: boolean;
}

export interface NotificationData {
    messageCount: number;
    systemCount: number;
    replyCount: number;
    mentionCount: number;
    likeCount: number;
}

// 宣告全域變量，讓 TS 知道 HTML 中注入的變量
declare global {
    interface Window {
        isLoggedIn: boolean;
        Cart: any;
        Auth: any;
        NotificationSys: any;
        addToCart: (productId: number) => Promise<void>;
        showNotification: (message: string, isError?: boolean) => void;
        updateCartBadge: (count: number) => void;
        formatPrice: (price: number) => string;
    }
}