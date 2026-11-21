import { create } from 'zustand';

interface PackState {
  isOpen: boolean;
  open: () => void;
  close: () => void;
}

export const usePackStore = create<PackState>((set) => ({
  isOpen: false,
  open: () => set({ isOpen: true }),
  close: () => set({ isOpen: false }),
}));
