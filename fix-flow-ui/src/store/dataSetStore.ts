import { create } from 'zustand';

interface DataSetState {
  isOpen: boolean;
  selectedId: string | null;
  open: () => void;
  close: () => void;
  select: (id: string | null) => void;
}

export const useDataSetStore = create<DataSetState>((set) => ({
  isOpen: false,
  selectedId: null,
  open: () => set({ isOpen: true }),
  close: () => set({ isOpen: false }),
  select: (id) => set({ selectedId: id }),
}));
