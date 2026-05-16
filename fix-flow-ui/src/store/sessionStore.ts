import { create } from 'zustand';
import { FIXSessionConfig } from '../types';

interface SessionState {
  sessions: FIXSessionConfig[];
  activeSession: FIXSessionConfig | null;
  setSessions: (s: FIXSessionConfig[]) => void;
  setActiveSession: (s: FIXSessionConfig | null) => void;
  updateSession: (id: string, patch: Partial<FIXSessionConfig>) => void;
}

export const useSessionStore = create<SessionState>((set) => ({
  sessions: [],
  activeSession: null,
  setSessions: (sessions) => set({ sessions }),
  setActiveSession: (activeSession) => set({ activeSession }),
  updateSession: (id, patch) =>
    set((s) => ({
      sessions: s.sessions.map((sess) => (sess.id === id ? { ...sess, ...patch } : sess)),
      activeSession:
        s.activeSession?.id === id ? { ...s.activeSession, ...patch } : s.activeSession,
    })),
}));
