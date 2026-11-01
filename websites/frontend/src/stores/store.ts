import { create } from 'zustand';
import type { CartItem } from '../types';

// === 購物車狀態 ===
interface CartState {
    isOpen: boolean;
    pendingDeleteId: number | null;
    selectedItems: Set<number>;
    setIsOpen: (isOpen: boolean) => void;
    setPendingDeleteId: (id: number | null) => void;
    toggleSelection: (cartId: number, isChecked: boolean) => void;
}

export const useCartStore = create<CartState>((set, get) => ({
    isOpen: false,
    pendingDeleteId: null,
    selectedItems: new Set<number>(),
    setIsOpen: (isOpen) => set({ isOpen }),
    setPendingDeleteId: (id) => set({ pendingDeleteId: id }),
    toggleSelection: (cartId, isChecked) => {
        const selectedItems = new Set(get().selectedItems);
        if (isChecked) selectedItems.add(cartId);
        else selectedItems.delete(cartId);
        set({ selectedItems });
    },
}));

// === 認證狀態 ===
interface AuthState {
    isLoginOpen: boolean;
    isRegisterOpen: boolean;
    isLogoutOpen: boolean;
    setLoginOpen: (open: boolean) => void;
    setRegisterOpen: (open: boolean) => void;
    setLogoutOpen: (open: boolean) => void;
}

export const useAuthStore = create<AuthState>((set) => ({
    isLoginOpen: false,
    isRegisterOpen: false,
    isLogoutOpen: false,
    setLoginOpen: (open) => set({ isLoginOpen: open }),
    setRegisterOpen: (open) => set({ isRegisterOpen: open }),
    setLogoutOpen: (open) => set({ isLogoutOpen: open }),
}));